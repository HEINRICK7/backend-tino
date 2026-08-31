plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    implementation(project(":shared:kernel"))
    implementation(project(":modules:business"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:catalog"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jooq)
    compileOnly(libs.spring.modulith.starter.core)
    compileOnly(libs.springdoc.openapi)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
