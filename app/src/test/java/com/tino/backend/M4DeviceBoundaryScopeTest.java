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

/** M4 dependency, privacy, Modulith and scope audits. */
class M4DeviceBoundaryScopeTest {
    @Test
    void testM4_027_deviceSchemaHasNoPii() {
        var migration = read(repositoryRoot().resolve(
                "app/src/main/resources/db/migration/V3__device_installations.sql"));
        assertThat(migration)
                .contains("CREATE TABLE public.device_installations")
                .contains("installation_external_id VARCHAR(200)")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("cpf")
                .doesNotContain("name")
                .doesNotContain("address")
                .doesNotContain("location")
                .doesNotContain("push_token")
                .doesNotContain("app_version");
    }

    @Test
    void testM4_028_noHardwareFingerprintOrSensitiveClientIdentifier() {
        var sources = sourceTexts(List.of(
                repositoryRoot().resolve("modules/device/src/main"),
                repositoryRoot().resolve("app/src/main/resources/db/migration/V3__device_installations.sql")));
        var forbidden = List.of(
                "imei", "mac address", "mac_address", "advertising id", "advertising_id",
                "serial number", "serial_number", "fingerprint", "hardware identifier",
                "telephone", "phone", "cpf", "email", "push token", "fcm");
        sources.forEach(source -> forbidden.forEach(term ->
                assertThat(source.toLowerCase()).as("forbidden device identifier %s", term)
                        .doesNotContain(term)));
    }

    @Test
    void testM4_036_jooqIsConfinedToDevicePersistenceAdapter() {
        var root = repositoryRoot();
        var application = sourceTexts(List.of(
                root.resolve("modules/device/src/main/java/com/tino/backend/device/domain"),
                root.resolve("modules/device/src/main/java/com/tino/backend/device/application")));
        application.forEach(source -> assertThat(source)
                .doesNotContain("org.jooq")
                .doesNotContain("DSLContext")
                .doesNotContain("DataAccessException")
                .doesNotContain("java.sql")
                .doesNotContain("Jdbc")
                .doesNotContain("org.springframework")
                .doesNotContain("Jwt")
                .doesNotContain("Authentication")
                .doesNotContain("Keycloak"));

        var adapters = sourceTexts(List.of(
                root.resolve("modules/device/src/main/java/com/tino/backend/device/adapter/out")));
        assertThat(adapters).isNotEmpty().allSatisfy(source ->
                assertThat(source).contains("DSLContext"));
    }

    @Test
    void testM4_037_deviceUsesBusinessPublicAuthorizationOnly() {
        var sources = sourceTexts(List.of(
                repositoryRoot().resolve("modules/device/src/main/java")));
        sources.forEach(source -> assertThat(source)
                .doesNotContain("BusinessRepository")
                .doesNotContain("BusinessMembershipRepository")
                .doesNotContain("JooqBusinessRepository")
                .doesNotContain("JooqBusinessMembershipRepository")
                .doesNotContain("ResolveBusinessAccess")
                .doesNotContain("com.tino.backend.business.domain")
                .doesNotContain("BusinessController"));
        assertThat(sources).anyMatch(source -> source.contains("BusinessAuthorization"));
    }

    @Test
    void testM4_038_deviceUsesIdentityPublicPortOnly() {
        var sources = sourceTexts(List.of(
                repositoryRoot().resolve("modules/device/src/main/java")));
        sources.forEach(source -> assertThat(source)
                .doesNotContain("JooqUserRepository")
                .doesNotContain("UserRepository")
                .doesNotContain("ResolveAuthenticatedUser")
                .doesNotContain("com.tino.backend.identity.domain"));
        assertThat(sources).anyMatch(source -> source.contains("AuthenticatedPrincipal"));
        assertThat(sources).anyMatch(source -> source.contains("AuthenticatedUserResolver"));
    }

    @Test
    void testM4_039_modulithBoundariesVerify() {
        ApplicationModules.of(TinoBackendApplication.class).verify();
    }

    @Test
    void testM4_041_secretScanGateIsPresentAndExecutable() {
        var scan = repositoryRoot().resolve("scripts/secret-scan.sh");
        assertThat(Files.isRegularFile(scan)).isTrue();
        assertThat(Files.isExecutable(scan)).isTrue();
        assertThat(read(scan)).contains("Secret scan passed.");
    }

    @Test
    void testM4_042_cleanBuildGateUsesRepositoryWrapper() {
        var root = repositoryRoot();
        var wrapper = root.resolve("gradlew");
        var wrapperJar = root.resolve("gradle/wrapper/gradle-wrapper.jar");
        assertThat(Files.isRegularFile(wrapper)).isTrue();
        assertThat(Files.isRegularFile(wrapperJar)).isTrue();
        assertThat(read(wrapper)).contains("gradle-wrapper.jar").contains("-jar");
    }

    @Test
    void testM4_043_noM5OrOutOfScopeFunctionalMarkers() {
        var root = repositoryRoot();
        var production = sourceTexts(List.of(
                root.resolve("modules/device/src/main/java"),
                root.resolve("modules/device/build.gradle.kts"),
                root.resolve("app/src/main/resources/db/migration/V3__device_installations.sql")));
        var forbidden = List.of(
                "bootstrap", "sync", "customer", "credit", "ledger", "payment", "pix",
                "reconciliation", "whatsapp", "notification", "push_token", "telemetry",
                "businessprofile", "capabilities", "outbox", "kafka", "rabbitmq", "redis",
                "hibernate", "jpa", "mongodb", "bypassrls");
        production.forEach(source -> forbidden.forEach(term -> assertThat(source.toLowerCase())
                .as("out-of-scope M4 marker %s", term)
                .doesNotContain(term)));

        var migration = read(root.resolve(
                "app/src/main/resources/db/migration/V3__device_installations.sql"));
        assertThat(migration).contains("CREATE TABLE public.device_installations")
                .doesNotContain("CREATE TABLE public.businesses")
                .doesNotContain("CREATE TABLE public.users")
                .doesNotContain("CREATE TABLE public.business_memberships");
    }

    private static List<String> sourceTexts(List<Path> roots) {
        var texts = new ArrayList<String>();
        for (var root : roots) {
            if (Files.isRegularFile(root)) {
                texts.add(read(root));
                continue;
            }
            try (var files = files(root)) {
                files.map(M4DeviceBoundaryScopeTest::read).forEach(texts::add);
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
                        || path.getFileName().toString().endsWith(".sql")
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
