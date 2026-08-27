package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class M2ArchitecturePrivacyScopeTest {
    @Test
    void identityDomainAndApplicationDoNotImportFrameworkPersistenceOrProviderTypes() {
        var root = repositoryRoot();
        var innerRoots = List.of(
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/domain"),
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/application"));

        sourceTexts(innerRoots).forEach(source -> assertThat(source)
                .doesNotContain("org.springframework")
                .doesNotContain("org.jooq")
                .doesNotContain("org.keycloak")
                .doesNotContain("Keycloak")
                .doesNotContain("Jwt")
                .doesNotContain("Authentication")
                .doesNotContain("Jdbc")
                .doesNotContain("DataSource"));
    }

    @Test
    void jooqIsConfinedToTheIdentityPersistenceAdapter() {
        var root = repositoryRoot();
        var innerRoots = List.of(
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/domain"),
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/application"));
        sourceTexts(innerRoots).forEach(source -> assertThat(source).doesNotContain("jOOQ"));

        var adapter = root.resolve(
                "modules/identity/src/main/java/com/tino/backend/identity/adapter/out/persistence/JooqUserRepository.java");
        assertThat(read(adapter)).contains("DSLContext").contains("DataAccessException");
    }

    @Test
    void usersMigrationIsMinimalAndPublishedV0RemainsTechnicalOnly() {
        var root = repositoryRoot();
        var migration = read(root.resolve("app/src/main/resources/db/migration/V1__identity_users.sql"));
        assertThat(migration).contains("CREATE TABLE public.users")
                .contains("external_subject VARCHAR")
                .contains("UNIQUE (external_subject)")
                .contains("CHECK (status IN ('ACTIVE', 'DISABLED'))")
                .contains("created_at TIMESTAMPTZ")
                .contains("updated_at TIMESTAMPTZ")
                .contains("GRANT SELECT, INSERT ON TABLE public.users TO tino_app")
                .doesNotContain("business_id")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("password")
                .doesNotContain("token");
        assertThat(read(root.resolve("app/src/main/resources/db/migration/V0__foundation.sql")))
                .isEqualTo("-- M0 verifies Flyway connectivity and lifecycle without creating domain tables.\nSELECT 1;\n");
        assertThat(sourceTexts(List.of(root.resolve("app/src/main/resources/db/migration"))))
                .allSatisfy(source -> assertThat(source.toLowerCase())
                        .doesNotContain("create table business")
                        .doesNotContain("create table membership")
                        .doesNotContain("create table device")
                        .doesNotContain("create table customer")
                        .doesNotContain("create table payment")
                        .doesNotContain("create table ledger"));
    }

    @Test
    void productionIdentityDoesNotPersistPersonalClaimsCredentialsOrTokens() {
        var root = repositoryRoot();
        var production = sourceTexts(List.of(
                root.resolve("modules/identity/src/main"),
                root.resolve("app/src/main/resources/db/migration")));
        production.forEach(source -> assertThat(source.toLowerCase())
                .doesNotContain("password_hash")
                .doesNotContain("refresh_token")
                .doesNotContain("access_token")
                .doesNotContain("email_verified")
                .doesNotContain("preferred_username")
                .doesNotContain("phone_number"));
    }

    @Test
    void scopeHasNoM3OrLaterFunctionalImplementation() {
        var root = repositoryRoot();
        var production = sourceTexts(List.of(
                root.resolve("modules/identity/src/main"),
                root.resolve("app/src/main/java"),
                root.resolve("app/src/main/resources/db/migration")));
        var forbidden = List.of(
                "businessmembership", "businessrole", "device registration", "bootstrap",
                "customer", "credit", "ledger", "payment", "pix", "reconciliation", "sync event",
                "whatsapp");
        production.forEach(source -> forbidden.forEach(term -> assertThat(source.toLowerCase())
                .as("forbidden M2 scope term %s", term)
                .doesNotContain(term)));
    }

    private static List<String> sourceTexts(List<Path> roots) {
        var texts = new ArrayList<String>();
        for (var root : roots) {
            try (var files = files(root)) {
                files.map(M2ArchitecturePrivacyScopeTest::read).forEach(texts::add);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return texts;
    }

    private static Stream<Path> files(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    var name = path.getFileName().toString();
                    return name.endsWith(".java") || name.endsWith(".sql");
                });
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path repositoryRoot() {
        var candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("Could not locate repository root");
        }
        return candidate;
    }
}
