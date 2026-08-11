pluginManagement {
    repositories {
        google()
        // Google Maven mirror — reliable DNS, often works when repo.maven.apache.org is blocked
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// ── Fix: Android prefs path conflict (ANDROID_PREFS_ROOT vs ANDROID_USER_HOME) ──
// Gradle fails when both point to different locations (Docker vs local).
// We unify to ANDROID_USER_HOME if both exist, and ensure a clean state.
run {
    val prefsRoot = System.getenv("ANDROID_PREFS_ROOT")
    val userHome = System.getenv("ANDROID_USER_HOME")
    if (prefsRoot != null && userHome != null && prefsRoot != userHome) {
        println("⚠️ ANDROID_PREFS_ROOT ($prefsRoot) conflicts with ANDROID_USER_HOME ($userHome) — unifying to USER_HOME and clearing PREFS_ROOT for this build")
        // JVM cannot unset env, but we can set a system property that Android SDK checks
        System.setProperty("android.prefs.root", userHome)
    }
    if (prefsRoot != null && userHome == null) {
        println("ℹ️ ANDROID_PREFS_ROOT is set ($prefsRoot) but ANDROID_USER_HOME is not — using PREFS_ROOT as USER_HOME for consistency")
        System.setProperty("android.prefs.root", prefsRoot)
    }
}

rootProject.name = "RED-Ultimate"

// Canonical RED Android product. The legacy Signal fork remains in app/ as an
// extraction source only; it is deliberately outside the build graph.
include(":app")
project(":app").projectDir = file("red-app")

// One protocol shared by Android and the backend.
include(":shared-proto")

// Root QA tasks consume these tools as a composite build. Normal sync/assemble
// does not need the legacy Signal QA composite, so avoid configuring it unless a
// QA task explicitly asks for it. RED_SKIP_BUILD_LOGIC remains an override.
val buildLogicTasks = setOf("buildQa", "qa", "qaRemote", "ci", "ciRemote", "qualityGate", "format")
val buildLogicRequested = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in buildLogicTasks }
val skipBuildLogic = providers.gradleProperty("RED_SKIP_BUILD_LOGIC").orNull
    ?.toBooleanStrictOrNull()
    ?: !buildLogicRequested
if (!skipBuildLogic) includeBuild("build-logic")
if (buildLogicRequested) include(":fast-lint")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Google Maven mirror FIRST (fast + reliable DNS)
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        maven {
            url = uri("$rootDir/local-maven")
            content { includeGroup("org.signal") }
            metadataSources { gradleMetadata() }
        }
        // libsignal-android is a large AAR. Use two HTTPS front doors for Maven Central;
        // strict SHA-256 verification still rejects any byte not pinned in metadata.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
            content { includeGroup("org.signal") }
        }
        maven {
            url = uri("https://repo1.maven.org/maven2")
            content { includeGroup("org.signal") }
        }
        // Alibaba Maven mirror — fast for users behind GFW (China, Yemen sometimes)
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        mavenCentral()
    }
    versionCatalogs {
        create("benchmarkLibs") { from(files("gradle/benchmark-libs.versions.toml")) }
        create("testLibs") { from(files("gradle/test-libs.versions.toml")) }
        create("lintLibs") { from(files("gradle/lint-libs.versions.toml")) }
    }
}
