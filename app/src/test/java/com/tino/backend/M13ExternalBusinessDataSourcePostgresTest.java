package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.catalog.application.model.ExternalPriceOptionProjection;
import com.tino.backend.catalog.application.model.ExternalProductProjection;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.external.application.port.out.ExternalBusinessConnectionRepository;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for external connection, projection ownership, RLS and replay safety. */
@Testcontainers
@SpringBootTest
class M13ExternalBusinessDataSourcePostgresTest {
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-00000000013a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-00000000013b");
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired private ExternalBusinessConnectionRepository connections;
    @Autowired private ProductCatalog catalog;
    @Autowired private TenantContextExecutor tenants;
    @Autowired private DSLContext dsl;

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
    void migrateAndSeed() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()).locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword()); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.external_product_price_options, public.external_product_mappings, public.external_business_connections, public.products, public.businesses CASCADE");
            statement.execute("INSERT INTO public.businesses (id, trade_name, vertical, status, created_at, updated_at) VALUES "
                    + "('" + BUSINESS_A + "', 'External A', 'OTHER', 'ACTIVE', '2026-08-30T12:00:00Z', '2026-08-30T12:00:00Z'),"
                    + "('" + BUSINESS_B + "', 'External B', 'OTHER', 'ACTIVE', '2026-08-30T12:00:00Z', '2026-08-30T12:00:00Z')");
        }
    }

    @Test
    void connectionAndProjectionAreTenantScopedAndReplayKeepsOneProduct() {
        var business = new BusinessId(BUSINESS_A);
        var connection = tenants.execute(business, () -> connections.create(business, "DOCES_SONHOS", NOW));
        var projection = new ExternalProductProjection(connection.id(), "external-1", "Bolo", true, NOW, "P", "P",
                new BigDecimal("50.00"), List.of(new ExternalPriceOptionProjection("p", "P", new BigDecimal("1"), "P", "P",
                        new BigDecimal("50.00"), true)), "Bolos", "Tradicionais", NOW);

        var first = tenants.execute(business, () -> catalog.upsertExternalProduct(business, projection));
        var replay = tenants.execute(business, () -> catalog.upsertExternalProduct(business, projection));

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(tenants.execute(business, () -> dsl.fetchCount(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("public", "products"))))).isEqualTo(1);
        assertThat(tenants.execute(business, () -> catalog.search(business, "Bolo", null, 50)))
                .singleElement().satisfies(item -> assertThat(item.price()).isEqualByComparingTo("50.00"));
        assertThat(tenants.execute(new BusinessId(BUSINESS_B), () -> dsl.fetchCount(org.jooq.impl.DSL.table(org.jooq.impl.DSL.name("public", "products"))))).isZero();
        assertThat(tenants.execute(new BusinessId(BUSINESS_B), () -> connections.list(new BusinessId(BUSINESS_B)))).isEmpty();
    }
}
