package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.shared.infrastructure.tenant.PostgresTenantContextExecutor;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidV7Generator;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL gates for M5's read-only composition and reused tenant boundary. */
@Testcontainers
class M5BootstrapPostgresTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-08-27T12:34:56.123456Z");
    private static final UuidV7Generator IDS = new UuidV7Generator();

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @BeforeEach
    void migrateAndClear() throws Exception {
        migrate().migrate();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.inventory_movements, public.inventory_balances, public.goods_receipt_items, public.goods_receipts, public.goods_receipt_preview_items, public.goods_receipt_previews, public.packaging_conversions, public.supplier_product_mappings, public.product_identifiers, public.products, public.nfe_retrieval_idempotency_keys, public.nfe_items, public.nfe_document_versions, public.nfe_documents, public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void testM5_018_rlsSameTenantReturnsInstallation() throws Exception {
        var fixture = fixture("m5-rls-same");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, fixture.businessId());
            assertThat(countVisibleInstallations(connection)).isEqualTo(1L);
            connection.commit();
        }
    }

    @Test
    void testM5_019_rlsCrossTenantHidesInstallation() throws Exception {
        var fixture = fixture("m5-rls-cross");
        var otherBusiness = new BusinessFixture(IDS.next(), IDS.next(), IDS.next(), IDS.next());
        insertUserAndBusiness(otherBusiness, "m5-rls-other");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, otherBusiness.businessId());
            assertThat(countVisibleInstallations(connection)).isZero();
            connection.commit();
        }
        assertThat(fixture.businessId()).isNotEqualTo(otherBusiness.businessId());
    }

    @Test
    void testM5_020_rlsFailsClosedWithoutTenantContext() throws Exception {
        fixture("m5-rls-fail-closed");

        try (var connection = appConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT count(*) FROM public.device_installations")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isZero();
        }
    }

    @Test
    void testM5_036_noBootstrapOrSessionTableExists() throws Exception {
        var forbiddenTables = List.of(
                "bootstrap", "bootstrap_context", "bootstrap_state", "current_business",
                "selected_business", "sessions", "session");

        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = 'public'")) {
            try (var result = statement.executeQuery()) {
                var tables = new ArrayList<String>();
                while (result.next()) {
                    tables.add(result.getString(1));
                }
                assertThat(tables).doesNotContainAnyElementsOf(forbiddenTables);
            }
        }
    }

    @Test
    void testM5_037_migrationHistoryContainsOnlyExplicitVersions() {
        var info = migrate().info();

        assertThat(info.applied()).extracting(migration -> migration.getVersion().toString())
                .containsExactly("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
        assertThat(info.current().getVersion().toString()).isEqualTo("13");
    }

    @Test
    void testM5_043_readyPersistenceUsesRealPostgresql() throws Exception {
        var fixture = fixture("m5-postgresql");

        try (var connection = appConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT version()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).containsIgnoringCase("postgresql");
        }
        assertThat(currentUser(M2PostgresTestContainer.APP)).isEqualTo(M2PostgresTestContainer.APP);
        assertThat(fixture.installationId()).isNotNull();
    }

    @Test
    void testM5_044_poolTenantContextResetsAfterCommitAndRollback() throws Exception {
        var firstBusiness = IDS.next();
        var secondBusiness = IDS.next();
        try (var dataSource = appDataSource()) {
            var jdbc = new JdbcTemplate(dataSource);
            var transactions = new DataSourceTransactionManager(dataSource);
            var tenants = new PostgresTenantContextExecutor(dataSource, transactions);

            assertThat(tenants.execute(new BusinessId(firstBusiness),
                    () -> jdbc.queryForObject(
                            "SELECT current_setting('app.business_id', true)", String.class)))
                    .isEqualTo(firstBusiness.toString());
            assertThat(currentTenant(jdbc)).isBlank();

            assertThatThrownBy(() -> tenants.execute(new BusinessId(secondBusiness), () -> {
                assertThat(jdbc.queryForObject(
                        "SELECT current_setting('app.business_id', true)", String.class))
                        .isEqualTo(secondBusiness.toString());
                throw new IllegalStateException("rollback probe");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(currentTenant(jdbc)).isBlank();
        }
    }

    private static BusinessFixture fixture(String subject) throws Exception {
        var fixture = new BusinessFixture(IDS.next(), IDS.next(), IDS.next(), IDS.next());
        insertUserAndBusiness(fixture, subject);
        insertInstallation(fixture);
        return fixture;
    }

    private static void insertUserAndBusiness(BusinessFixture fixture, String subject)
            throws SQLException {
        try (var connection = migratorConnection();
                var user = connection.prepareStatement(
                        "INSERT INTO public.users "
                                + "(id, external_subject, status, created_at, updated_at) "
                                + "VALUES (?, ?, 'ACTIVE', ?, ?)")) {
            user.setObject(1, fixture.userId());
            user.setString(2, subject);
            user.setObject(3, FIXED_TIME.atOffset(ZoneOffset.UTC));
            user.setObject(4, FIXED_TIME.atOffset(ZoneOffset.UTC));
            user.executeUpdate();
        }
        try (var connection = migratorConnection();
                var business = connection.prepareStatement(
                        "INSERT INTO public.businesses "
                                + "(id, trade_name, vertical, status, created_at, updated_at) "
                                + "VALUES (?, ?, 'OTHER', 'ACTIVE', ?, ?)")) {
            business.setObject(1, fixture.businessId());
            business.setString(2, subject);
            business.setObject(3, FIXED_TIME.atOffset(ZoneOffset.UTC));
            business.setObject(4, FIXED_TIME.atOffset(ZoneOffset.UTC));
            business.executeUpdate();
        }
        try (var connection = migratorConnection();
                var membership = connection.prepareStatement(
                        "INSERT INTO public.business_memberships "
                                + "(id, business_id, user_id, role, status, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'OWNER', 'ACTIVE', ?, ?)")) {
            membership.setObject(1, fixture.membershipId());
            membership.setObject(2, fixture.businessId());
            membership.setObject(3, fixture.userId());
            membership.setObject(4, FIXED_TIME.atOffset(ZoneOffset.UTC));
            membership.setObject(5, FIXED_TIME.atOffset(ZoneOffset.UTC));
            membership.executeUpdate();
        }
    }

    private static void insertInstallation(BusinessFixture fixture) throws SQLException {
        try (var connection = adminConnection();
                var installation = connection.prepareStatement(
                        "INSERT INTO public.device_installations "
                                + "(id, business_id, installation_external_id, status, "
                                + "registered_by_user_id, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)")) {
            installation.setObject(1, fixture.installationId());
            installation.setObject(2, fixture.businessId());
            installation.setString(3, "installation-" + fixture.installationId());
            installation.setObject(4, fixture.userId());
            installation.setObject(5, FIXED_TIME.atOffset(ZoneOffset.UTC));
            installation.setObject(6, FIXED_TIME.atOffset(ZoneOffset.UTC));
            installation.executeUpdate();
        }
    }

    private static void setTenant(Connection connection, UUID businessId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT set_config('app.business_id', ?, true)")) {
            statement.setString(1, businessId.toString());
            statement.execute();
        }
    }

    private static long countVisibleInstallations(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT count(*) FROM public.device_installations")) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static String currentTenant(JdbcTemplate jdbc) {
        var value = jdbc.queryForObject(
                "SELECT current_setting('app.business_id', true)", String.class);
        return value == null ? "" : value;
    }

    private static Flyway migrate() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String currentUser(String role) throws SQLException {
        try (var connection = role.equals(M2PostgresTestContainer.APP)
                ? appConnection() : migratorConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static HikariDataSource appDataSource() {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(M2PostgresTestContainer.APP);
        dataSource.setPassword(POSTGRES.appPassword());
        dataSource.setMaximumPoolSize(1);
        dataSource.setMinimumIdle(1);
        dataSource.setConnectionTimeout(10_000);
        return dataSource;
    }

    private record BusinessFixture(
            UUID businessId, UUID userId, UUID membershipId, UUID installationId) {}
}
