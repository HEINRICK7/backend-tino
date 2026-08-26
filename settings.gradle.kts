pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
}

rootProject.name = "tino-backend"

includeBuild("build-logic")

include(
    "app",
    "modules:identity",
    "modules:business",
    "modules:device",
    "modules:sync",
    "shared:kernel",
    "shared:infrastructure",
)
