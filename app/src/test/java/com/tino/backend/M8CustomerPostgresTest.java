package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.customer.application.exception.CustomerConflictException;
import com.tino.backend.customer.application.model.CustomerCreateResult;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.application.usecase.CreateCustomer;
import com.tino.backend.customer.application.usecase.ListCustomers;
import com.tino.backend.shared.kernel.BusinessId;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL proof for M8 tenant ownership, RLS, and idempotency. */
@Testcontainers
@SpringBootTest
class M8CustomerPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000701");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000702");
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-00000000070a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-00000000070b");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private CreateCustomer createCustomer;
    @Autowired private ListCustomers listCustomers;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://127.0.0.1:65535/realms/test");
    }

    @BeforeEach
    void migrateAndSeed() throws Exception {
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        POSTGRES.migratorPassword())
                .locations("classpath:db/migration").load().migrate();
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, public.sync_changes, "
                    + "public.sync_event_claims, public.device_installations, public.business_memberships, "
                    + "public.businesses, public.users");
        }
        seedBusiness(BUSINESS_A, "M8 A");
    }

    @Test
    void createReplayDoesNotDuplicateCustomer() throws Exception {
        var first = create(BUSINESS_A, "request-a", "fingerprint-a");
        var replay = create(BUSINESS_A, "request-a", "fingerprint-a");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.customer().id()).isEqualTo(first.customer().id());
        assertThat(adminCount("customers")).isEqualTo(1);
    }

    @Test
    void idempotencyConflictAndCrossBusinessAccessFailClosed() throws Exception {
        create(BUSINESS_A, "request-a", "fingerprint-a");
        assertThatThrownBy(() -> create(BUSINESS_A, "request-a", "other"))
                .isInstanceOf(CustomerConflictException.class);

        seedBusiness(BUSINESS_B, "M8 B", OTHER_USER_ID, "m8-other-user");
        assertThatThrownBy(() -> create(BUSINESS_B, "request-b", "fingerprint-b"))
                .isInstanceOf(BusinessAuthorizationDeniedException.class);
    }

    @Test
    void rlsExposesOnlyTheSelectedBusiness() throws Exception {
        var created = create(BUSINESS_A, "request-a", "fingerprint-a");
        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, BUSINESS_A);
            assertThat(countVisible(connection)).isEqualTo(1);
            connection.commit();
        }
        assertThat(listCustomers.execute(USER_ID, new BusinessId(BUSINESS_A)))
                .extracting("id").containsExactly(created.customer().id());
    }

    @Test
    void concurrentCreateWithSameKeyProducesOneCustomer() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> create(BUSINESS_A, "concurrent", "same"));
            var second = executor.submit(() -> create(BUSINESS_A, "concurrent", "same"));
            var results = List.of(first.get(), second.get());
            assertThat(results).extracting(CustomerCreateResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(results.get(0).customer().id()).isEqualTo(results.get(1).customer().id());
        }
        assertThat(adminCount("customers")).isEqualTo(1);
    }

    private CustomerCreateResult create(UUID businessId, String key, String fingerprint) {
        return createCustomer.execute(USER_ID, new BusinessId(businessId), "Maria", "Mari", "55119999",
                key, fingerprint);
    }

    private static void seedBusiness(UUID businessId, String tradeName) throws SQLException {
        seedBusiness(businessId, tradeName, USER_ID, "m8-user");
    }

    private static void seedBusiness(UUID businessId, String tradeName, UUID userId, String subject)
            throws SQLException {
        try (var connection = adminConnection()) {
            execute(connection, String.format("INSERT INTO public.users "
                    + "(id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'ACTIVE', '%s', '%s') ON CONFLICT (id) DO NOTHING",
                    userId, subject, NOW, NOW));
            execute(connection, String.format("INSERT INTO public.businesses "
                    + "(id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'OTHER', 'ACTIVE', '%s', '%s')",
                    businessId, tradeName, NOW, NOW));
            execute(connection, String.format("INSERT INTO public.business_memberships "
                    + "(id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')",
                    UUID.randomUUID(), businessId, userId, NOW, NOW));
        }
    }

    private static void setTenant(Connection connection, UUID businessId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.business_id', ?, true)")) {
            statement.setString(1, businessId.toString());
            statement.execute();
        }
    }

    private static long countVisible(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT count(*) FROM public.customers")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static long adminCount(String table) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }
}
