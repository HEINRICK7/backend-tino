plugins {
    id("tino.java-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    api(platform(libs.spring.boot.dependencies))
    implementation(project(":shared:kernel"))
    implementation(project(":shared:infrastructure"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:business"))
    implementation(project(":modules:device"))
    implementation(project(":modules:sync"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.springdoc.openapi)
    implementation(libs.resilience4j.spring.boot4)
    implementation(libs.otel.spring.boot) {
        exclude(group = "io.opentelemetry.instrumentation", module = "opentelemetry-kafka-clients-2.6")
        exclude(group = "io.opentelemetry.instrumentation", module = "opentelemetry-kafka-clients-common-0.11")
        exclude(group = "io.opentelemetry.instrumentation", module = "opentelemetry-spring-kafka-2.7")
    }
    implementation(libs.micrometer.otel)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.modulith.test)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit)
}

tasks.test { maxParallelForks = 1 }
