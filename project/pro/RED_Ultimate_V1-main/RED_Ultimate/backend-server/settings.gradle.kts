pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

rootProject.name = "red-backend"

include(":shared-proto")
project(":shared-proto").projectDir = file("../shared-proto")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
