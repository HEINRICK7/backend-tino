package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.usecase.ExecuteAuthorizedBusinessOperation;
import com.tino.backend.business.application.usecase.ResolveBusinessAccess;
import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessName;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.device.adapter.out.persistence.JooqDeviceInstallationRepository;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.application.usecase.RegisterDeviceInstallation;
import com.tino.backend.device.application.usecase.ResolveDeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallationId;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.identity.adapter.out.persistence.JooqUserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.shared.infrastructure.tenant.PostgresTenantContextExecutor;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidV7Generator;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real PostgreSQL gates for the M4 schema, RLS, repository and lifecycle. */
@Testcontainers
class M4DevicePostgresTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-08-27T12:34:56.123456Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private static final UuidV7Generator IDS = new UuidV7Generator();

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    private static HikariDataSource appDataSource;
    private static org.jooq.DSLContext appDsl;
    private static JooqUserRepository users;
    private static JooqBusinessRepositoryFacade businessRepositories;
    private static DeviceInstallationRepository installations;
    private static RegisterDeviceInstallation register;
    private static ResolveDeviceInstallation resolve;
    private static TenantContextExecutor tenantContext;

    @BeforeAll
    static void migrateAndConfigure() {
        migrate(POSTGRES).migrate();

        appDataSource = new HikariDataSource();
        appDataSource.setJdbcUrl(POSTGRES.getJdbcUrl());
        appDataSource.setUsername(M2PostgresTestContainer.APP);
        appDataSource.setPassword(POSTGRES.appPassword());
        appDataSource.setMaximumPoolSize(24);
        appDataSource.setMinimumIdle(2);
        appDataSource.setConnectionTimeout(10_000);

        var transactionAware = new TransactionAwareDataSourceProxy(appDataSource);
        appDsl = DSL.using(transactionAware, SQLDialect.POSTGRES);
        users = new JooqUserRepository(appDsl);
        businessRepositories = new JooqBusinessRepositoryFacade(appDsl);
        installations = new JooqDeviceInstallationRepository(appDsl);
        var transactionManager = new DataSourceTransactionManager(appDataSource);
        tenantContext = new PostgresTenantContextExecutor(appDataSource, transactionManager);
        var access = new ResolveBusinessAccess(
                businessRepositories.memberships(), businessRepositories.businesses());
        var authorized = new ExecuteAuthorizedBusinessOperation(access, tenantContext);
        var businessAuthorization = businessAuthorization(authorized);
        register = new RegisterDeviceInstallation(businessAuthorization, installations, IDS, CLOCK);
        resolve = new ResolveDeviceInstallation(businessAuthorization, installations);
    }

    @AfterAll
    static void closeDataSource() {
        if (appDataSource != null) {
            appDataSource.close();
        }
    }

    @BeforeEach
    void clearData() throws Exception {
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.message_delivery_evidence, public.message_outbox, public.messages, public.message_consent_audit, public.message_consents, public.reconciliation_items, public.reconciliation_runs, public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void testM4_001_authorizedUserRegistersInstallation() {
        var userId = createUser("m4-register-user");
        var businessId = createBusiness(userId, "M4 Register Business");

        var created = register.execute(userId, businessId, "m4-registration");

        assertThat(created.businessId()).isEqualTo(businessId);
        assertThat(created.externalId().value()).isEqualTo("m4-registration");
        assertThat(created.registeredByUserId()).isEqualTo(userId);
    }

    @Test
    void testM4_002_internalInstallationIdentifierIsUuidV7() {
        var userId = createUser("m4-uuid-user");
        var businessId = createBusiness(userId, "M4 UUID Business");

        assertThat(register.execute(userId, businessId, "m4-uuid").id().value().version())
                .isEqualTo(7);
    }

    @Test
    void testM4_003_newInstallationIsActiveByDefault() {
        var userId = createUser("m4-active-user");
        var businessId = createBusiness(userId, "M4 Active Business");

        assertThat(register.execute(userId, businessId, "m4-active").status())
                .isEqualTo(InstallationStatus.ACTIVE);
    }

    @Test
    void testM4_004_businessForeignKeyIsPhysical() throws Exception {
        var userId = createUser("m4-business-fk-user");

        assertThatThrownBy(() -> insertRaw(
                newDeviceId(), new BusinessId(IDS.next()), "m4-invalid-business", userId,
                InstallationStatus.ACTIVE))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testM4_005_registeredUserForeignKeyIsPhysical() throws Exception {
        var businessId = createBusiness(createUser("m4-user-fk-owner"), "M4 FK Business");

        assertThatThrownBy(() -> insertRaw(
                newDeviceId(), businessId, "m4-invalid-user", IDS.next(), InstallationStatus.ACTIVE))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testM4_006_statusCheckRejectsUnknownValue() throws Exception {
        var userId = createUser("m4-status-check-user");
        var businessId = createBusiness(userId, "M4 Status Business");

        assertThatThrownBy(() -> insertRawStatus(
                newDeviceId(), businessId, "m4-invalid-status", userId, "INVALID"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testM4_007_externalInstallationIdIsGloballyUnique() throws Exception {
        var userId = createUser("m4-unique-user");
        var businessId = createBusiness(userId, "M4 Unique Business");
        register.execute(userId, businessId, "m4-unique");

        assertThatThrownBy(() -> insertRaw(
                newDeviceId(), businessId, "m4-unique", userId, InstallationStatus.ACTIVE))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void testM4_008_sameBusinessRegistrationIsIdempotent() throws Exception {
        var userId = createUser("m4-idempotent-user");
        var businessId = createBusiness(userId, "M4 Idempotent Business");

        var first = register.execute(userId, businessId, "m4-idempotent");
        var second = register.execute(userId, businessId, "m4-idempotent");

        assertThat(second).isEqualTo(first);
        assertThat(adminCount("device_installations")).isEqualTo(1L);
    }

    @Test
    void testM4_009_crossBusinessReassignmentIsDenied() {
        var userA = createUser("m4-cross-a");
        var userB = createUser("m4-cross-b");
        var businessA = createBusiness(userA, "M4 Cross A");
        var businessB = createBusiness(userB, "M4 Cross B");
        register.execute(userA, businessA, "m4-cross-installation");

        assertThatThrownBy(() -> register.execute(userB, businessB, "m4-cross-installation"))
                .isInstanceOf(DeviceInstallationAccessDeniedException.class);
    }

    @Test
    void testM4_010_missingMembershipIsDenied() {
        var member = createUser("m4-member-owner");
        var outsider = createUser("m4-no-membership");
        var businessId = createBusiness(member, "M4 Membership Business");

        assertThatThrownBy(() -> register.execute(outsider, businessId, "m4-missing-membership"))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM4_011_disabledMembershipIsDenied() throws Exception {
        var userId = createUser("m4-disabled-membership");
        var businessId = createBusiness(userId, "M4 Disabled Membership");
        setStatus("business_memberships", membershipId(userId, businessId), "DISABLED");

        assertThatThrownBy(() -> register.execute(userId, businessId, "m4-disabled-membership"))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM4_012_disabledBusinessIsDenied() throws Exception {
        var userId = createUser("m4-disabled-business");
        var businessId = createBusiness(userId, "M4 Disabled Business");
        setStatus("businesses", businessId.value(), "DISABLED");

        assertThatThrownBy(() -> register.execute(userId, businessId, "m4-disabled-business"))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM4_013_clientBusinessIdIsOnlyAnAuthorizedRequestedTarget() {
        var userA = createUser("m4-client-business-a");
        var userB = createUser("m4-client-business-b");
        var businessA = createBusiness(userA, "M4 Client A");
        var businessB = createBusiness(userB, "M4 Client B");

        assertThatThrownBy(() -> register.execute(userA, businessB, "m4-client-target"))
                .isInstanceOf(BusinessAccessDeniedException.class);
        assertThat(register.execute(userA, businessA, "m4-client-target-a").businessId())
                .isEqualTo(businessA);
    }

    @Test
    void testM4_014_installationIdentifierAloneIsNotAuthority() throws Exception {
        var userId = createUser("m4-id-not-authority");
        var businessId = createBusiness(userId, "M4 Installation Authority");
        register.execute(userId, businessId, "m4-not-authority");

        assertThat(installations.findByExternalId(new InstallationExternalId("m4-not-authority")))
                .isEmpty();
        assertThatThrownBy(() -> resolve.execute(
                userId, new BusinessId(IDS.next()), "m4-not-authority"))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM4_015_storeIdIsNotPersistedOrAuthority() throws Exception {
        var userId = createUser("m4-store-id-user");
        var businessId = createBusiness(userId, "M4 Store Id Business");
        var created = register.execute(userId, businessId, "m4-store-id-installation");

        assertThat(columns("device_installations"))
                .doesNotContain("store_id", "device_id", "installation_id");
        assertThat(created.businessId()).isEqualTo(businessId);
    }

    @Test
    void testM4_016_authorizationPrecedesTenantContextAndPersistence() {
        var events = new ArrayList<String>();
        var userId = createUser("m4-order-user");
        var businessId = createBusiness(userId, "M4 Order Business");
        var tracedAuthorization = new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID id, BusinessId requested, java.util.function.Function<BusinessId, T> operation) {
                events.add("business-authorization");
                return tenantContext.execute(requested, () -> {
                    events.add("tenant-context");
                    return operation.apply(requested);
                });
            }
        };
        var traced = new RegisterDeviceInstallation(
                tracedAuthorization,
                installationRepositoryWithEvents(events),
                IDS,
                CLOCK);

        traced.execute(userId, businessId, "m4-order");

        assertThat(events).startsWith("business-authorization", "tenant-context", "repository");
    }

    @Test
    void testM4_017_rlsShowsOwnBusinessInstallation() {
        var userId = createUser("m4-rls-own-user");
        var businessId = createBusiness(userId, "M4 RLS Own");
        var installation = register.execute(userId, businessId, "m4-rls-own");

        assertThat(tenantContext.execute(businessId,
                () -> installations.findByExternalId(installation.externalId())))
                .contains(installation);
    }

    @Test
    void testM4_018_rlsHidesCrossBusinessInstallation() {
        var userA = createUser("m4-rls-a");
        var userB = createUser("m4-rls-b");
        var businessA = createBusiness(userA, "M4 RLS A");
        var businessB = createBusiness(userB, "M4 RLS B");
        var installation = register.execute(userA, businessA, "m4-rls-cross");

        assertThat(tenantContext.execute(businessB,
                () -> installations.findByExternalId(installation.externalId())))
                .isEmpty();
    }

    @Test
    void testM4_019_rlsFailsClosedWithoutTenantContext() {
        assertThat(installations.findByExternalId(new InstallationExternalId("m4-no-tenant")))
                .isEmpty();
        var userId = createUser("m4-no-tenant-user");
        var businessId = createBusiness(userId, "M4 No Tenant");
        var candidate = DeviceInstallation.active(
                new DeviceInstallationId(IDS.next()), businessId,
                new InstallationExternalId("m4-no-tenant-write"), userId, FIXED_TIME, FIXED_TIME);

        assertThatThrownBy(() -> installations.insertIfAbsent(candidate))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testM4_020_tenantContextResetsAfterCommit() {
        var businessId = new BusinessId(IDS.next());

        assertThat(tenantContext.execute(businessId, () -> currentTenantSetting()))
                .isEqualTo(businessId.value().toString());
        assertThat(currentTenantSetting()).isBlank();
    }

    @Test
    void testM4_021_tenantContextResetsAfterRollback() {
        var businessId = new BusinessId(IDS.next());

        assertThatThrownBy(() -> tenantContext.execute(businessId, () -> {
            assertThat(currentTenantSetting()).isEqualTo(businessId.value().toString());
            throw new IllegalStateException("rollback probe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(currentTenantSetting()).isBlank();
    }

    @Test
    void testM4_022_concurrentRegistrationProducesOneIdentity() throws Exception {
        var userId = createUser("m4-concurrent-user");
        var businessId = createBusiness(userId, "M4 Concurrent Business");
        var barrier = new CyclicBarrier(20);
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            var tasks = IntStream.range(0, 20).mapToObj(ignored -> (java.util.concurrent.Callable<DeviceInstallation>) () -> {
                barrier.await(30, TimeUnit.SECONDS);
                return register.execute(userId, businessId, "m4-concurrent");
            }).toList();
            var results = executor.invokeAll(tasks);
            var installationsReturned = new ArrayList<DeviceInstallation>();
            for (var result : results) {
                installationsReturned.add(result.get());
            }
            assertThat(installationsReturned).hasSize(20);
            assertThat(installationsReturned.stream().map(DeviceInstallation::id).distinct()).hasSize(1);
            assertThat(adminCount("device_installations")).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testM4_023_revokedInstallationIsDenied() throws Exception {
        var userId = createUser("m4-revoked-user");
        var businessId = createBusiness(userId, "M4 Revoked Business");
        var installation = register.execute(userId, businessId, "m4-revoked");
        setStatus("device_installations", installation.id().value(), "REVOKED");

        assertThatThrownBy(() -> resolve.execute(userId, businessId, "m4-revoked"))
                .isInstanceOf(RevokedDeviceInstallationException.class);
    }

    @Test
    void testM4_024_revokedInstallationIsNeverAutoReactivated() throws Exception {
        var userId = createUser("m4-no-reactivation-user");
        var businessId = createBusiness(userId, "M4 No Reactivation");
        var installation = register.execute(userId, businessId, "m4-no-reactivation");
        setStatus("device_installations", installation.id().value(), "REVOKED");

        assertThatThrownBy(() -> register.execute(userId, businessId, "m4-no-reactivation"))
                .isInstanceOf(RevokedDeviceInstallationException.class);
        assertThat(adminStatus(installation.id().value())).isEqualTo("REVOKED");
    }

    @Test
    void testM4_025_businessMayHaveMultipleInstallations() {
        var userId = createUser("m4-multiple-installations-user");
        var businessId = createBusiness(userId, "M4 Multiple Installations");

        register.execute(userId, businessId, "m4-multiple-one");
        register.execute(userId, businessId, "m4-multiple-two");

        assertThat(adminCount("device_installations")).isEqualTo(2L);
    }

    @Test
    void testM4_026_userCanKeepMultipleBusinesses() {
        var userId = createUser("m4-multi-business-user");
        var businessA = createBusiness(userId, "M4 Multi Business A");
        var businessB = createBusiness(userId, "M4 Multi Business B");

        assertThat(register.execute(userId, businessA, "m4-multi-a").businessId()).isEqualTo(businessA);
        assertThat(register.execute(userId, businessB, "m4-multi-b").businessId()).isEqualTo(businessB);
        assertThat(adminCount("businesses")).isEqualTo(2L);
    }

    @Test
    void testM4_032_timestamptzRoundTripsAsInstant() {
        var userId = createUser("m4-timestamp-user");
        var businessId = createBusiness(userId, "M4 Timestamp Business");
        var installation = register.execute(userId, businessId, "m4-timestamp");

        var fetched = tenantContext.execute(businessId,
                () -> installations.findByExternalId(installation.externalId()).orElseThrow());

        assertThat(fetched.createdAt()).isEqualTo(FIXED_TIME);
        assertThat(fetched.updatedAt()).isEqualTo(FIXED_TIME);
        assertThat(timestampTypes()).containsExactly(
                "timestamp with time zone", "timestamp with time zone");
    }

    @Test
    void testM4_033_emptyDatabaseMigratesFromM0ThroughM4() {
        try (var fresh = new M2PostgresTestContainer()) {
            fresh.start();
            var result = migrate(fresh).migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(10);
        }
    }

    @Test
    void testM4_034_flywayValidatePasses() {
        assertThatCode(() -> migrate(POSTGRES).validate()).doesNotThrowAnyException();
    }

    @Test
    void testM4_035_jooqRepositoryRunsAgainstPostgresql() {
        var userId = createUser("m4-jooq-user");
        var businessId = createBusiness(userId, "M4 jOOQ Business");
        var created = register.execute(userId, businessId, "m4-jooq");

        assertThat(tenantContext.execute(businessId,
                () -> installations.findByExternalId(created.externalId())))
                .contains(created);
        assertThat(appDsl.fetchValue("select current_database()", String.class))
                .isEqualTo("tino");
    }

    @Test
    void testM4_040_runtimeAndMigrationRolesAreLeastPrivileged() throws Exception {
        var role = adminQuery("select rolsuper, rolbypassrls, rolcreatedb, rolcreaterole "
                + "from pg_roles where rolname = 'tino_app'");
        assertThat(role).containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false)
                .containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false);
        assertThat(adminValue("select has_table_privilege('tino_app', "
                + "'public.device_installations', 'SELECT')", Boolean.class)).isTrue();
        assertThat(adminValue("select has_table_privilege('tino_app', "
                + "'public.device_installations', 'INSERT')", Boolean.class)).isTrue();
        assertThat(adminValue("select has_table_privilege('tino_app', "
                + "'public.device_installations', 'UPDATE')", Boolean.class)).isFalse();
        assertThat(adminValue("select has_table_privilege('tino_app', "
                + "'public.device_installations', 'DELETE')", Boolean.class)).isFalse();
        assertThat(adminValue("select relrowsecurity from pg_class "
                + "where oid = 'public.device_installations'::regclass", Boolean.class)).isTrue();
        assertThat(adminValue("select relforcerowsecurity from pg_class "
                + "where oid = 'public.device_installations'::regclass", Boolean.class)).isTrue();
    }

    private static BusinessAuthorization businessAuthorization(
            ExecuteAuthorizedBusinessOperation authorized) {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID authenticatedUserId, BusinessId requestedBusinessId,
                    java.util.function.Function<BusinessId, T> operation) {
                try {
                    return authorized.execute(new UserId(authenticatedUserId), requestedBusinessId,
                            context -> operation.apply(context.businessId()));
                } catch (BusinessAccessDeniedException exception) {
                    throw exception;
                }
            }
        };
    }

    private static Flyway migrate(M2PostgresTestContainer postgres) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        postgres.migratorPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private UUID createUser(String subject) {
        var user = User.active(
                new com.tino.backend.identity.domain.model.UserId(IDS.next()),
                new ExternalSubject(subject), FIXED_TIME, FIXED_TIME);
        return users.insert(user).id().value();
    }

    private BusinessId createBusiness(UUID userId, String name) {
        var id = new BusinessId(IDS.next());
        var business = Business.active(id, new BusinessName(name), BusinessVertical.OTHER,
                FIXED_TIME, FIXED_TIME);
        var owner = BusinessMembership.owner(
                new MembershipId(IDS.next()), id, new UserId(userId), FIXED_TIME, FIXED_TIME);
        businessRepositories.businesses().createWithOwner(business, owner);
        return id;
    }

    private UUID membershipId(UUID userId, BusinessId businessId) throws Exception {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "select id from public.business_memberships where user_id = ? and business_id = ?")) {
            statement.setObject(1, userId);
            statement.setObject(2, businessId.value());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static DeviceInstallationRepository installationRepositoryWithEvents(
            List<String> events) {
        return new DeviceInstallationRepository() {
            @Override
            public int insertIfAbsent(DeviceInstallation installation) {
                events.add("repository");
                return installations.insertIfAbsent(installation);
            }

            @Override
            public java.util.Optional<DeviceInstallation> findByExternalId(InstallationExternalId externalId) {
                events.add("repository");
                return installations.findByExternalId(externalId);
            }
        };
    }

    private static DeviceInstallationId newDeviceId() {
        return new DeviceInstallationId(IDS.next());
    }

    private static void insertRaw(
            DeviceInstallationId id, BusinessId businessId, String externalId,
            UUID registeredByUserId, InstallationStatus status) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "insert into public.device_installations "
                                + "(id, business_id, installation_external_id, status, "
                                + "registered_by_user_id, created_at, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, id.value());
            statement.setObject(2, businessId.value());
            statement.setString(3, externalId);
            statement.setString(4, status.name());
            statement.setObject(5, registeredByUserId);
            statement.setObject(6, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.setObject(7, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private static void insertRawStatus(
            DeviceInstallationId id, BusinessId businessId, String externalId,
            UUID registeredByUserId, String status) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "insert into public.device_installations "
                                + "(id, business_id, installation_external_id, status, "
                                + "registered_by_user_id, created_at, updated_at) "
                                + "values (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, id.value());
            statement.setObject(2, businessId.value());
            statement.setString(3, externalId);
            statement.setString(4, status);
            statement.setObject(5, registeredByUserId);
            statement.setObject(6, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.setObject(7, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private static void setStatus(String table, UUID id, String status) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "update public." + table + " set status = ? where id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
    }

    private static String adminStatus(UUID id) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "select status from public.device_installations where id = ?")) {
            statement.setObject(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static long adminCount(String table) {
        return adminValue("select count(*) from public." + table, Long.class);
    }

    private static List<String> columns(String table) throws SQLException {
        try (var connection = adminConnection();
                var statement = connection.prepareStatement(
                        "select column_name from information_schema.columns "
                                + "where table_schema = 'public' and table_name = ? "
                                + "order by ordinal_position")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                var columns = new ArrayList<String>();
                while (result.next()) {
                    columns.add(result.getString(1));
                }
                return columns;
            }
        }
    }

    private static List<String> timestampTypes() {
        return adminQueryList("select data_type from information_schema.columns "
                + "where table_schema = 'public' and table_name = 'device_installations' "
                + "and column_name in ('created_at', 'updated_at') order by ordinal_position");
    }

    private static String currentTenantSetting() {
        var value = appDsl.fetchValue("select current_setting('app.business_id', true)");
        return value == null ? "" : value.toString();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static <T> T adminValue(String sql, Class<T> type) {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getObject(1, type);
        } catch (SQLException exception) {
            throw new IllegalStateException("admin query failed", exception);
        }
    }

    private static java.util.Map<String, Object> adminQuery(String sql) {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            var metadata = result.getMetaData();
            var values = new java.util.HashMap<String, Object>();
            for (var i = 1; i <= metadata.getColumnCount(); i++) {
                values.put(metadata.getColumnLabel(i), result.getObject(i));
            }
            return values;
        } catch (SQLException exception) {
            throw new IllegalStateException("admin query failed", exception);
        }
    }

    private static List<String> adminQueryList(String sql) {
        try (var connection = adminConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery(sql)) {
            var values = new ArrayList<String>();
            while (result.next()) {
                values.add(result.getString(1));
            }
            return values;
        } catch (SQLException exception) {
            throw new IllegalStateException("admin query failed", exception);
        }
    }

    /** Keeps the two existing jOOQ adapters together without exposing generated records. */
    private record JooqBusinessRepositoryFacade(
            BusinessRepository businesses, BusinessMembershipRepository memberships) {
        private JooqBusinessRepositoryFacade(org.jooq.DSLContext dsl) {
            this(new com.tino.backend.business.adapter.out.persistence.JooqBusinessRepository(dsl),
                    new com.tino.backend.business.adapter.out.persistence.JooqBusinessMembershipRepository(dsl));
        }
    }
}
