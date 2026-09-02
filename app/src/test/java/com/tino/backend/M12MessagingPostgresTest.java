package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.messaging.application.exception.ConsentRequiredException;
import com.tino.backend.messaging.application.exception.MessagingConflictException;
import com.tino.backend.messaging.application.model.MessageCommandResult;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.messaging.application.usecase.ProcessMessage;
import com.tino.backend.messaging.application.usecase.QueueMessage;
import com.tino.backend.messaging.application.usecase.SetConsent;
import com.tino.backend.messaging.domain.model.MessageChannel;
import com.tino.backend.messaging.domain.model.MessagePurpose;
import com.tino.backend.messaging.domain.model.MessageTemplate;
import com.tino.backend.shared.kernel.BusinessId;
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
class M12MessagingPostgresTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000f01");
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-000000000f0a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-000000000f0b");
    private static final UUID CUSTOMER_A = UUID.fromString("00000000-0000-7000-8000-000000000f11");
    private static final UUID CUSTOMER_B = UUID.fromString("00000000-0000-7000-8000-000000000f12");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Container static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();
    @Autowired private SetConsent setConsent;
    @Autowired private QueueMessage queueMessage;
    @Autowired private ProcessMessage processMessage;

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
        try (var connection = admin(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.business_item_purposes, public.business_operating_modes, public.business_activities, public.purchase_receipt_confirmation_idempotency, public.receiving_events, public.purchase_price_observations, public.purchase_receipt_items, public.purchase_receipts, public.receiving_purchase_preview_idempotency, public.receiving_purchase_preview_items, public.receiving_purchase_previews, public.purchase_document_items, public.purchase_documents, public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, "
                    + "public.message_consent_audit, public.message_consents, public.reconciliation_items, "
                    + "public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, "
                    + "public.credit_idempotency_keys, public.credit_ledger_entries, public.credit_accounts, "
                    + "public.customer_idempotency_keys, public.customers, public.sync_event_rejections, "
                    + "public.sync_outbox, public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
        executeAdmin("INSERT INTO public.users (id, external_subject, status, created_at, updated_at) VALUES "
                + "('%s', 'm12-user', 'ACTIVE', '%s', '%s')".formatted(USER_ID, NOW, NOW));
        seedBusiness(BUSINESS_A, "m12-a");
        seedBusiness(BUSINESS_B, "m12-b");
        seedCustomer(BUSINESS_A, CUSTOMER_A, "M12 A");
        seedCustomer(BUSINESS_B, CUSTOMER_B, "M12 B");
    }

    @Test
    void consentQueueReplayProcessAndEvidenceAreDurable() throws Exception {
        assertThatThrownBy(() -> queue("before-consent", "a".repeat(64)))
                .isInstanceOf(ConsentRequiredException.class);
        var consent = setConsent.execute(USER_ID, new BusinessId(BUSINESS_A), CUSTOMER_A,
                MessageChannel.WHATSAPP, MessagePurpose.TRANSACTIONAL, true, "5511999999999");
        assertThat(consent.granted()).isTrue();
        var first = queue("message-key", "b".repeat(64));
        assertThat(first.replayed()).isFalse();
        assertThat(queue("message-key", "b".repeat(64)).replayed()).isTrue();
        assertThatThrownBy(() -> queue("message-key", "c".repeat(64)))
                .isInstanceOf(MessagingConflictException.class);
        assertThat(adminCount("message_outbox")).isEqualTo(1);
        var processed = processMessage.execute(USER_ID, new BusinessId(BUSINESS_A), first.message().id());
        assertThat(processed.message().status()).isEqualTo("SENT");
        assertThat(processed.message().providerMessageId()).startsWith("sandbox-message-");
        assertThat(adminState("message_outbox")).isEqualTo("COMPLETED");
        assertThat(adminCount("message_delivery_evidence")).isEqualTo(1);
        assertThat(processMessage.execute(USER_ID, new BusinessId(BUSINESS_A), first.message().id()).replayed())
                .isTrue();
        assertThat(adminRecipientHash()).hasSize(64).doesNotContain("5511999999999");
        assertThatThrownBy(() -> executeAdmin("UPDATE public.message_consent_audit SET granted = false"))
                .isInstanceOf(SQLException.class).hasMessageContaining("message_evidence_is_append_only");
    }

    @Test
    void tenantAuthorizationAndRlsDoNotLeakMessages() throws Exception {
        setConsent.execute(USER_ID, new BusinessId(BUSINESS_A), CUSTOMER_A, MessageChannel.WHATSAPP,
                MessagePurpose.TRANSACTIONAL, true, "sandbox-recipient-a");
        assertThatThrownBy(() -> queueMessage.execute(USER_ID, new BusinessId(BUSINESS_B), CUSTOMER_B,
                MessageChannel.WHATSAPP, MessagePurpose.OPERATIONAL, MessageTemplate.RECONCILIATION_ALERT,
                "foreign", "d".repeat(64))).isInstanceOf(RuntimeException.class);
        queue("rls-key", "e".repeat(64));
        try (var connection = app()) {
            connection.setAutoCommit(false);
            setTenant(connection, BUSINESS_A);
            assertThat(countVisible(connection, "messages")).isEqualTo(1);
            setTenant(connection, BUSINESS_B);
            assertThat(countVisible(connection, "messages")).isZero();
            connection.commit();
        }
    }

    private MessageCommandResult queue(String key, String fingerprint) {
        return queueMessage.execute(USER_ID, new BusinessId(BUSINESS_A), CUSTOMER_A, MessageChannel.WHATSAPP,
                MessagePurpose.TRANSACTIONAL, MessageTemplate.PAYMENT_UPDATE, key, fingerprint);
    }
    private static void seedBusiness(UUID id, String subject) throws SQLException {
        try (var connection = admin()) {
            execute(connection, "INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', 'OTHER', 'ACTIVE', '%s', '%s')".formatted(id, subject, NOW, NOW));
            execute(connection, "INSERT INTO public.business_memberships (id, business_id, user_id, role, status, created_at, updated_at) VALUES "
                    + "('%s', '%s', '%s', 'OWNER', 'ACTIVE', '%s', '%s')".formatted(UUID.randomUUID(), id, USER_ID, NOW, NOW));
        }
    }
    private static void seedCustomer(UUID business, UUID id, String name) throws SQLException {
        executeAdmin("INSERT INTO public.customers (id, business_id, name, status, created_at, updated_at) VALUES "
                + "('%s', '%s', '%s', 'ACTIVE', '%s', '%s')".formatted(id, business, name, NOW, NOW));
    }
    private static long adminCount(String table) throws SQLException {
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM public." + table)) {
            r.next(); return r.getLong(1);
        }
    }
    private static String adminState(String table) throws SQLException {
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT state FROM public." + table + " LIMIT 1")) {
            r.next(); return r.getString(1);
        }
    }
    private static String adminRecipientHash() throws SQLException {
        try (var c = admin(); var s = c.createStatement(); var r = s.executeQuery("SELECT recipient_ref_hash FROM public.message_consents")) {
            r.next(); return r.getString(1);
        }
    }
    private static long countVisible(Connection c, String table) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM public." + table)) {
            r.next(); return r.getLong(1);
        }
    }
    private static void setTenant(Connection c, UUID business) throws SQLException {
        try (var s = c.prepareStatement("SELECT set_config('app.business_id', ?, true)")) {
            s.setString(1, business.toString()); s.execute();
        }
    }
    private static void executeAdmin(String sql) throws SQLException { try (var c = admin(); var s = c.createStatement()) { s.execute(sql); } }
    private static void execute(Connection c, String sql) throws SQLException { try (var s = c.createStatement()) { s.execute(sql); } }
    private static Connection admin() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }
    private static Connection app() throws SQLException { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword()); }
}
