package com.tino.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.github.dockerjava.api.command.InspectContainerResponse;

/**
 * PostgreSQL 17 fixture with roles created using process-local credentials.
 * Nothing from this fixture is persisted in source, logs, or evidence.
 */
final class M2PostgresTestContainer extends PostgreSQLContainer {
    static final String MIGRATOR = "tino_migrator";
    static final String APP = "tino_app";

    private final String migratorPassword = runtimePassword();
    private final String appPassword = runtimePassword();

    M2PostgresTestContainer() {
        super("postgres:17-alpine");
        withDatabaseName("tino");
        withUsername("tino_test_admin");
        withPassword(runtimePassword());
    }

    String migratorPassword() {
        return migratorPassword;
    }

    String appPassword() {
        return appPassword;
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        configureRoles();
    }

    private void configureRoles() {
        try (var connection = adminConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE tino_migrator LOGIN NOSUPERUSER NOCREATEDB "
                    + "NOCREATEROLE NOINHERIT NOBYPASSRLS");
            statement.execute("CREATE ROLE tino_app LOGIN NOSUPERUSER NOCREATEDB "
                    + "NOCREATEROLE NOINHERIT NOBYPASSRLS");
            statement.execute("ALTER ROLE tino_migrator PASSWORD '" + sqlLiteral(migratorPassword) + "'");
            statement.execute("ALTER ROLE tino_app PASSWORD '" + sqlLiteral(appPassword) + "'");
            statement.execute("GRANT CONNECT ON DATABASE tino TO tino_migrator, tino_app");
            statement.execute("GRANT CREATE, USAGE ON SCHEMA public TO tino_migrator");
            statement.execute("GRANT USAGE ON SCHEMA public TO tino_app");
        } catch (SQLException exception) {
            throw new IllegalStateException("could not configure disposable database roles", exception);
        }
    }

    private Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), getUsername(), getPassword());
    }

    private static String runtimePassword() {
        return UUID.randomUUID().toString();
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
