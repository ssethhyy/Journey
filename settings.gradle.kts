pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
        }
    }
}

rootProject.name = "Journey"
include(":app")

// Essentials Island SDK (vendored from sameerasw/essentials, see third_party/essentials-island/README.md)
include(":island-sdk")
project(":island-sdk").projectDir = file("third_party/essentials-island/island-sdk")
include(":island-sdk-compose")
project(":island-sdk-compose").projectDir = file("third_party/essentials-island/island-sdk-compose")
 