package com.tino.backend.shared.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresTenantContextExecutorTest {
    private static final String APP_ROLE = "tino_app";
    private static final UUID BUSINESS_A = UUID.fromString("018f0b8e-5e2d-7abc-8a01-000000000001");
    private static final UUID BUSINESS_B = UUID.fromString("018f0b8e-5e2d-7abc-8a01-000000000002");
    private static final UUID PROBE_A = UUID.fromString("018f0b8e-5e2d-7abc-8a01-000000000011");
    private static final UUID PROBE_B = UUID.fromString("018f0b8e-5e2d-7abc-8a01-000000000012");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static HikariDataSource adminDataSource;
    private static HikariDataSource appDataSource;
    private static JdbcTemplate admin;
    private static JdbcTemplate app;
    private static DSLContext appDsl;
    private static PostgresTenantContextExecutor executor;
    private static String ephemeralCredential;

    @BeforeAll
    static void createRoleAndFixture() {
        ephemeralCredential = UUID.randomUUID().toString();
        adminDataSource = dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), 2);
        admin = new JdbcTemplate(adminDataSource);
        createAppRoleAndProbeTable();

        appDataSource = dataSource(POSTGRES.getJdbcUrl(), APP_ROLE, ephemeralCredential, 1);
        appDataSource.setMinimumIdle(1);
        app = new JdbcTemplate(appDataSource);
        var transactionManager = new DataSourceTransactionManager(appDataSource);
        executor = new PostgresTenantContextExecutor(appDataSource, transactionManager);

        var transactionAwareDataSource = new TransactionAwareDataSourceProxy(appDataSource);
        appDsl = DSL.using(transactionAwareDataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void closeDataSources() {
        if (appDataSource != null) {
            appDataSource.close();
        }
        if (adminDataSource != null) {
            adminDataSource.close();
        }
    }

    @Test
    void appRoleIsRestrictedAndProbeHasForcedRls() {
        var role = admin.queryForMap(
                "select rolsuper, rolbypassrls, rolcreatedb, rolcreaterole "
                        + "from pg_roles where rolname = 'tino_app'");

        assertThat(role)
                .containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false);
        assertThat(admin.queryForObject(
                "select has_schema_privilege('tino_app', 'public', 'CREATE')", Boolean.class))
                .isFalse();
        assertThat(admin.queryForObject(
                "select pg_get_userbyid(nspowner) from pg_namespace where nspname = 'public'",
                String.class))
                .isNotEqualTo(APP_ROLE);
        assertThat(admin.queryForObject(
                "select pg_get_userbyid(relowner) from pg_class "
                        + "where oid = 'public.tenant_probe'::regclass",
                String.class))
                .isNotEqualTo(APP_ROLE);
        assertThat(admin.queryForObject(
                "select relrowsecurity from pg_class where oid = 'public.tenant_probe'::regclass",
                Boolean.class))
                .isTrue();
        assertThat(admin.queryForObject(
                "select relforcerowsecurity from pg_class where oid = 'public.tenant_probe'::regclass",
                Boolean.class))
                .isTrue();
        assertThat(app.queryForObject("select current_user", String.class)).isEqualTo(APP_ROLE);
        assertThatThrownBy(() -> app.execute(
                "create table public.m1_app_must_not_ddl (id uuid primary key)"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void rlsReadIsolationIsAAndBSpecific() {
        assertThat(visibleCount(new BusinessId(BUSINESS_A))).isEqualTo(1L);
        assertThat(visibleCount(new BusinessId(BUSINESS_B))).isEqualTo(1L);
        assertThat(visibleCountWithoutTenant()).isZero();
    }

    @Test
    void rlsRejectsCrossTenantInsert() {
        assertThatThrownBy(() -> executor.execute(new BusinessId(BUSINESS_A), () -> app.update(
                "insert into tenant_probe (id, business_id, value) values (?, ?, ?)",
                UUID.randomUUID(),
                BUSINESS_B,
                "must-be-rejected")))
                .isInstanceOf(DataAccessException.class);

        assertThat(admin.queryForObject(
                "select count(*) from tenant_probe where value = 'must-be-rejected'", Long.class))
                .isZero();
    }

    @Test
    void rlsRejectsCrossTenantUpdate() {
        assertThatThrownBy(() -> executor.execute(new BusinessId(BUSINESS_A), () -> app.update(
                "update tenant_probe set business_id = ? where id = ?",
                BUSINESS_B,
                PROBE_A)))
                .isInstanceOf(DataAccessException.class);

        assertThat(admin.queryForObject(
                "select business_id from tenant_probe where id = ?", UUID.class, PROBE_A))
                .isEqualTo(BUSINESS_A);
    }

    @Test
    void rlsProtectsOtherTenantFromDelete() {
        var deleted = executor.execute(new BusinessId(BUSINESS_A), () -> app.update(
                "delete from tenant_probe where id = ?", PROBE_B));

        assertThat(deleted).isZero();
        assertThat(admin.queryForObject(
                "select count(*) from tenant_probe where id = ?", Long.class, PROBE_B))
                .isEqualTo(1L);
    }

    @Test
    void noTenantContextFailsClosed() {
        assertThat(visibleCountWithoutTenant()).isZero();
        assertThatThrownBy(() -> app.update(
                "insert into tenant_probe (id, business_id, value) values (?, ?, ?)",
                UUID.randomUUID(),
                BUSINESS_A,
                "no-tenant-must-be-rejected"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void tenantContextIsAbsentAfterCommit() {
        assertThat(executor.execute(new BusinessId(BUSINESS_A), () -> currentTenantSetting()))
                .isEqualTo(BUSINESS_A.toString());

        assertThat(currentTenantSetting()).isBlank();
    }

    @Test
    void tenantContextIsAbsentAfterRollback() {
        assertThatThrownBy(() -> executor.execute(new BusinessId(BUSINESS_A), () -> {
            assertThat(currentTenantSetting()).isEqualTo(BUSINESS_A.toString());
            throw new IllegalStateException("rollback probe");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(currentTenantSetting()).isBlank();
    }

    @Test
    void repeatedTenantOperationsDoNotLeakAcrossPooledConnectionReuse() {
        for (int attempt = 0; attempt < 12; attempt++) {
            assertThat(visibleCount(new BusinessId(BUSINESS_A))).isEqualTo(1L);
            assertThat(currentTenantSetting()).isBlank();
            assertThat(visibleCount(new BusinessId(BUSINESS_B))).isEqualTo(1L);
            assertThat(currentTenantSetting()).isBlank();
            assertThat(visibleCountWithoutTenant()).isZero();
        }
    }

    @Test
    void dslContextExecutesAgainstRealPostgres() {
        assertThat(appDsl.fetchValue("select 1", Integer.class)).isEqualTo(1);
    }

    private static long visibleCount(BusinessId businessId) {
        return executor.execute(businessId, () -> (Long) appDsl.fetchValue(
                "select count(*) from tenant_probe", Long.class));
    }

    private static long visibleCountWithoutTenant() {
        return app.queryForObject("select count(*) from tenant_probe", Long.class);
    }

    private static String currentTenantSetting() {
        return app.queryForObject(
                "select current_setting('app.business_id', true)",
                (resultSet, rowNumber) -> resultSet.getString(1));
    }

    private static void createAppRoleAndProbeTable() {
        admin.execute("create role tino_app login password '" + ephemeralCredential
                + "' nosuperuser nobypassrls nocreatedb nocreaterole noinherit");
        admin.execute("grant connect on database \"" + POSTGRES.getDatabaseName() + "\" to tino_app");
        admin.execute("revoke create on schema public from public");
        admin.execute("grant usage on schema public to tino_app");
        admin.execute("create table public.tenant_probe ("
                + "id uuid primary key, business_id uuid not null, value varchar(200) not null)");
        admin.execute("alter table public.tenant_probe enable row level security");
        admin.execute("alter table public.tenant_probe force row level security");
        admin.execute("create policy tenant_probe_isolation on public.tenant_probe "
                + "using (business_id = nullif(current_setting('app.business_id', true), '')::uuid) "
                + "with check (business_id = nullif(current_setting('app.business_id', true), '')::uuid)");
        admin.execute("grant select, insert, update, delete on table public.tenant_probe to tino_app");
        admin.update(
                "insert into tenant_probe (id, business_id, value) values (?, ?, ?), (?, ?, ?)",
                PROBE_A,
                BUSINESS_A,
                "business-a",
                PROBE_B,
                BUSINESS_B,
                "business-b");
    }

    private static HikariDataSource dataSource(
            String jdbcUrl, String username, String password, int maximumPoolSize) {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setConnectionTimeout(5_000);
        return dataSource;
    }
}
