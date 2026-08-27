plugins {
    id("tino.java-conventions")
    alias(libs.plugins.jooq.codegen)
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":shared:kernel"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.jdbc)
    implementation(libs.jooq)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation("com.zaxxer:HikariCP")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    jooqCodegen(platform(libs.spring.boot.dependencies))
    jooqCodegen(libs.postgresql)
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = providers.environmentVariable("JOOQ_JDBC_URL")
                .orElse("jdbc:postgresql://localhost:5432/tino").get()
            user = providers.environmentVariable("JOOQ_JDBC_USER").orElse("tino_migrator").get()
            password = providers.environmentVariable("JOOQ_JDBC_PASSWORD").orElse("").get()
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
                excludes = "flyway_schema_history"
            }
            target {
                packageName = "com.tino.backend.generated.jooq"
                directory = layout.buildDirectory.dir("generated-src/jooq/main").get().asFile.path
            }
        }
    }
}
