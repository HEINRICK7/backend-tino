package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.payment.application.exception.PaymentConflictException;
import com.tino.backend.payment.application.exception.PaymentTransitionException;
import com.tino.backend.payment.application.model.PaymentCommandResult;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.application.usecase.CreatePayment;
import com.tino.backend.payment.application.usecase.IngestPaymentWebhook;
import com.tino.backend.payment.application.usecase.ProcessPayment;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class M10PaymentPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b01");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000b02");
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-000000000b0a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-000000000b0b");
    private static final UUID CUSTOMER_A = UUID.fromString("00000000-0000-7000-8000-000000000b11");
    private static final UUID CUSTOMER_B = UUID.fromString("00000000-0000-7000-8000-000000000b12");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private CreatePayment createPayment;
    @Autowired private ProcessPayment processPayment;
    @Autowired private IngestPaymentWebhook ingestWebhook;
    @Autowired private TenantContextExecutor tenantContext;
    @Autowired private PaymentRepository payments;

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
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, "
                    + "public.credit_idempotency_keys, public.credit_ledger_entries, public.credit_accounts, "
                    + "public.customer_idempotency_keys, public.customers, public.sync_event_rejections, "
                    + "public.sync_outbox, public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
        seedBusiness(BUSINESS_A, USER_ID, "m10-a");
        seedCustomer(BUSINESS_A, CUSTOMER_A, "M10 customer A");
    }

    @Test
    void createReplayProcessAndProviderEventAreDurable() throws Exception {
        var first = create("payment-key", "a".repeat(64));
        assertThat(first.payment().status()).isEqualTo("CREATED");
        assertThat(first.replayed()).isFalse();
        assertThat(create("payment-key", "a".repeat(64)).replayed()).isTrue();
        assertThatThrownBy(() -> create("payment-key", "b".repeat(64)))
                .isInstanceOf(PaymentConflictException.class);
        assertThat(adminCount("payment_outbox")).isEqualTo(1);

        var processed = processPayment.execute(USER_ID, new BusinessId(BUSINESS_A), first.payment().id());
        assertThat(processed.payment().status()).isEqualTo("AUTHORIZED");
        assertThat(processed.payment().providerPaymentId()).startsWith("sandbox-payment-");
        assertThat(adminCount("payment_provider_events")).isEqualTo(1);
        assertThat(adminState("payment_outbox")).isEqualTo("COMPLETED");
        assertThat(processPayment.execute(USER_ID, new BusinessId(BUSINESS_A), first.payment().id()).replayed())
                .isTrue();
    }

    @Test
    void webhookReplayIsIdempotentAndIllegalTransitionIsRejected() throws Exception {
        var created = create("webhook-key", "c".repeat(64));
        var processed = processPayment.execute(USER_ID, new BusinessId(BUSINESS_A), created.payment().id());
        var providerPaymentId = processed.payment().providerPaymentId();
        var accepted = webhook(created.payment().id(), "event-1", providerPaymentId, "CAPTURED");
        assertThat(accepted.status()).isEqualTo("CAPTURED");
        assertThat(webhook(created.payment().id(), "event-1", providerPaymentId, "CAPTURED").status())
                .isEqualTo("CAPTURED");
        assertThat(adminCount("payment_provider_events")).isEqualTo(2);
        assertThatThrownBy(() -> webhook(created.payment().id(), "event-2", providerPaymentId, "CREATED"))
                .isInstanceOf(PaymentTransitionException.class);
    }

    @Test
    void crossTenantCustomerReferenceAndRlsDoNotLeakPayments() throws Exception {
        seedBusiness(BUSINESS_B, OTHER_USER_ID, "m10-b");
        seedCustomer(BUSINESS_B, CUSTOMER_B, "M10 customer B");
        assertThatThrownBy(() -> createPayment.execute(USER_ID, new BusinessId(BUSINESS_B), CUSTOMER_B,
                new BigDecimal("1.00"), null, "foreign", "d".repeat(64)))
                .isInstanceOf(RuntimeException.class);
        create("rls-key", "e".repeat(64));
        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, BUSINESS_A);
            assertThat(countVisible(connection, "payments")).isEqualTo(1);
            setTenant(connection, BUSINESS_B);
            assertThat(countVisible(connection, "payments")).isZero();
            connection.commit();
        }
    }

    private PaymentCommandResult create(String key, String fingerprint) {
        return createPayment.execute(USER_ID, new BusinessId(BUSINESS_A), CUSTOMER_A,
                new BigDecimal("25.50"), "order-10", key, fingerprint);
    }

    private com.tino.backend.payment.application.model.PaymentView webhook(UUID paymentId, String event,
            String providerPaymentId, String status) {
        return tenantContext.execute(new BusinessId(BUSINESS_A), () -> ingestWebhook.execute(
                new BusinessId(BUSINESS_A), paymentId, "sandbox", event, providerPaymentId,
                PaymentStatus.valueOf(status), "f".repeat(64)));
    }

    private static void seedBusiness(UUID businessId, UUID userId, String subject) throws SQLException {
        try (var connection = adminConnection()) {
            execute(connection, "INSERT INTO public.users (id, external_subject, status, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'ACTIVE', '%s', '%s')".formatted(userId, subject, NOW, NOW));
            execute(connection, "INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'OTHER', 'ACTIVE', '%s', '%s')".formatted(businessId, subject, NOW, NOW));
            execute(connection, "INSERT INTO public.business_memberships "
                    + "(id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')"
                            .formatted(UUID.randomUUID(), businessId, userId, NOW, NOW));
        }
    }

    private static void seedCustomer(UUID businessId, UUID customerId, String name) throws SQLException {
        executeAdmin("INSERT INTO public.customers (id, business_id, name, status, created_at, updated_at) "
                + "VALUES ('%s', '%s', '%s', 'ACTIVE', '%s', '%s')"
                        .formatted(customerId, businessId, name, NOW, NOW));
    }

    private static long adminCount(String table) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next(); return result.getLong(1);
        }
    }

    private static String adminState(String table) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT state FROM public." + table + " LIMIT 1")) {
            result.next(); return result.getString(1);
        }
    }

    private static long countVisible(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT count(*) FROM public." + table)) { result.next(); return result.getLong(1); }
    }

    private static void setTenant(Connection connection, UUID businessId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.business_id', ?, true)")) {
            statement.setString(1, businessId.toString()); statement.execute();
        }
    }

    private static void executeAdmin(String sql) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }
}
