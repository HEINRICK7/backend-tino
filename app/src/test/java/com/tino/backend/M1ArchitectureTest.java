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

class M1ArchitectureTest {
    @Test
    void jooqStaysOutOfKernelDomainAndApplicationContracts() {
        var root = repositoryRoot();
        var contractRoots = List.of(
                root.resolve("shared/kernel/src/main"),
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/domain"),
                root.resolve("modules/identity/src/main/java/com/tino/backend/identity/application"),
                root.resolve("modules/business/src/main/java/com/tino/backend/business/domain"),
                root.resolve("modules/business/src/main/java/com/tino/backend/business/application"),
                root.resolve("modules/device/src/main/java/com/tino/backend/device/domain"),
                root.resolve("modules/device/src/main/java/com/tino/backend/device/application"),
                root.resolve("modules/sync/src/main/java/com/tino/backend/sync/domain"),
                root.resolve("modules/sync/src/main/java/com/tino/backend/sync/application"),
                root.resolve("modules/customer/src/main/java/com/tino/backend/customer/domain"),
                root.resolve("modules/customer/src/main/java/com/tino/backend/customer/application"),
                root.resolve("modules/credit/src/main/java/com/tino/backend/credit/domain"),
                root.resolve("modules/credit/src/main/java/com/tino/backend/credit/application"),
                root.resolve("app/src/main/java"));

        sourceTexts(contractRoots).forEach(source -> assertThat(source)
                .doesNotContain("org.jooq")
                .doesNotContain("DSLContext")
                .doesNotContain("org.jooq.impl"));

        sourceTexts(List.of(root.resolve(
                        "modules/business/src/main/java/com/tino/backend/business/adapter/out/persistence"),
                root.resolve("modules/device/src/main/java/com/tino/backend/device/adapter/out/persistence")))
                .forEach(source -> assertThat(source).contains("org.jooq").contains("DSLContext"));
        sourceTexts(List.of(root.resolve(
                "modules/sync/src/main/java/com/tino/backend/sync/adapter/out/persistence")))
                .forEach(source -> assertThat(source).contains("org.jooq").contains("DSLContext"));
        sourceTexts(List.of(root.resolve(
                "modules/customer/src/main/java/com/tino/backend/customer/adapter/out/persistence")))
                .forEach(source -> assertThat(source).contains("org.jooq").contains("DSLContext"));
        sourceTexts(List.of(root.resolve(
                "modules/credit/src/main/java/com/tino/backend/credit/adapter/out/persistence")))
                .forEach(source -> assertThat(source).contains("org.jooq").contains("DSLContext"));
    }

    @Test
    void prohibitedOrmPersistenceIsAbsentFromSourcesAndRuntimeClasspath() {
        var root = repositoryRoot();
        var sourceRoots = List.of(
                root.resolve("shared"),
                root.resolve("modules"),
                root.resolve("app/src/main"));

        sourceTexts(sourceRoots).forEach(source -> assertThat(source)
                .doesNotContain("hibernate" + "-core")
                .doesNotContain("spring-data" + "-jpa")
                .doesNotContain("jakarta." + "persistence")
                .doesNotContain("org.hibernate." + "orm"));

        var runtimeClasspath = System.getProperty("java.class.path").toLowerCase();
        assertThat(runtimeClasspath)
                .doesNotContain("hibernate" + "-core")
                .doesNotContain("spring-data" + "-jpa")
                .doesNotContain("jakarta." + "persistence");
    }

    private static List<String> sourceTexts(List<Path> roots) {
        var texts = new ArrayList<String>();
        for (var root : roots) {
            try (var files = sourceFiles(root)) {
                files.map(M1ArchitectureTest::read).forEach(texts::add);
            }
        }
        return texts;
    }

    private static Stream<Path> sourceFiles(Path root) {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        try {
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(M1ArchitectureTest::isSourceFile);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean isSourceFile(Path path) {
        var name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".kt");
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
