package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.identity.adapter.out.persistence.JooqUserRepository;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.out.ExternalSubjectAlreadyExistsException;
import com.tino.backend.identity.application.usecase.ResolveAuthenticatedUser;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.identity.domain.model.User;
import com.tino.backend.identity.domain.model.UserId;
import com.tino.backend.identity.domain.model.UserStatus;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class M2IdentityPostgresTest {
    private static final String SUBJECT = "opaque-subject-1";
    private static final Instant FIXED_TIME = Instant.parse("2026-08-26T12:34:56.123456Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @BeforeEach
    void clearUsers() throws Exception {
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute(
                    "TRUNCATE TABLE public.sync_event_rejections, public.sync_outbox, "
                            + "public.sync_changes, public.sync_event_claims, "
                            + "public.device_installations, public.business_memberships, "
                            + "public.businesses, public.users");
        }
    }

    @Test
    void migratesFromZeroAndFlywayValidatePasses() {
        var flyway = migrate();

        assertThat(flyway.info().applied()).hasSize(5);
        flyway.validate();
        assertThat(tableColumns()).containsExactly(
                "id", "external_subject", "status", "created_at", "updated_at");
        assertThat(timestampTypes()).containsExactly(
                "timestamp with time zone", "timestamp with time zone");
    }

    @Test
    void sameSubjectIsIdempotentAndNewUsersDefaultToActive() throws Exception {
        var useCase = useCase();

        var first = useCase.execute(principal(SUBJECT));
        var second = useCase.execute(principal(SUBJECT));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(countFor(SUBJECT)).isEqualTo(1);
    }

    @Test
    void differentSubjectsProduceDifferentUsers() throws Exception {
        var useCase = useCase();

        var first = useCase.execute(principal("subject-a"));
        var second = useCase.execute(principal("subject-b"));

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(countUsers()).isEqualTo(2);
    }

    @Test
    void physicalUniqueConstraintTranslatesDuplicateInsert() throws Exception {
        var repository = repository();
        var first = User.active(
                new UserId(new UuidV7Generator().next()),
                new ExternalSubject(SUBJECT),
                FIXED_TIME,
                FIXED_TIME);
        var duplicate = User.active(
                new UserId(new UuidV7Generator().next()),
                new ExternalSubject(SUBJECT),
                FIXED_TIME,
                FIXED_TIME);

        repository.insert(first);
        assertThatThrownBy(() -> repository.insert(duplicate))
                .isInstanceOf(ExternalSubjectAlreadyExistsException.class);
        assertThat(countFor(SUBJECT)).isEqualTo(1);
    }

    @Test
    void physicalStatusCheckRejectsUnknownState() throws Exception {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO public.users "
                                + "(id, external_subject, status, created_at, updated_at) "
                                + "VALUES (?, ?, 'UNKNOWN', ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, "invalid-status-subject");
            statement.setObject(3, FIXED_TIME.atOffset(ZoneOffset.UTC));
            statement.setObject(4, FIXED_TIME.atOffset(ZoneOffset.UTC));
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void twentyConcurrentFirstAccessesResolveOneUuidV7User() throws Exception {
        var workers = 20;
        var barrier = new CyclicBarrier(workers);
        var executor = Executors.newFixedThreadPool(workers);
        try {
            var tasks = IntStream.range(0, workers)
                    .mapToObj(ignored -> executor.submit(() -> {
                        barrier.await(30, TimeUnit.SECONDS);
                        return useCase().execute(principal("concurrent-subject"));
                    }))
                    .toList();
            var resolved = new ArrayList<User>();
            for (var task : tasks) {
                resolved.add(task.get(30, TimeUnit.SECONDS));
            }

            assertThat(resolved).hasSize(workers);
            assertThat(resolved.stream().map(User::id).distinct().toList()).hasSize(1);
            assertThat(resolved).extracting(User::id).containsOnly(resolved.getFirst().id());
            assertThat(resolved.getFirst().id().value().version()).isEqualTo(7);
            assertThat(countFor("concurrent-subject")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void disabledUserIsRejected() throws Exception {
        var user = useCase().execute(principal("disabled-subject"));
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "UPDATE public.users SET status = 'DISABLED' WHERE id = ?")) {
            statement.setObject(1, user.id().value());
            statement.executeUpdate();
        }

        assertThatThrownBy(() -> useCase().execute(principal("disabled-subject")))
                .isInstanceOf(DisabledUserException.class);
    }

    @Test
    void timestampsRoundTripAsInstantAndUuidIsVersionSeven() throws Exception {
        var inserted = new User(
                new UserId(new UuidV7Generator().next()),
                new ExternalSubject("timestamp-subject"),
                UserStatus.ACTIVE,
                FIXED_TIME,
                FIXED_TIME);
        var repository = repository();
        repository.insert(inserted);
        var fetched = repository.findByExternalSubject(inserted.externalSubject()).orElseThrow();

        assertThat(inserted.id().value().version()).isEqualTo(7);
        assertThat(fetched.createdAt()).isEqualTo(FIXED_TIME);
        assertThat(fetched.updatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void runtimeUsesTinoAppAndMigrationUsesTinoMigrator() throws Exception {
        assertThat(currentUser()).isEqualTo(M2PostgresTestContainer.APP);
        assertThat(migratorCurrentUser()).isEqualTo(M2PostgresTestContainer.MIGRATOR);
        assertThat(hasTablePrivilege("SELECT")).isTrue();
        assertThat(hasTablePrivilege("INSERT")).isTrue();
        assertThat(hasTablePrivilege("UPDATE")).isFalse();
        assertThat(hasTablePrivilege("DELETE")).isFalse();
    }

    @Test
    void usersIsGlobalIdentityAndHasNoPersonalOrTenantColumns() throws Exception {
        assertThat(tableColumns()).doesNotContain(
                "business_id", "email", "name", "phone", "username", "password",
                "password_hash", "access_token", "refresh_token");
        assertThat(tableRlsEnabled()).isFalse();
    }

    private static Flyway migrate() {
        var flyway = Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        M2PostgresTestContainer.MIGRATOR,
                        POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load();
        assertThat(flyway.migrate().success).isTrue();
        return flyway;
    }

    private static ResolveAuthenticatedUser useCase() {
        return new ResolveAuthenticatedUser(
                repository(), new UuidV7Generator(), Clock.fixed(FIXED_TIME, ZoneOffset.UTC));
    }

    private static JooqUserRepository repository() {
        migrate();
        return new JooqUserRepository(DSL.using(appDataSource(), SQLDialect.POSTGRES));
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(M2PostgresTestContainer.APP);
        dataSource.setPassword(POSTGRES.appPassword());
        return dataSource;
    }

    private static AuthenticatedPrincipal principal(String subject) {
        return new AuthenticatedPrincipal(new ExternalSubject(subject));
    }

    private static java.sql.Connection migratorConnection() throws Exception {
        migrate();
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
    }

    private static String currentUser() throws Exception {
        try (var connection = appConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            result.next();
            return result.getString(1);
        }
    }

    private static String migratorCurrentUser() throws Exception {
        try (var connection = migratorConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT current_user")) {
            result.next();
            return result.getString(1);
        }
    }

    private static boolean hasTablePrivilege(String privilege) throws Exception {
        try (var connection = appConnection();
                var statement = connection.prepareStatement(
                        "SELECT has_table_privilege(current_user, 'public.users', ? )")) {
            statement.setString(1, privilege);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static List<String> tableColumns() {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "SELECT column_name FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'users' "
                                + "ORDER BY ordinal_position");
                var result = statement.executeQuery()) {
            var columns = new ArrayList<String>();
            while (result.next()) {
                columns.add(result.getString(1));
            }
            return columns;
        } catch (Exception exception) {
            throw new IllegalStateException("could not inspect users columns", exception);
        }
    }

    private static List<String> timestampTypes() {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "SELECT data_type FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = 'users' "
                                + "AND column_name IN ('created_at', 'updated_at') "
                                + "ORDER BY column_name");
                var result = statement.executeQuery()) {
            var types = new ArrayList<String>();
            while (result.next()) {
                types.add(result.getString(1));
            }
            return types;
        } catch (Exception exception) {
            throw new IllegalStateException("could not inspect users timestamp types", exception);
        }
    }

    private static boolean tableRlsEnabled() throws Exception {
        try (var connection = migratorConnection();
                var statement = connection.prepareStatement(
                        "SELECT relrowsecurity FROM pg_class "
                                + "WHERE oid = 'public.users'::regclass");
                var result = statement.executeQuery()) {
            result.next();
            return result.getBoolean(1);
        }
    }

    private static int countUsers() throws Exception {
        try (var connection = appConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public.users")) {
            result.next();
            return result.getInt(1);
        }
    }

    private static int countFor(String subject) throws Exception {
        try (var connection = appConnection();
                var statement = connection.prepareStatement(
                        "SELECT count(*) FROM public.users WHERE external_subject = ?")) {
            statement.setString(1, subject);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static java.sql.Connection appConnection() throws Exception {
        migrate();
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }
}
