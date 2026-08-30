plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":shared:kernel"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    compileOnly(libs.spring.modulith.starter.core)
    implementation(libs.jooq)
    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
