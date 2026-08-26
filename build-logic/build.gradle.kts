plugins { `kotlin-dsl` }

gradlePlugin {
    plugins {
        register("tinoJavaConventions") {
            id = "tino.java-conventions"
            implementationClass = "TinoJavaConventionsPlugin"
        }
    }
}
