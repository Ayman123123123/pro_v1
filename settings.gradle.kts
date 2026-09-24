pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NOVA Connect"

include(":app")

include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:security")
include(":core:analytics")

include(":domain")
include(":data")

include(":feature:onboarding")
include(":feature:authentication")
include(":feature:chat")
include(":feature:calls")
include(":feature:communities")
include(":feature:profile")
include(":feature:settings")
