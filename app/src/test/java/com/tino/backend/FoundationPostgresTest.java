package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

class FoundationPostgresTest {
    @Test
    void flywayMigratesFromEmptyAndJooqWorksAgainstDisposablePostgres() {
        try (var postgres = new M2PostgresTestContainer()) {
            postgres.start();

            var flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR, postgres.migratorPassword())
                    .locations("classpath:db/migration")
                    .load();
            var result = flyway.migrate();

            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(5);
            assertThatCode(flyway::validate).doesNotThrowAnyException();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), M2PostgresTestContainer.APP, postgres.appPassword())) {
                var value = DSL.using(connection, SQLDialect.POSTGRES).fetchValue("select 1", Integer.class);
                assertThat(value).isEqualTo(1);
            } catch (SQLException exception) {
                throw new IllegalStateException("jOOQ foundation query failed", exception);
            }
        }
    }
}
