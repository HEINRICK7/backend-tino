package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.adapter.out.persistence.JooqBusinessMembershipRepository;
import com.tino.backend.business.adapter.out.persistence.JooqBusinessRepository;
import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.DuplicateMembershipException;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.application.usecase.ListUserBusinesses;
import com.tino.backend.business.application.usecase.ResolveBusinessAccess;
import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessName;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
class M3BusinessPostgresTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-08-27T12:34:56.123456Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @Autowired
    private CreateBusiness createBusiness;

    @Autowired
    private ListUserBusinesses listUserBusinesses;

    @Autowired
    private ResolveBusinessAccess resolveBusinessAccess;

    @Autowired
    private JooqBusinessRepository businesses;

    @Autowired
    private JooqBusinessMembershipRepository memberships;

    @Autowired
    private UserRepository users;

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
    void clearM3Data() throws Exception {
        migrate(POSTGRES);
        try (var connection = migratorConnection(POSTGRES); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @Test
    void testM3_001_createBusinessWithActiveDefaults() {
        var user = createUser("m3-create-user");

        var created = createBusiness.execute(
                authenticated(user), "  Mercadinho São José  ", BusinessVertical.RETAIL);

        assertThat(created.business().status()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(created.business().tradeName().value()).isEqualTo("Mercadinho São José");
        assertThat(created.business().vertical()).isEqualTo(BusinessVertical.RETAIL);
        var persisted = businesses.findById(created.business().id()).orElseThrow();
        assertThat(persisted.id()).isEqualTo(created.business().id());
        assertThat(persisted.tradeName()).isEqualTo(created.business().tradeName());
        assertThat(persisted.vertical()).isEqualTo(created.business().vertical());
        assertThat(persisted.status()).isEqualTo(created.business().status());
        assertThat(Duration.between(created.business().createdAt(), persisted.createdAt()).abs())
                .isLessThan(Duration.ofMillis(1));
        assertThat(Duration.between(created.business().updatedAt(), persisted.updatedAt()).abs())
                .isLessThan(Duration.ofMillis(1));
    }

    @Test
    void testM3_002_creatorBecomesActiveOwner() {
        var user = createUser("m3-owner-user");

        var created = createBusiness.execute(
                authenticated(user), "Padaria Central", BusinessVertical.BAKERY);

        var owner = memberships.findByUserAndBusiness(
                new UserId(user.id().value()), created.business().id()).orElseThrow();
        assertThat(owner.id()).isEqualTo(created.membership().id());
        assertThat(owner.role()).isEqualTo(BusinessRole.OWNER);
        assertThat(owner.status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void testM3_003_businessAndOwnerRollbackTogetherWhenOwnerInsertFails() throws Exception {
        var business = Business.active(
                new BusinessId(new UuidV7Generator().next()),
                new BusinessName("Atomic failure"),
                BusinessVertical.OTHER,
                FIXED_TIME,
                FIXED_TIME);
        var owner = BusinessMembership.owner(
                new MembershipId(new UuidV7Generator().next()),
                business.id(),
                new UserId(new UuidV7Generator().next()),
                FIXED_TIME,
                FIXED_TIME);

        assertThatThrownBy(() -> businesses.createWithOwner(business, owner))
                .isInstanceOf(BusinessPersistenceException.class);
        assertThat(count("businesses")).isZero();
        assertThat(count("business_memberships")).isZero();
    }

    @Test
    void testM3_004_listOwnActiveBusinessesOnly() {
        var user = createUser("m3-list-user");
        var first = createBusiness.execute(authenticated(user), "First", BusinessVertical.STORE);
        var second = createBusiness.execute(authenticated(user), "Second", BusinessVertical.OTHER);

        var listed = listUserBusinesses.execute(new UserId(user.id().value()));

        assertThat(listed).extracting(item -> item.business().id())
                .containsExactly(first.business().id(), second.business().id());
    }

    @Test
    void testM3_005_listDoesNotExposeForeignBusiness() {
        var user = createUser("m3-list-mine");
        var foreignUser = createUser("m3-list-foreign");
        var mine = createBusiness.execute(authenticated(user), "Mine", BusinessVertical.RETAIL);
        createBusiness.execute(authenticated(foreignUser), "Foreign", BusinessVertical.RETAIL);

        var listed = listUserBusinesses.execute(new UserId(user.id().value()));

        assertThat(listed).extracting(item -> item.business().id())
                .containsExactly(mine.business().id());
    }

    @Test
    void testM3_006_activeMembershipAndBusinessAuthorizeAccess() {
        var user = createUser("m3-access-user");
        var created = createBusiness.execute(authenticated(user), "Access", BusinessVertical.STORE);

        var context = resolveBusinessAccess.execute(
                new UserId(user.id().value()), created.business().id());

        assertThat(context.userId()).isEqualTo(new UserId(user.id().value()));
        assertThat(context.businessId()).isEqualTo(created.business().id());
        assertThat(context.role()).isEqualTo(BusinessRole.OWNER);
    }

    @Test
    void testM3_007_missingMembershipIsDenied() {
        var user = createUser("m3-missing-membership");
        var foreign = createBusiness.execute(
                authenticated(createUser("m3-owned-by-other")), "Foreign", BusinessVertical.OTHER);

        assertThatThrownBy(() -> resolveBusinessAccess.execute(
                new UserId(user.id().value()), foreign.business().id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM3_008_disabledMembershipIsDenied() throws Exception {
        var user = createUser("m3-disabled-membership");
        var created = createBusiness.execute(authenticated(user), "Disabled link", BusinessVertical.OTHER);
        setStatus("business_memberships", created.membership().id().value(), "DISABLED");

        assertThatThrownBy(() -> resolveBusinessAccess.execute(
                new UserId(user.id().value()), created.business().id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM3_009_disabledBusinessIsDenied() throws Exception {
        var user = createUser("m3-disabled-business");
        var created = createBusiness.execute(authenticated(user), "Disabled tenant", BusinessVertical.OTHER);
        setStatus("businesses", created.business().id().value(), "DISABLED");

        assertThatThrownBy(() -> resolveBusinessAccess.execute(
                new UserId(user.id().value()), created.business().id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void testM3_010_sameUserMayOwnMultipleBusinesses() {
        var user = createUser("m3-multiple-businesses");
        createBusiness.execute(authenticated(user), "One", BusinessVertical.RETAIL);
        createBusiness.execute(authenticated(user), "Two", BusinessVertical.BAKERY);

        assertThat(listUserBusinesses.execute(new UserId(user.id().value()))).hasSize(2);
    }

    @Test
    void testM3_011_membershipUniqueConstraintIsTranslated() {
        var user = createUser("m3-unique-membership");
        var created = createBusiness.execute(authenticated(user), "Unique", BusinessVertical.OTHER);
        var duplicate = new BusinessMembership(
                new MembershipId(new UuidV7Generator().next()),
                created.business().id(),
                new UserId(user.id().value()),
                BusinessRole.STAFF,
                MembershipStatus.ACTIVE,
                FIXED_TIME,
                FIXED_TIME);

        assertThatThrownBy(() -> memberships.insert(duplicate))
                .isInstanceOf(DuplicateMembershipException.class);
    }

    @Test
    void testM3_012_businessIdentifierIsUuidV7() {
        var created = createBusiness.execute(
                authenticated(createUser("m3-business-uuid")), "UUID", BusinessVertical.OTHER);

        assertThat(created.business().id().value().version()).isEqualTo(7);
    }

    @Test
    void testM3_013_membershipIdentifierIsUuidV7() {
        var created = createBusiness.execute(
                authenticated(createUser("m3-membership-uuid")), "UUID", BusinessVertical.OTHER);

        assertThat(created.membership().id().value().version()).isEqualTo(7);
    }

    @Test
    void testM3_014_businessStatusCheckRejectsUnknownValue() throws Exception {
        assertCheckRejects(
                "INSERT INTO public.businesses "
                        + "(id, trade_name, vertical, status, created_at, updated_at) "
                        + "VALUES (?, 'Invalid', 'OTHER', 'UNKNOWN', ?, ?)");
    }

    @Test
    void testM3_015_businessVerticalCheckRejectsUnknownValue() throws Exception {
        assertCheckRejects(
                "INSERT INTO public.businesses "
                        + "(id, trade_name, vertical, status, created_at, updated_at) "
                        + "VALUES (?, 'Invalid', 'UNKNOWN', 'ACTIVE', ?, ?)");
    }

    @Test
    void testM3_016_membershipRoleCheckRejectsUnknownValue() throws Exception {
        var user = createUser("m3-invalid-role-user");
        var business = createBusiness.execute(authenticated(user), "Role", BusinessVertical.OTHER);
        assertCheckRejects(
                "INSERT INTO public.business_memberships "
                        + "(id, business_id, user_id, role, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', ?, ?)",
                business.business().id().value(),
                user.id().value());
    }

    @Test
    void testM3_017_membershipStatusCheckRejectsUnknownValue() throws Exception {
        var user = createUser("m3-invalid-membership-status-user");
        var business = createBusiness.execute(
                authenticated(user), "Membership status", BusinessVertical.OTHER);
        assertCheckRejects(
                "INSERT INTO public.business_memberships "
                        + "(id, business_id, user_id, role, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'STAFF', 'UNKNOWN', ?, ?)",
                business.business().id().value(),
                user.id().value());
    }

    @Test
    void testM3_018_businessForeignKeyIsPhysical() {
        var user = createUser("m3-business-fk-user");
        var membership = new BusinessMembership(
                new MembershipId(new UuidV7Generator().next()),
                new BusinessId(new UuidV7Generator().next()),
                new UserId(user.id().value()),
                BusinessRole.STAFF,
                MembershipStatus.ACTIVE,
                FIXED_TIME,
                FIXED_TIME);

        assertThatThrownBy(() -> memberships.insert(membership))
                .isInstanceOf(BusinessPersistenceException.class);
    }

    @Test
    void testM3_019_userForeignKeyIsPhysical() {
        var business = createBusinessWithMigrator("m3-user-fk-business");
        var membership = new BusinessMembership(
                new MembershipId(new UuidV7Generator().next()),
                business,
                new UserId(new UuidV7Generator().next()),
                BusinessRole.STAFF,
                MembershipStatus.ACTIVE,
                FIXED_TIME,
                FIXED_TIME);

        assertThatThrownBy(() -> memberships.insert(membership))
                .isInstanceOf(BusinessPersistenceException.class);
    }

    @Test
    void testM3_020_flywayMigratesEmptyDatabaseThroughM3() {
        try (var fresh = new M2PostgresTestContainer()) {
            fresh.start();
            var result = migrate(fresh).info();
            assertThat(result.applied()).hasSize(8);
            assertThat(result.current().getVersion().getVersion()).isEqualTo("7");
        }
    }

    @Test
    void testM3_021_flywayValidatePasses() {
        assertThatCode(() -> migrate(POSTGRES).validate()).doesNotThrowAnyException();
    }

    @Test
    void testM3_022_jooqBusinessAdapterRunsAgainstPostgresql() {
        var user = createUser("m3-jooq-user");
        var created = createBusiness.execute(authenticated(user), "jOOQ", BusinessVertical.STORE);

        assertThat(businesses.findById(created.business().id())).isPresent();
        assertThat(memberships.findByUserAndBusiness(
                new UserId(user.id().value()), created.business().id())).isPresent();
        assertThat(currentUser(M2PostgresTestContainer.APP)).isEqualTo(M2PostgresTestContainer.APP);
    }

    @Test
    void persistenceCrossBusinessAccessIsDenied() {
        var userA = createUser("m3-cross-a");
        var userB = createUser("m3-cross-b");
        var businessB = createBusiness.execute(authenticated(userB), "Business B", BusinessVertical.OTHER);

        assertThatThrownBy(() -> resolveBusinessAccess.execute(
                new UserId(userA.id().value()), businessB.business().id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void persistenceSchemaIsMinimalAndControlPlaneHasNoCircularRls() throws Exception {
        assertThat(columns("businesses")).containsExactly(
                "id", "trade_name", "vertical", "status", "created_at", "updated_at");
        assertThat(columns("business_memberships")).containsExactly(
                "id", "business_id", "user_id", "role", "status", "created_at", "updated_at");
        assertThat(columns("businesses")).doesNotContain(
                "business_id", "owner_user_id", "email", "phone", "store_id", "device_id");
        assertThat(rlsEnabled("businesses")).isFalse();
        assertThat(rlsEnabled("business_memberships")).isFalse();
    }

    @Test
    void testM3_034_timestampsRoundTripAsInstant() {
        var user = createUser("m3-timestamp-user");
        var business = Business.active(
                new BusinessId(new UuidV7Generator().next()),
                new BusinessName("Timestamp"),
                BusinessVertical.OTHER,
                FIXED_TIME,
                FIXED_TIME);
        var owner = BusinessMembership.owner(
                new MembershipId(new UuidV7Generator().next()),
                business.id(),
                new UserId(user.id().value()),
                FIXED_TIME,
                FIXED_TIME);
        businesses.createWithOwner(business, owner);

        var fetchedBusiness = businesses.findById(business.id()).orElseThrow();
        var fetchedMembership = memberships.findByUserAndBusiness(
                new UserId(user.id().value()), business.id()).orElseThrow();
        assertThat(fetchedBusiness.createdAt()).isEqualTo(FIXED_TIME);
        assertThat(fetchedBusiness.updatedAt()).isEqualTo(FIXED_TIME);
        assertThat(fetchedMembership.createdAt()).isEqualTo(FIXED_TIME);
        assertThat(fetchedMembership.updatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void persistenceRuntimeAndMigrationRolesRemainSeparateAndLeastPrivileged() throws Exception {
        assertThat(currentUser(M2PostgresTestContainer.APP)).isEqualTo(M2PostgresTestContainer.APP);
        assertThat(currentUser(M2PostgresTestContainer.MIGRATOR)).isEqualTo(M2PostgresTestContainer.MIGRATOR);
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "businesses", "SELECT")).isTrue();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "businesses", "INSERT")).isTrue();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "businesses", "UPDATE")).isFalse();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "businesses", "DELETE")).isFalse();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "business_memberships", "SELECT")).isTrue();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "business_memberships", "INSERT")).isTrue();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "business_memberships", "UPDATE")).isFalse();
        assertThat(hasTablePrivilege(M2PostgresTestContainer.APP, "business_memberships", "DELETE")).isFalse();
    }

    private User createUser(String subject) {
        var user = User.active(
                new com.tino.backend.identity.domain.model.UserId(new UuidV7Generator().next()),
                new ExternalSubject(subject),
                FIXED_TIME,
                FIXED_TIME);
        return users.insert(user);
    }

    private static AuthenticatedUser authenticated(User user) {
        return new AuthenticatedUser(new UserId(user.id().value()), true);
    }

    private static BusinessId createBusinessWithMigrator(String subject) {
        var businessId = new BusinessId(new UuidV7Generator().next());
        try (var connection = migratorConnection(POSTGRES);
                var statement = connection.prepareStatement(
                        "INSERT INTO public.businesses "
                                + "(id, trade_name, vertical, status, created_at, updated_at) "
                                + "VALUES (?, ?, 'OTHER', 'ACTIVE', ?, ?)")) {
            statement.setObject(1, businessId.value());
            statement.setString(2, subject);
            statement.setObject(3, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.setObject(4, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.executeUpdate();
            return businessId;
        } catch (SQLException exception) {
            throw new IllegalStateException("could not create test business", exception);
        }
    }

    private static void setStatus(String table, UUID id, String status) throws Exception {
        var sql = "UPDATE public." + table + " SET status = ? WHERE id = ?";
        try (var connection = migratorConnection(POSTGRES); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
    }

    private static void assertCheckRejects(String sql, UUID... references) throws Exception {
        try (var connection = migratorConnection(POSTGRES); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            for (var index = 0; index < references.length; index++) {
                statement.setObject(index + 2, references[index]);
            }
            statement.setObject(references.length + 2, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.setObject(references.length + 3, FIXED_TIME.atOffset(ZoneOffset.UTC));
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
        }
    }

    private static int count(String table) throws Exception {
        try (var connection = migratorConnection(POSTGRES);
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static List<String> columns(String table) throws Exception {
        try (var connection = migratorConnection(POSTGRES);
                var statement = connection.prepareStatement(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ? "
                                + "ORDER BY ordinal_position")) {
            statement.setString(1, table);
            try (var result = statement.executeQuery()) {
                var values = new ArrayList<String>();
                while (result.next()) {
                    values.add(result.getString(1));
                }
                return values;
            }
        }
    }

    private static boolean rlsEnabled(String table) throws Exception {
        try (var connection = migratorConnection(POSTGRES);
                var statement = connection.prepareStatement(
                        "SELECT relrowsecurity FROM pg_class WHERE oid = ?::regclass")) {
            statement.setString(1, "public." + table);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static String currentUser(String username) {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), username,
                M2PostgresTestContainer.APP.equals(username)
                        ? POSTGRES.appPassword() : POSTGRES.migratorPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            result.next();
            return result.getString(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("could not inspect database role", exception);
        }
    }

    private static boolean hasTablePrivilege(String username, String table, String privilege) {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), username,
                M2PostgresTestContainer.APP.equals(username)
                        ? POSTGRES.appPassword() : POSTGRES.migratorPassword());
                var statement = connection.prepareStatement(
                        "SELECT has_table_privilege(current_user, ?, ?)")) {
            statement.setString(1, "public." + table);
            statement.setString(2, privilege);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not inspect table privilege", exception);
        }
    }

    private static Flyway migrate(M2PostgresTestContainer postgres) {
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                        postgres.migratorPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(flyway.migrate().success).isTrue();
        return flyway;
    }

    private static Connection migratorConnection(M2PostgresTestContainer postgres) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                postgres.migratorPassword());
    }
}
