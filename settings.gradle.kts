@file:Suppress("UnstableApiUsage")

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
    // Lets Gradle auto-provision JDK toolchains (e.g. JDK 21 for compilation)
    // independently of the JDK the Gradle daemon runs on.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "kotlin-mp-todo"

include(":common")
include(":client-core")
include(":ui-compose")
include(":server")
include(":apps:android")
include(":apps:desktop")
include(":apps:web")
// Note: the iOS app is an Xcode project consuming a KMP framework, not a Gradle
// module of its own; the shared framework is produced by :ui-compose / :client-core.
