package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class FoundationPostgresTest {
    @Test
    void flywayMigratesFromEmptyAndJooqWorksAgainstDisposablePostgres() {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();

            var flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load();
            var result = flyway.migrate();

            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(1);
            assertThatCode(flyway::validate).doesNotThrowAnyException();

            try (var connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                var value = DSL.using(connection, SQLDialect.POSTGRES).fetchValue("select 1", Integer.class);
                assertThat(value).isEqualTo(1);
            } catch (SQLException exception) {
                throw new IllegalStateException("jOOQ foundation query failed", exception);
            }
        }
    }
}
