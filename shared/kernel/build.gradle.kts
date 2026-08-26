plugins { id("tino.java-conventions") }

dependencies {
    api(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.test)
}
