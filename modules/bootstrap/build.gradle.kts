plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    implementation(project(":modules:identity"))
    implementation(project(":modules:business"))
    implementation(project(":modules:device"))
    implementation(project(":modules:businessunderstanding"))
    implementation(libs.spring.context)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
