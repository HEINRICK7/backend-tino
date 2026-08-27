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
                root.resolve("modules"),
                root.resolve("app/src/main/java"));

        sourceTexts(contractRoots).forEach(source -> assertThat(source)
                .doesNotContain("org.jooq")
                .doesNotContain("DSLContext")
                .doesNotContain("org.jooq.impl"));
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
