package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.credit.application.exception.CreditConflictException;
import com.tino.backend.credit.application.exception.CreditInsufficientBalanceException;
import com.tino.backend.credit.application.model.CreditOperationResult;
import com.tino.backend.credit.application.usecase.AppendCreditEntry;
import com.tino.backend.credit.application.usecase.CompensateCreditEntry;
import com.tino.backend.credit.application.usecase.GetCreditBalance;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
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

/** Real PostgreSQL proof for M9 append-only ledger, atomic balance, and tenant safety. */
@Testcontainers
@SpringBootTest
class M9CreditLedgerPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000a01");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000a02");
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-000000000a0a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-000000000a0b");
    private static final UUID CUSTOMER_A = UUID.fromString("00000000-0000-7000-8000-000000000a11");
    private static final UUID CUSTOMER_B = UUID.fromString("00000000-0000-7000-8000-000000000a12");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private AppendCreditEntry appendCreditEntry;
    @Autowired private CompensateCreditEntry compensateCreditEntry;
    @Autowired private GetCreditBalance getCreditBalance;

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
            statement.execute("TRUNCATE TABLE public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, "
                    + "public.customers, public.sync_event_rejections, public.sync_outbox, public.sync_changes, "
                    + "public.sync_event_claims, public.device_installations, public.business_memberships, "
                    + "public.businesses, public.users");
        }
        seedBusiness(BUSINESS_A, USER_ID, "m9-a");
        seedCustomer(BUSINESS_A, CUSTOMER_A, "M9 customer A");
    }

    @Test
    void creditDebitAndAuditAreAtomicAndExplainable() throws Exception {
        var credit = append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "100.00", "MANUAL_GRANT", "grant");
        var debit = append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT, "30.00", "MANUAL_USE", "use");

        assertThat(credit.replayed()).isFalse();
        assertThat(debit.account().balance()).isEqualByComparingTo("70.00");
        var balance = getCreditBalance.execute(USER_ID, new BusinessId(BUSINESS_A), CUSTOMER_A);
        assertThat(balance.balance()).isEqualByComparingTo("70.00");
        assertThat(adminCount("credit_ledger_entries")).isEqualTo(2);
        assertThat(adminCount("credit_audit_records")).isEqualTo(2);
    }

    @Test
    void compensationAppendsOppositeEntryAndOriginalCannotBeMutated() throws Exception {
        var original = append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "50.00", "MANUAL_GRANT", "original");
        var compensation = compensate(BUSINESS_A, CUSTOMER_A, original.entry().id(), "compensation");

        assertThat(compensation.entry().direction()).isEqualTo(CreditDirection.DEBIT);
        assertThat(compensation.entry().amount().value()).isEqualByComparingTo("50.00");
        assertThat(compensation.entry().compensatesEntryId()).isEqualTo(original.entry().id());
        assertThat(compensation.account().balance()).isEqualByComparingTo("0.00");
        assertThat(adminCount("credit_ledger_entries")).isEqualTo(2);

        assertThatThrownBy(() -> executeAdmin("UPDATE public.credit_ledger_entries SET reason = 'MUTATED'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("credit_ledger_is_append_only");
        assertThatThrownBy(() -> executeAdmin("DELETE FROM public.credit_ledger_entries"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("credit_ledger_is_append_only");
    }

    @Test
    void idempotencyReplayConflictAndInsufficientDebitLeaveOneHistory() throws Exception {
        append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "10.00", "MANUAL_GRANT", "grant");
        var first = append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT, "10.00", "MANUAL_USE", "same");
        var replay = append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT, "10.00", "MANUAL_USE", "same");

        assertThat(first.entry().id()).isEqualTo(replay.entry().id());
        assertThat(replay.replayed()).isTrue();
        assertThatThrownBy(() -> append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "1.00", "OTHER", "same",
                "c".repeat(64)))
                .isInstanceOf(CreditConflictException.class);
        assertThatThrownBy(() -> append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT, "0.01", "MANUAL_USE", "over"))
                .isInstanceOf(CreditInsufficientBalanceException.class);
        assertThat(adminCount("credit_ledger_entries")).isEqualTo(2);
        assertThat(adminCount("credit_idempotency_keys")).isEqualTo(2);
    }

    @Test
    void concurrentDebitsSerializeOnAccountRow() throws Exception {
        append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "10.00", "MANUAL_GRANT", "seed");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT,
                    "10.00", "MANUAL_USE", "debit-a"));
            var second = executor.submit(() -> append(BUSINESS_A, CUSTOMER_A, CreditDirection.DEBIT,
                    "10.00", "MANUAL_USE", "debit-b"));
            var results = List.of(first, second);
            var successes = results.stream().filter(future -> {
                try { future.get(); return true; } catch (Exception exception) { return false; }
            }).count();
            var failures = results.stream().filter(future -> {
                try { future.get(); return false; } catch (Exception exception) {
                    return rootCause(exception) instanceof CreditInsufficientBalanceException;
                }
            }).count();
            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);
        }
        assertThat(adminCount("credit_ledger_entries")).isEqualTo(2);
    }

    @Test
    void rlsAndAuthorizationKeepCreditTenantBound() throws Exception {
        append(BUSINESS_A, CUSTOMER_A, CreditDirection.CREDIT, "12.34", "MANUAL_GRANT", "tenant-a");
        seedBusiness(BUSINESS_B, OTHER_USER_ID, "m9-b");
        seedCustomer(BUSINESS_B, CUSTOMER_B, "M9 customer B");

        assertThatThrownBy(() -> append(BUSINESS_B, CUSTOMER_B, CreditDirection.CREDIT, "1.00", "OTHER", "foreign"))
                .isInstanceOf(BusinessAuthorizationDeniedException.class);
        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, BUSINESS_A);
            assertThat(countVisible(connection, "credit_ledger_entries")).isEqualTo(1);
            assertThat(countVisible(connection, "credit_accounts")).isEqualTo(1);
            setTenant(connection, BUSINESS_B);
            assertThat(countVisible(connection, "credit_ledger_entries")).isZero();
            assertThat(countVisible(connection, "credit_accounts")).isZero();
            connection.commit();
        }
    }

    @Test
    void compositeForeignKeysRejectCrossTenantCustomerReferences() throws Exception {
        seedBusiness(BUSINESS_B, OTHER_USER_ID, "m9-b");
        seedCustomer(BUSINESS_B, CUSTOMER_B, "M9 customer B");

        assertThatThrownBy(() -> executeAdmin("INSERT INTO public.credit_accounts "
                + "(id, business_id, customer_id, currency, balance, version, status, created_at, updated_at) "
                + "VALUES ('%s', '%s', '%s', 'BRL', 0, 0, 'ACTIVE', '%s', '%s')"
                        .formatted(UUID.randomUUID(), BUSINESS_A, CUSTOMER_B, NOW, NOW)))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("credit_accounts_customer_fk");
    }

    private CreditOperationResult append(UUID businessId, UUID customerId, CreditDirection direction,
            String amount, String reason, String key) {
        return appendCreditEntry.execute(USER_ID, new BusinessId(businessId), customerId, direction,
                new BigDecimal(amount), reason, key, "a".repeat(64));
    }

    private CreditOperationResult append(UUID businessId, UUID customerId, CreditDirection direction,
            String amount, String reason, String key, String fingerprint) {
        return appendCreditEntry.execute(USER_ID, new BusinessId(businessId), customerId, direction,
                new BigDecimal(amount), reason, key, fingerprint);
    }

    private CreditOperationResult compensate(UUID businessId, UUID customerId, UUID entryId, String key) {
        return compensateCreditEntry.execute(USER_ID, new BusinessId(businessId), customerId, entryId,
                key, "b".repeat(64));
    }

    private static void seedBusiness(UUID businessId, UUID userId, String subject) throws SQLException {
        try (var connection = adminConnection()) {
            execute(connection, "INSERT INTO public.users (id, external_subject, status, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'ACTIVE', '%s', '%s')".formatted(userId, subject, NOW, NOW));
            execute(connection, "INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) "
                    + "VALUES ('%s', '%s', 'OTHER', 'ACTIVE', '%s', '%s')"
                            .formatted(businessId, subject, NOW, NOW));
            execute(connection, "INSERT INTO public.business_memberships "
                    + "(id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')"
                            .formatted(UUID.randomUUID(), businessId, userId, NOW, NOW));
        }
    }

    private static void seedCustomer(UUID businessId, UUID customerId, String name) throws SQLException {
        executeAdmin("INSERT INTO public.customers "
                + "(id, business_id, name, status, created_at, updated_at) VALUES "
                + "('%s', '%s', '%s', 'ACTIVE', '%s', '%s')".formatted(customerId, businessId, name, NOW, NOW));
    }

    private static void setTenant(Connection connection, UUID businessId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.business_id', ?, true)")) {
            statement.setString(1, businessId.toString());
            statement.execute();
        }
    }

    private static long countVisible(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static long adminCount(String table) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void executeAdmin(String sql) throws SQLException {
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
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

    private static Throwable rootCause(Throwable exception) {
        var current = exception;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }
}
