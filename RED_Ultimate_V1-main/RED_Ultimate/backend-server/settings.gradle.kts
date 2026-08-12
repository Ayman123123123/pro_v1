pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        google()
    }
}

rootProject.name = "red-backend"

include(":shared-proto")
project(":shared-proto").projectDir = file("../shared-proto")

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        google()
    }
}
