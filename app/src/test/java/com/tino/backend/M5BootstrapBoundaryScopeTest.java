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
import org.springframework.modulith.core.ApplicationModules;

/** M5 public-contract, dependency, privacy, and scope audits. */
class M5BootstrapBoundaryScopeTest {
    @Test
    void testM5_038_bootstrapUsesIdentityPublicContractOnly() {
        var root = repositoryRoot();
        var sources = sourceTexts(root.resolve("modules/bootstrap/src/main"));

        sources.forEach(source -> assertThat(source)
                .doesNotContain("JooqUserRepository")
                .doesNotContain("ResolveAuthenticatedUser")
                .doesNotContain("identity.application.port.out")
                .doesNotContain("identity.domain")
                .doesNotContain("org.springframework.security.oauth2.jwt")
                .doesNotContain("Keycloak"));
        assertThat(sources).anyMatch(source -> source.contains("AuthenticatedPrincipal"));
    }

    @Test
    void testM5_039_bootstrapUsesBusinessPublicContractOnly() {
        var root = repositoryRoot();
        var sources = sourceTexts(root.resolve("modules/bootstrap/src/main"));

        sources.forEach(source -> assertThat(source)
                .doesNotContain("JooqBusinessRepository")
                .doesNotContain("JooqBusinessMembershipRepository")
                .doesNotContain("BusinessRepository")
                .doesNotContain("BusinessMembershipRepository")
                .doesNotContain("business.domain")
                .doesNotContain("business.adapter.out"));
        assertThat(sources).anyMatch(source -> source.contains("BusinessContextReader"));
    }

    @Test
    void testM5_040_bootstrapUsesDevicePublicContractOnly() {
        var root = repositoryRoot();
        var sources = sourceTexts(root.resolve("modules/bootstrap/src/main"));

        sources.forEach(source -> assertThat(source)
                .doesNotContain("JooqDeviceInstallationRepository")
                .doesNotContain("DeviceInstallationRepository")
                .doesNotContain("device.domain")
                .doesNotContain("device.adapter.out"));
        assertThat(sources).anyMatch(source -> source.contains("DeviceInstallationContextReader"));
    }

    @Test
    void testM5_041_jooqAndJdbcAreAbsentFromBootstrap() {
        sourceTexts(repositoryRoot().resolve("modules/bootstrap/src/main"))
                .forEach(source -> assertThat(source)
                        .doesNotContain("org.jooq")
                        .doesNotContain("DSLContext")
                        .doesNotContain("DataAccessException")
                        .doesNotContain("java.sql")
                        .doesNotContain("Jdbc"));
    }

    @Test
    void testM5_042_modulithBoundariesVerify() {
        ApplicationModules.of(TinoBackendApplication.class).verify();
    }

    @Test
    void testM5_053_secretScanGateIsPresentAndExecutable() {
        var scan = repositoryRoot().resolve("scripts/secret-scan.sh");
        assertThat(Files.isRegularFile(scan)).isTrue();
        assertThat(Files.isExecutable(scan)).isTrue();
        assertThat(read(scan)).contains("Secret scan passed.");
    }

    @Test
    void testM5_054_cleanBuildGateUsesRepositoryWrapper() {
        var root = repositoryRoot();
        assertThat(Files.isRegularFile(root.resolve("gradlew"))).isTrue();
        assertThat(Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar"))).isTrue();
        assertThat(read(root.resolve("gradlew"))).contains("gradle-wrapper.jar").contains("-jar");
    }

    @Test
    void testM5_055_noM6OrOutOfScopeFunctionalMarkers() {
        var root = repositoryRoot();
        var production = sourceTexts(List.of(
                root.resolve("modules/bootstrap/src/main"),
                root.resolve("modules/bootstrap/build.gradle.kts")));
        var forbidden = List.of(
                "sync", "customer", "credit", "ledger", "payment", "pix",
                "reconciliation", "whatsapp", "notification", "push_token", "telemetry",
                "redis", "kafka", "rabbitmq", "mongodb", "hibernate", "jpa",
                "device registration", "device revocation", "outbox");

        production.forEach(source -> forbidden.forEach(term -> assertThat(source.toLowerCase())
                .as("out-of-scope M5 marker %s", term)
                .doesNotContain(term)));
        assertThat(Files.exists(root.resolve("modules/bootstrap/src/main/resources/db/migration")))
                .isFalse();
        assertThat(Files.exists(root.resolve("app/src/main/resources/db/migration/V4__bootstrap")))
                .isFalse();
    }

    private static List<String> sourceTexts(Path root) {
        return sourceTexts(List.of(root));
    }

    private static List<String> sourceTexts(List<Path> roots) {
        var texts = new ArrayList<String>();
        for (var root : roots) {
            if (Files.isRegularFile(root)) {
                texts.add(read(root));
                continue;
            }
            try (var files = files(root)) {
                files.map(M5BootstrapBoundaryScopeTest::read).forEach(texts::add);
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
                .filter(path -> path.getFileName().toString().endsWith(".java")
                        || path.getFileName().toString().endsWith(".kts"));
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
