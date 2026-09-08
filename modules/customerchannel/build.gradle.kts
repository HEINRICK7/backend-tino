plugins { id("tino.java-conventions") }

dependencies {
    compileOnly(libs.spring.modulith.starter.core)
    api(platform(libs.spring.boot.dependencies))
    implementation(project(":shared:kernel"))
    implementation(project(":shared:infrastructure"))
    implementation(project(":modules:business"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:customer"))
    implementation(project(":modules:messaging"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jooq)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
