package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Explicit M3 structural gates.  These checks intentionally inspect only the
 * M3 production boundary; M2's independent scope gate remains authoritative
 * for the identity foundation.
 */
class M3BusinessBoundaryScopeTest {
    @Test
    void testM3_023_jooqIsConfinedToBusinessPersistenceAdapters() {
        var root = repositoryRoot();
        var contracts = sourceTexts(List.of(
                root.resolve("modules/business/src/main/java/com/tino/backend/business/domain"),
                root.resolve("modules/business/src/main/java/com/tino/backend/business/application")));

        // Package-level NamedInterface metadata is the explicit Modulith boundary
        // declaration; it is not application logic or a framework dependency in
        // the domain/use-case classes themselves.
        contracts.stream()
                .filter(source -> !source.contains("@org.springframework.modulith.NamedInterface"))
                .forEach(source -> assertThat(source)
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
                root.resolve("modules/business/src/main/java/com/tino/backend/business/adapter/out")));
        assertThat(adapters).isNotEmpty().allSatisfy(source ->
                assertThat(source).contains("DSLContext"));
    }

    @Test
    void testM3_024_businessModuleDoesNotReachIdentityInternals() {
        var root = repositoryRoot();
        sourceTexts(List.of(root.resolve("modules/business/src/main/java")))
                .forEach(source -> assertThat(source)
                        .doesNotContain("com.tino.backend.identity.domain")
                        .doesNotContain("com.tino.backend.identity.application.port.out")
                        .doesNotContain("JooqUserRepository")
                        .doesNotContain("ResolveAuthenticatedUser")
                        .doesNotContain("Jwt")
                        .doesNotContain("Keycloak"));
    }

    @Test
    void testM3_025_modulithBoundariesVerify() {
        ApplicationModules.of(TinoBackendApplication.class).verify();
    }

    @Test
    void testM3_029_noDeviceImplementationInBusinessSlice() {
        var root = repositoryRoot();
        var business = sourceTexts(List.of(
                root.resolve("modules/business/src/main/java"),
                root.resolve("app/src/main/resources/db/migration/V2__business_memberships.sql")),
                path -> !path.getFileName().toString().equals("BusinessContextReader.java"));

        business.forEach(source -> assertThat(source.toLowerCase())
                .doesNotContain("device")
                .doesNotContain("bootstrap")
                .doesNotContain("customer")
                .doesNotContain("credit")
                .doesNotContain("ledger")
                .doesNotContain("payment")
                .doesNotContain("pix")
                .doesNotContain("reconciliation")
                .doesNotContain("whatsapp"));
    }

    @Test
    void testM3_030_businessSchemaContainsNoUnnecessaryPersonalClaims() {
        var migration = read(repositoryRoot().resolve(
                "app/src/main/resources/db/migration/V2__business_memberships.sql"));
        assertThat(migration).contains("CREATE TABLE public.businesses")
                .contains("CREATE TABLE public.business_memberships")
                .doesNotContain("email")
                .doesNotContain("phone")
                .doesNotContain("password")
                .doesNotContain("token")
                .doesNotContain("external_subject")
                .doesNotContain("owner_user_id")
                .doesNotContain("store_id")
                .doesNotContain("device_id");
    }

    /** The executable clean-build gate is run by the verification pipeline; this keeps its entrypoint auditable. */
    @Test
    void testM3_035_cleanBuildGateUsesRepositoryGradleWrapper() {
        var root = repositoryRoot();
        var wrapper = root.resolve("gradlew");
        var wrapperJar = root.resolve("gradle/wrapper/gradle-wrapper.jar");
        assertThat(Files.isRegularFile(wrapper)).isTrue();
        assertThat(Files.isRegularFile(wrapperJar)).isTrue();
        assertThat(read(wrapper)).contains("gradle-wrapper.jar").contains("-jar");
    }

    /** The executable secret-scan gate is run before commits and pushes; this checks that it is present and executable. */
    @Test
    void testM3_036_secretScanGateIsPresent() {
        var scan = repositoryRoot().resolve("scripts/secret-scan.sh");
        assertThat(Files.isRegularFile(scan)).isTrue();
        assertThat(Files.isExecutable(scan)).isTrue();
        assertThat(read(scan)).contains("Secret scan passed.");
    }

    @Test
    void testM3_037_noOutOfScopeFunctionalMarkersInBusinessProduction() {
        var root = repositoryRoot();
        var business = sourceTexts(List.of(
                root.resolve("modules/business/src/main/java"),
                root.resolve("app/src/main/java/com/tino/backend/foundation/BusinessApiExceptionHandler.java"),
                root.resolve("app/src/main/java/com/tino/backend/foundation/BusinessAuthenticatedUserResolver.java"),
                root.resolve("app/src/main/resources/db/migration/V2__business_memberships.sql")),
                path -> !path.getFileName().toString().equals("BusinessContextReader.java"));
        var forbidden = List.of(
                "device", "bootstrap", "customer", "credit", "ledger", "payment", "pix",
                "reconciliation", "sync", "whatsapp", "businessprofile");

        business.forEach(source -> forbidden.forEach(term -> assertThat(source.toLowerCase())
                .as("out-of-scope M3 marker %s", term)
                .doesNotContain(term)));

        var controller = read(root.resolve(
                "modules/business/src/main/java/com/tino/backend/business/adapter/in/web/BusinessController.java"));
        assertThat(controller)
                .doesNotContain("@PutMapping")
                .doesNotContain("@DeleteMapping")
                .doesNotContain("@PatchMapping")
                .contains("@PostMapping")
                .contains("@GetMapping");
    }

    private static List<String> sourceTexts(List<Path> roots) {
        return sourceTexts(roots, path -> true);
    }

    private static List<String> sourceTexts(List<Path> roots, Predicate<Path> include) {
        var texts = new ArrayList<String>();
        for (var root : roots) {
            if (Files.isRegularFile(root) && include.test(root)) {
                texts.add(read(root));
                continue;
            }
            try (var files = files(root)) {
                files.filter(include).map(M3BusinessBoundaryScopeTest::read).forEach(texts::add);
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
                        || path.getFileName().toString().endsWith(".sql"));
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
