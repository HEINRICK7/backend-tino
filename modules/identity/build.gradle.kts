plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    api(project(":shared:kernel"))
    implementation(project(":shared:infrastructure"))
    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.jooq)
    testImplementation(libs.spring.boot.starter.test)
}
