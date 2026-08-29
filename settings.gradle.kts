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
    "modules:bootstrap",
    "modules:sync",
    "modules:customer",
    "modules:credit",
    "modules:payment",
    "modules:reconciliation",
    "modules:messaging",
    "shared:kernel",
    "shared:infrastructure",
)
