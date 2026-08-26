import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

class TinoJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) = with(project) {
        pluginManager.apply("java-library")

        extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            withSourcesJar()
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Werror"))
        }

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging { events("failed", "skipped") }
        }
    }
}
