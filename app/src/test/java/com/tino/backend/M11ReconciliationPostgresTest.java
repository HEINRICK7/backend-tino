package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.payment.application.model.PaymentCommandResult;
import com.tino.backend.payment.application.usecase.CreatePayment;
import com.tino.backend.payment.application.usecase.IngestPaymentWebhook;
import com.tino.backend.payment.application.usecase.ProcessPayment;
import com.tino.backend.payment.domain.model.PaymentAmount;
import com.tino.backend.payment.domain.model.PaymentStatus;
import com.tino.backend.reconciliation.application.exception.ReconciliationConflictException;
import com.tino.backend.reconciliation.application.model.ReconciliationCommandResult;
import com.tino.backend.reconciliation.application.usecase.ReconcilePayments;
import com.tino.backend.reconciliation.domain.model.SettlementEntry;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.math.BigDecimal;
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

@Testcontainers
@SpringBootTest
class M11ReconciliationPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000d01");
    private static final UUID BUSINESS = UUID.fromString("00000000-0000-7000-8000-000000000d0a");
    private static final UUID CUSTOMER = UUID.fromString("00000000-0000-7000-8000-000000000d11");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    @Container static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();
    @Autowired private CreatePayment createPayment;
    @Autowired private ProcessPayment processPayment;
    @Autowired private IngestPaymentWebhook ingestWebhook;
    @Autowired private TenantContextExecutor tenantContext;
    @Autowired private ReconcilePayments reconcile;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://127.0.0.1:65535/realms/test");
    }

    @BeforeEach
    void seed() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = admin(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.purchase_receipt_confirmation_idempotency, public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, public.purchase_receipts, public.receiving_purchase_preview_idempotency, public.receiving_purchase_preview_items, public.receiving_purchase_previews, public.purchase_document_items, public.purchase_documents, public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, "
                    + "public.payment_provider_events, public.payment_outbox, public.payment_idempotency_keys, public.payments, "
                    + "public.credit_audit_records, public.credit_idempotency_keys, public.credit_ledger_entries, public.credit_accounts, "
                    + "public.customer_idempotency_keys, public.customers, public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, public.business_memberships, "
                    + "public.businesses, public.users");
            statement.execute("INSERT INTO public.users (id, external_subject, status, created_at, updated_at) VALUES "
                    + "('%s', 'm11-user', 'ACTIVE', '%s', '%s')".formatted(USER_ID, NOW, NOW));
            statement.execute("INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('%s', 'M11', 'OTHER', 'ACTIVE', '%s', '%s')".formatted(BUSINESS, NOW, NOW));
            statement.execute("INSERT INTO public.business_memberships (id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')".formatted(UUID.randomUUID(), BUSINESS, USER_ID, NOW, NOW));
            statement.execute("INSERT INTO public.customers (id, business_id, name, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'M11 customer', 'ACTIVE', '%s', '%s')".formatted(CUSTOMER, BUSINESS, NOW, NOW));
        }
    }

    @Test
    void matchesExactlyAndRetainsDiscrepanciesWithoutFinancialMutation() throws Exception {
        PaymentCommandResult created = createPayment.execute(USER_ID, new BusinessId(BUSINESS), CUSTOMER,
                new BigDecimal("25.50"), "m11-order", "payment", "a".repeat(64));
        var payment = processPayment.execute(USER_ID, new BusinessId(BUSINESS), created.payment().id()).payment();
        tenantContext.execute(new BusinessId(BUSINESS), () -> ingestWebhook.execute(new BusinessId(BUSINESS),
                payment.id(), "sandbox", "capture-for-reconciliation", payment.providerPaymentId(),
                PaymentStatus.CAPTURED, "a".repeat(64)));
        var entries = List.of(
                settlement("event-match", payment.providerPaymentId(), "25.50", "CAPTURED"),
                settlement("event-amount", payment.providerPaymentId(), "20.00", "CAPTURED"),
                settlement("event-missing", "sandbox-payment-missing", "1.00", "CAPTURED"));

        ReconciliationCommandResult result = reconcile.execute(USER_ID, new BusinessId(BUSINESS), "sandbox",
                "run-1", "b".repeat(64), entries);
        assertThat(result.replayed()).isFalse();
        assertThat(result.run().state()).isEqualTo("COMPLETED");
        assertThat(result.run().matchedCount()).isEqualTo(1);
        assertThat(result.run().discrepancyCount()).isEqualTo(2);
        assertThat(result.run().items()).extracting(item -> item.classification())
                .containsExactlyInAnyOrder("MATCHED", "AMOUNT_MISMATCH", "MISSING_PAYMENT");
        assertThat(reconcile.execute(USER_ID, new BusinessId(BUSINESS), "sandbox", "run-1",
                "b".repeat(64), entries).replayed()).isTrue();
        assertThatThrownBy(() -> reconcile.execute(USER_ID, new BusinessId(BUSINESS), "sandbox", "run-1",
                "c".repeat(64), entries)).isInstanceOf(ReconciliationConflictException.class);
        assertThat(adminCount("payments")).isEqualTo(1);
        assertThat(adminCount("reconciliation_items")).isEqualTo(3);
        assertThatThrownBy(() -> executeAdmin("UPDATE public.reconciliation_items SET classification = 'MATCHED'"))
                .isInstanceOf(SQLException.class).hasMessageContaining("reconciliation_evidence_is_append_only");
    }

    private static SettlementEntry settlement(String event, String providerPayment, String amount, String status) {
        return new SettlementEntry(event, providerPayment, new PaymentAmount(new BigDecimal(amount)), "BRL",
                PaymentStatus.valueOf(status), "f".repeat(64));
    }
    private static long adminCount(String table) throws SQLException {
        try (var connection = admin(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next(); return result.getLong(1);
        }
    }
    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
    private static void executeAdmin(String sql) throws SQLException {
        try (var connection = admin(); var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
