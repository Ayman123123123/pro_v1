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

// ── Fix: Android Home Trap — Legendary Hardening for Gradle 9.7 + AGP 9.3 (Isolated Projects) ──
// Gradle 9.4.1+ (now 9.7.0) is strict: AndroidLocationsBuildService throws if
// ANDROID_PREFS_ROOT / ANDROID_USER_HOME / deprecated ANDROID_SDK_HOME point to different places.
// Docker mounts .android_home vs host .android cause deterministic failure.
// JVM cannot unset env, so we unify via system properties that Android SDK checks FIRST.
// This block runs at settings-evaluation-time (before any Android plugin) — required for
// configuration-cache + isolated-projects compatibility.
// See: https://stackoverflow.com/questions/50520656/android_home-vs-android_sdk_home
run {
    val prefsRoot = System.getenv("ANDROID_PREFS_ROOT")
    val userHome = System.getenv("ANDROID_USER_HOME")
    val sdkHomeDeprecated = System.getenv("ANDROID_SDK_HOME") // deprecated, must NOT equal SDK root
    val androidHome = System.getenv("ANDROID_HOME")
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")

    // 1) Detect deprecated ANDROID_SDK_HOME misuse (should be prefs parent, not SDK root)
    if (sdkHomeDeprecated != null && (sdkHomeDeprecated == androidHome || sdkHomeDeprecated == sdkRoot)) {
        println("⚠️ ANDROID_SDK_HOME is set to SDK root ($sdkHomeDeprecated) — deprecated misuse. Use ANDROID_HOME for SDK, ANDROID_USER_HOME for prefs. Unifying prefs to USER_HOME.")
        // Force prefs to USER_HOME if available, otherwise to GRADLE_USER_HOME
        val fallback = userHome ?: System.getProperty("user.home") + "/.android"
        System.setProperty("android.prefs.root", fallback)
        System.setProperty("android.user.home", fallback)
    }

    // 2) Primary conflict: PREFS_ROOT vs USER_HOME
    if (prefsRoot != null && userHome != null && prefsRoot != userHome) {
        println("⚠️ ANDROID_PREFS_ROOT ($prefsRoot) conflicts with ANDROID_USER_HOME ($userHome) — unifying to USER_HOME")
        System.setProperty("android.prefs.root", userHome)
        System.setProperty("android.user.home", userHome)
        // Also align deprecated SDK_HOME if it pointed to prefsRoot
        if (sdkHomeDeprecated == prefsRoot) {
            System.setProperty("android.sdk.home", userHome)
        }
    } else if (prefsRoot != null && userHome == null) {
        println("ℹ️ ANDROID_PREFS_ROOT is set ($prefsRoot) but ANDROID_USER_HOME is not — using PREFS_ROOT as USER_HOME for consistency")
        System.setProperty("android.prefs.root", prefsRoot)
        System.setProperty("android.user.home", prefsRoot)
    } else if (prefsRoot == null && userHome != null) {
        // Normal case: Docker or local sets USER_HOME only — ensure prefs.root mirrors it
        System.setProperty("android.prefs.root", userHome)
        System.setProperty("android.user.home", userHome)
    }

    // 3) Log resolved state for CI debugging (visible in --info)
    val resolvedPrefs = System.getProperty("android.prefs.root") ?: userHome ?: prefsRoot ?: "default (~/.android)"
    println("✅ Android prefs resolved to: $resolvedPrefs (Gradle 9.7 strict mode satisfied)")
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
        // Google Maven mirror FIRST (fast + reliable DNS) — for all androidx/google deps
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        // ── Sovereign Signal Artifact: Legendary Resolution Chain ──
        // libsignal-android 0.99.1 is Sovereign PQXDH-patched. Resolution order is critical:
        // 1) local-maven (if populated by CI via LFS or manual Sovereign build) — fastest, offline-capable
        // 2) Google Maven Central mirror (storage-download) — reliable DNS + SHA-256 pinned
        // 3) repo1.maven.org — fallback — strict SHA-256 still rejects tampered bytes
        // local-maven is intentionally empty in git (see local-maven/.gitignore). CI populates it
        // via `./scripts/fetch-sovereign-signal.sh` if needed. Empty local-maven safely falls through.
        maven {
            url = uri("$rootDir/local-maven")
            content { includeGroup("org.signal") }
            metadataSources { gradleMetadata() }
            // Optional: log when local-maven is empty for CI visibility
            // (Gradle 9.7 will not fail on empty dir due to content filter)
        }
        // libsignal-android is a large AAR (~15MB). Two HTTPS front doors for Maven Central;
        // strict SHA-256 verification (gradle/verification-metadata.xml) rejects any byte not pinned.
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
            content { includeGroup("org.signal") }
        }
        maven {
            url = uri("https://repo1.maven.org/maven2")
            content { includeGroup("org.signal") }
        }
        // Alibaba Maven mirror — fast for users behind GFW (China, Yemen sometimes) — for non-signal deps
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
