package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.identity.adapter.out.crypto.HmacOtpSecretHasher;
import com.tino.backend.identity.adapter.out.persistence.JooqOtpChallengeRepository;
import com.tino.backend.identity.domain.model.OtpChallenge;
import com.tino.backend.identity.domain.model.PhoneNumber;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OtpChallengePostgresTest {
    private static final String PHONE = "+5586995922924";
    private static final Instant NOW = Instant.parse("2026-08-31T15:00:00Z");

    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    @BeforeEach
    void cleanChallenges() throws Exception {
        migrate().migrate();
        try (var connection = migratorConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.business_item_purposes, public.business_operating_modes, public.business_activities, public.otp_verification_events, public.otp_challenges");
        }
    }

    @Test
    void appRepositoryRoundTripsChallengeAndTicketFields() {
        var hasher = new HmacOtpSecretHasher("postgres-test-secret");
        var id = new UuidV7Generator().next();
        var phone = PhoneNumber.normalize(PHONE);
        var challenge = OtpChallenge.pending(
                id,
                phone,
                hasher.hashPhone(PHONE),
                hasher.hashOrigin("127.0.0.1"),
                hasher.hashCode(id.toString(), PHONE, "482731"),
                NOW.plus(5, ChronoUnit.MINUTES),
                NOW.plus(30, ChronoUnit.SECONDS),
                NOW,
                5,
                3)
                .verified(hasher.hashTicket("ticket-1"), NOW, NOW.plus(1, ChronoUnit.MINUTES));
        var repository = repository();

        repository.insert(challenge);
        var fetched = repository.findByIdForUpdate(id).orElseThrow();

        assertThat(fetched).isEqualTo(challenge);
        assertThat(repository.findByTicketHashForUpdate(hasher.hashTicket("ticket-1")))
                .contains(challenge);
    }

    @Test
    void appRoleCanWriteOnlyTheOtpTableAsGrantedByTheMigration() throws Exception {
        migrate().migrate();
        try (var connection = appConnection(); var statement = connection.prepareStatement(
                "SELECT has_table_privilege(current_user, 'public.otp_challenges', ?::text)")) {
            statement.setString(1, "SELECT");
            try (var result = statement.executeQuery()) {
                result.next();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
    }

    private static JooqOtpChallengeRepository repository() {
        return new JooqOtpChallengeRepository(DSL.using(appDataSource(), SQLDialect.POSTGRES));
    }

    private static Flyway migrate() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private static DriverManagerDataSource appDataSource() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(M2PostgresTestContainer.APP);
        dataSource.setPassword(POSTGRES.appPassword());
        return dataSource;
    }

    private static java.sql.Connection appConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.APP, POSTGRES.appPassword());
    }

    private static java.sql.Connection migratorConnection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR, POSTGRES.migratorPassword());
    }
}
