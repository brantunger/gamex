rootProject.name = "gamex"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // Lets Gradle download the Java 21 toolchain on machines that don't have it.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
