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
    "modules:businessunderstanding",
    "modules:device",
    "modules:bootstrap",
    "modules:sync",
    "modules:customer",
    "modules:credit",
    "modules:payment",
    "modules:reconciliation",
    "modules:messaging",
    "modules:fiscal",
    "modules:catalog",
    "modules:receiving",
    "modules:inventory",
    "modules:external",
    "keycloak-extension",
    "shared:kernel",
    "shared:infrastructure",
)
