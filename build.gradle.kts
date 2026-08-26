plugins {
    base
}

group = "com.tino.backend"
version = "0.1.0-SNAPSHOT"

tasks.register("architecture") {
    group = "verification"
    description = "Runs architecture and Spring Modulith verification tests."
    dependsOn(":app:test")
}

tasks.register("migrations") {
    group = "verification"
    description = "Runs the empty-database Flyway migration integration test."
    dependsOn(":app:test")
}
