package com.tino.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class M1ConfigurationTest {
    @Test
    void keepsRuntimeAndFlywayDatabaseIdentitiesSeparateByDefault() throws IOException {
        try (var resource = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("application.yml"))) {
            var yaml = new String(resource.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(yaml).contains("username: ${SPRING_DATASOURCE_USERNAME:tino_app}");
            assertThat(yaml).contains("password: ${SPRING_DATASOURCE_PASSWORD:tino_app}");
            assertThat(yaml).contains("user: ${SPRING_FLYWAY_USER:tino_migrator}");
            assertThat(yaml).contains("password: ${SPRING_FLYWAY_PASSWORD:tino_migrator}");
            assertThat(yaml).doesNotContain("${SPRING_FLYWAY_USER:${spring.datasource.username}}");
            assertThat(yaml).doesNotContain("${SPRING_FLYWAY_PASSWORD:${spring.datasource.password}}");
        }
    }
}
