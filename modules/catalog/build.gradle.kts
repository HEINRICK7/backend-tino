plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":shared:kernel"))
    api(project(":modules:fiscal"))
    implementation(project(":modules:business"))
    implementation(project(":modules:identity"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    compileOnly(libs.spring.modulith.starter.core)
    implementation(libs.jooq)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    compileOnly(libs.springdoc.openapi)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
