package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.businessunderstanding.adapter.out.persistence.JooqBusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.application.exception.BusinessUnderstandingNotFoundException;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeSource;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BusinessUnderstandingPostgresTest {
    private static final OffsetDateTime NOW = nowMicros();

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    private static OffsetDateTime nowMicros() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.withNano((now.getNano() / 1_000) * 1_000);
    }

    @BeforeEach
    void migrateAndClear() throws Exception {
        flyway().migrate();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.business_item_purposes, public.business_operating_modes, public.business_activities");
        }
    }

    @Test
    void newTablesMigrateWithExpectedTenantIsolation() throws Exception {
        var businessA = UUID.randomUUID();
        var businessB = UUID.randomUUID();
        insertBusiness(businessA, "Understanding A");
        insertBusiness(businessB, "Understanding B");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, businessA);
            insertActivity(connection, businessA, "CONFEITARIA", null);
            insertMode(connection, businessA, "PRODUCES_GOODS");
            connection.commit();
        }

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, businessB);
            assertThat(count(connection, "business_activities")).isZero();
            assertThat(count(connection, "business_operating_modes")).isZero();
            connection.commit();
        }
    }

    @Test
    void rowLevelSecurityFailsClosedAndRejectsCrossTenantWrites() throws Exception {
        var businessA = UUID.randomUUID();
        var businessB = UUID.randomUUID();
        insertBusiness(businessA, "RLS A");
        insertBusiness(businessB, "RLS B");

        try (var connection = appConnection()) {
            assertThat(count(connection, "business_activities")).isZero();
            connection.setAutoCommit(false);
            setTenant(connection, businessA);
            assertThatThrownBy(() -> insertActivity(connection, businessB, "MERCADINHO", null))
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void constraintsProtectActivityOtherLabelAndOperatingModeVocabulary() throws Exception {
        var business = UUID.randomUUID();
        insertBusiness(business, "Constraints");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, business);
            assertThatThrownBy(() -> insertActivity(connection, business, "OTHER", null))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertMode(connection, business, "NOT_A_MODE"))
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void jooqPurposeUpsertIsCorrectableAndRejectsProductFromAnotherBusiness() throws Exception {
        var businessA = UUID.randomUUID();
        var businessB = UUID.randomUUID();
        var productA = UUID.randomUUID();
        var productB = UUID.randomUUID();
        insertBusiness(businessA, "Purpose A");
        insertBusiness(businessB, "Purpose B");
        insertProduct(businessA, productA, "Açúcar");
        insertProduct(businessB, productB, "Açúcar");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, businessA);
            var repository = new JooqBusinessUnderstandingRepository(DSL.using(connection, SQLDialect.POSTGRES));
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(
                    new BusinessId(businessA), productA, ItemPurpose.PRODUCTION, NOW.toInstant()));
            assertThat(repository.findPurposeByProduct(new BusinessId(businessA), productA).orElseThrow().purpose())
                    .isEqualTo(ItemPurpose.PRODUCTION);
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(
                    new BusinessId(businessA), productA, ItemPurpose.RESALE, NOW.toInstant()));
            var corrected = repository.findPurposeByProduct(new BusinessId(businessA), productA).orElseThrow();
            assertThat(corrected.purpose()).isEqualTo(ItemPurpose.RESALE);
            assertThat(corrected.evidenceCount()).isEqualTo(2);
            assertThatThrownBy(() -> repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(
                    new BusinessId(businessA), productB, ItemPurpose.RESALE, NOW.toInstant())))
                    .isInstanceOf(BusinessUnderstandingNotFoundException.class);
            connection.rollback();
        }
    }

    @Test
    void sameProductSupportsDifferentPurposesPerUsageContextAndKeepsEvidence() throws Exception {
        var business = UUID.randomUUID();
        var product = UUID.randomUUID();
        insertBusiness(business, "Contextual Purpose");
        insertProduct(business, product, "Shampoo");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, business);
            var repository = new JooqBusinessUnderstandingRepository(DSL.using(connection, SQLDialect.POSTGRES));
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(new BusinessId(business), product,
                    com.tino.backend.businessunderstanding.domain.model.UsageContext.of("SERVICE_CONSUMPTION"),
                    ItemPurpose.SERVICE_INPUT, UUID.randomUUID(), "Usado durante o atendimento", NOW.toInstant()));
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(new BusinessId(business), product,
                    com.tino.backend.businessunderstanding.domain.model.UsageContext.of("DIRECT_SALE"),
                    ItemPurpose.RESALE, UUID.randomUUID(), "Vendido separadamente", NOW.toInstant()));

            var service = repository.findPurposeByProduct(new BusinessId(business), product,
                    com.tino.backend.businessunderstanding.domain.model.UsageContext.of("SERVICE_CONSUMPTION"))
                    .orElseThrow();
            var sale = repository.findPurposeByProduct(new BusinessId(business), product,
                    com.tino.backend.businessunderstanding.domain.model.UsageContext.of("DIRECT_SALE"))
                    .orElseThrow();
            assertThat(service.purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
            assertThat(sale.purpose()).isEqualTo(ItemPurpose.RESALE);
            assertThat(service.evidenceReason()).isEqualTo("Usado durante o atendimento");
            assertThat(service.evidenceClassifiedBy()).startsWith("USER:");
            assertThat(service.evidenceAt()).isEqualTo(NOW.toInstant().truncatedTo(ChronoUnit.MICROS));
            connection.rollback();
        }
    }

    @Test
    void automaticAuthorityNeverReplacesUserConfirmationButExplicitCorrectionDoes() throws Exception {
        var business = UUID.randomUUID();
        var product = UUID.randomUUID();
        insertBusiness(business, "Authority");
        insertProduct(business, product, "Shampoo");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, business);
            var repository = new JooqBusinessUnderstandingRepository(DSL.using(connection, SQLDialect.POSTGRES));
            var context = com.tino.backend.businessunderstanding.domain.model.UsageContext.of("DIRECT_SALE");
            repository.upsertAutomaticPurpose(classification(business, product, context, ItemPurpose.RESALE,
                    ItemPurposeSource.SYSTEM_SUGGESTED, "SYSTEM", "Initial suggestion"));
            repository.upsertAutomaticPurpose(classification(business, product, context, ItemPurpose.PRODUCTION,
                    ItemPurposeSource.LEARNED, "SYSTEM", "Learned from evidence"));
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(new BusinessId(business), product,
                    context, ItemPurpose.SERVICE_INPUT, UUID.randomUUID(), "Correção explícita", NOW.toInstant()));
            repository.upsertAutomaticPurpose(classification(business, product, context, ItemPurpose.RESALE,
                    ItemPurposeSource.LEARNED, "SYSTEM", "Late automatic suggestion"));

            var stored = repository.findPurposeByProduct(new BusinessId(business), product, context).orElseThrow();
            assertThat(stored.purpose()).isEqualTo(ItemPurpose.SERVICE_INPUT);
            assertThat(stored.source()).isEqualTo(ItemPurposeSource.USER_CONFIRMED);
            assertThat(stored.evidenceReason()).isEqualTo("Correção explícita");
            assertThat(stored.evidenceCount()).isEqualTo(3);
            connection.rollback();
        }
    }

    @Test
    void purposeWritesRemainBlockedAcrossTenants() throws Exception {
        var businessA = UUID.randomUUID();
        var businessB = UUID.randomUUID();
        var productA = UUID.randomUUID();
        insertBusiness(businessA, "Purpose tenant A");
        insertBusiness(businessB, "Purpose tenant B");
        insertProduct(businessA, productA, "Açúcar");

        try (var connection = appConnection()) {
            connection.setAutoCommit(false);
            setTenant(connection, businessB);
            var repository = new JooqBusinessUnderstandingRepository(DSL.using(connection, SQLDialect.POSTGRES));
            assertThatThrownBy(() -> repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(
                    new BusinessId(businessA), productA,
                    com.tino.backend.businessunderstanding.domain.model.UsageContext.of("DIRECT_SALE"),
                    ItemPurpose.RESALE, UUID.randomUUID(), "Tentativa cross-tenant", NOW.toInstant())))
                    .isInstanceOf(BusinessUnderstandingNotFoundException.class);
            connection.rollback();
        }
    }

    private static BusinessItemPurpose classification(UUID businessId, UUID productId,
            com.tino.backend.businessunderstanding.domain.model.UsageContext context, ItemPurpose purpose,
            ItemPurposeSource source, String classifier, String reason) {
        return new BusinessItemPurpose(UUID.randomUUID(), new BusinessId(businessId), productId, null, context,
                purpose, source, new BigDecimal("0.60"), 1, classifier, reason, NOW.toInstant(), NOW.toInstant(),
                NOW.toInstant(), NOW.toInstant(), NOW.toInstant());
    }

    private static Flyway flyway() {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load();
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP,
                POSTGRES.appPassword());
    }

    private static void insertBusiness(UUID id, String name) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                "INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) VALUES (?, ?, 'OTHER', 'ACTIVE', ?, ?)")) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setObject(3, NOW);
            statement.setObject(4, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertProduct(UUID businessId, UUID productId, String name) throws SQLException {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(
                "INSERT INTO public.products (id, business_id, name, base_unit, status, created_at, updated_at) VALUES (?, ?, ?, 'UN', 'ACTIVE', ?, ?)")) {
            statement.setObject(1, productId);
            statement.setObject(2, businessId);
            statement.setString(3, name);
            statement.setObject(4, NOW);
            statement.setObject(5, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertActivity(Connection connection, UUID businessId, String code, String label)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO public.business_activities (id, business_id, activity_code, custom_label, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, businessId);
            statement.setString(3, code);
            statement.setString(4, label);
            statement.setObject(5, NOW);
            statement.setObject(6, NOW);
            statement.executeUpdate();
        }
    }

    private static void insertMode(Connection connection, UUID businessId, String code) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO public.business_operating_modes (id, business_id, mode_code, source, created_at, updated_at) VALUES (?, ?, ?, 'USER_DECLARED', ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, businessId);
            statement.setString(3, code);
            statement.setObject(4, NOW);
            statement.setObject(5, NOW);
            statement.executeUpdate();
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(
                "SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void setTenant(Connection connection, UUID businessId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.business_id', ?, true)")) {
            statement.setString(1, businessId.toString());
            statement.executeQuery();
        }
    }
}
