package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.model.SyncChangePage;
import com.tino.backend.sync.application.usecase.PullSyncChanges;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL proof for M7 sequence pagination and tenant isolation. */
@Testcontainers
@SpringBootTest
class M7SyncPullPostgresTest {
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-7000-8000-000000000401");
    private static final UUID BUSINESS_A = UUID.fromString(
            "00000000-0000-7000-8000-00000000040a");
    private static final UUID BUSINESS_B = UUID.fromString(
            "00000000-0000-7000-8000-00000000040b");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired
    private PullSyncChanges pullSyncChanges;

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
        migrate();
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
        seedBusiness(BUSINESS_A, "m7-a", "M7 A");
    }

    @Test
    void cursorUsesServerSequenceAndPagesWithoutTimestampTies() throws Exception {
        seedChange(BUSINESS_A, UUID.fromString("00000000-0000-7000-8000-00000000041a"));
        seedChange(BUSINESS_A, UUID.fromString("00000000-0000-7000-8000-00000000041b"));
        seedChange(BUSINESS_A, UUID.fromString("00000000-0000-7000-8000-00000000041c"));

        var first = pull(BUSINESS_A, 0, 2);
        var second = pull(BUSINESS_A, first.nextCursor(), 2);

        assertThat(first.changes()).extracting("eventId").containsExactly(
                UUID.fromString("00000000-0000-7000-8000-00000000041a"),
                UUID.fromString("00000000-0000-7000-8000-00000000041b"));
        assertThat(first.nextCursor()).isPositive();
        assertThat(second.changes()).extracting("eventId").containsExactly(
                UUID.fromString("00000000-0000-7000-8000-00000000041c"));
        assertThat(second.nextCursor()).isGreaterThan(first.nextCursor());
    }

    @Test
    void everyPageIsTenantScopedAndEmptyPageKeepsCursor() throws Exception {
        seedBusiness(BUSINESS_B, "m7-b", "M7 B");
        var eventA = UUID.fromString("00000000-0000-7000-8000-00000000042a");
        var eventB = UUID.fromString("00000000-0000-7000-8000-00000000042b");
        seedChange(BUSINESS_A, eventA);
        seedChange(BUSINESS_B, eventB);

        var pageA = pull(BUSINESS_A, 0, 100);
        var pageB = pull(BUSINESS_B, 0, 100);
        var empty = pull(BUSINESS_A, Long.MAX_VALUE, 100);

        assertThat(pageA.changes()).extracting("eventId").containsExactly(eventA);
        assertThat(pageB.changes()).extracting("eventId").containsExactly(eventB);
        assertThat(empty).isEqualTo(new SyncChangePage(List.of(), Long.MAX_VALUE));
    }

    private SyncChangePage pull(UUID businessId, long cursor, int limit) {
        return pullSyncChanges.execute(USER_ID, businessId, cursor, limit);
    }

    private static void seedBusiness(UUID businessId, String subject, String tradeName)
            throws SQLException {
        try (var connection = adminConnection()) {
            execute(connection, "INSERT INTO public.users "
                    + "(id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'ACTIVE', '%s', '%s')"
                    .formatted(USER_ID, subject + "-user", OCCURRED_AT, OCCURRED_AT)
                    + " ON CONFLICT (id) DO NOTHING");
            execute(connection, "INSERT INTO public.businesses "
                    + "(id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'OTHER', 'ACTIVE', '%s', '%s')"
                    .formatted(businessId, tradeName, OCCURRED_AT, OCCURRED_AT));
            execute(connection, "INSERT INTO public.business_memberships "
                    + "(id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')"
                    .formatted(UUID.randomUUID(), businessId, USER_ID, OCCURRED_AT, OCCURRED_AT));
        }
    }

    private static void seedChange(UUID businessId, UUID eventId) throws SQLException {
        try (var connection = adminConnection()) {
            execute(connection, String.format("INSERT INTO public.sync_event_claims "
                    + "(business_id, event_id, store_id, device_id, aggregate_id, event_type, "
                    + "schema_version, occurred_at, payload, created_at) VALUES "
                    + "('%s', '%s', 'store', 'device', 'aggregate', 'known', 1, '%s', "
                    + "'{\"value\":1}', '%s')",
                    businessId, eventId, OCCURRED_AT, OCCURRED_AT));
            execute(connection, String.format("INSERT INTO public.sync_changes "
                    + "(business_id, event_id, store_id, device_id, aggregate_id, event_type, "
                    + "schema_version, occurred_at, payload, created_at) VALUES "
                    + "('%s', '%s', 'store', 'device', 'aggregate', 'known', 1, '%s', "
                    + "'{\"value\":1}', '%s')",
                    businessId, eventId, OCCURRED_AT, OCCURRED_AT));
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void migrate() {
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load().migrate();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
