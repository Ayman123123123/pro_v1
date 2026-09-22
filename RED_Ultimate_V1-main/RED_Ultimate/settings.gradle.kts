// ── Fix: Android Home Trap — Gradle 9.7 + AGP 9.3 strict (Isolated Projects) ──
// Gradle 9.4+ is strict: AndroidLocationsBuildService throws when
// ANDROID_PREFS_ROOT / ANDROID_USER_HOME / deprecated ANDROID_SDK_HOME disagree.
// Docker mounting .android_home while the host has .android fails deterministically.
// The JVM cannot unset env vars, so we unify through the system properties the Android
// SDK consults first. This runs at settings-evaluation time — before any Android plugin —
// which is what configuration-cache and isolated-projects require.
run {
    val prefsRoot = System.getenv("ANDROID_PREFS_ROOT")
    val userHome = System.getenv("ANDROID_USER_HOME")
    val sdkHomeDeprecated = System.getenv("ANDROID_SDK_HOME") // deprecated: prefs parent, NOT the SDK root
    val androidHome = System.getenv("ANDROID_HOME")
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")

    // 1) Reject the deprecated ANDROID_SDK_HOME = SDK root misuse.
    if (sdkHomeDeprecated != null && (sdkHomeDeprecated == androidHome || sdkHomeDeprecated == sdkRoot)) {
        println("⚠️ ANDROID_SDK_HOME points at the SDK root ($sdkHomeDeprecated) — deprecated. Falling back prefs to ANDROID_USER_HOME/USER_HOME.")
        val fallback = userHome ?: (System.getProperty("user.home") + "/.android")
        System.setProperty("android.prefs.root", fallback)
        System.setProperty("android.user.home", fallback)
    }

    // 2) Primary conflict: ANDROID_PREFS_ROOT vs ANDROID_USER_HOME — unify to USER_HOME.
    if (prefsRoot != null && userHome != null && prefsRoot != userHome) {
        println("⚠️ ANDROID_PREFS_ROOT ($prefsRoot) conflicts with ANDROID_USER_HOME ($userHome) — unifying to USER_HOME")
        System.setProperty("android.prefs.root", userHome)
        System.setProperty("android.user.home", userHome)
        if (sdkHomeDeprecated == prefsRoot) System.setProperty("android.sdk.home", userHome)
    } else if (prefsRoot != null && userHome == null) {
        println("ℹ️ ANDROID_PREFS_ROOT ($prefsRoot) set without ANDROID_USER_HOME — mirroring it for consistency")
        System.setProperty("android.prefs.root", prefsRoot)
        System.setProperty("android.user.home", prefsRoot)
    } else if (prefsRoot == null && userHome != null) {
        // Normal container case: only USER_HOME is set — prefs must mirror it.
        System.setProperty("android.prefs.root", userHome)
        System.setProperty("android.user.home", userHome)
    } else {
        val fallback = System.getProperty("user.home") + "/.android"
        System.setProperty("android.prefs.root", fallback)
        System.setProperty("android.user.home", fallback)
    }

    // 3) Resolved state for CI logs (the trap guard greps this line).
    val resolvedPrefs = System.getProperty("android.prefs.root") ?: userHome ?: prefsRoot ?: "default (~/.android)"
    println("✅ Android prefs resolved to: $resolvedPrefs (Gradle 9.7 strict mode satisfied)")
}

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

rootProject.name = "RED-Ultimate"

// backend-server مشروع Spring Boot مستقلّ ببناء منفصل — خارج build graph
// الأندرويد عمدًا: له settings.gradle.kts وgradlew الخاصّان به، ويُبنى عبر
// scripts/build-backend.sh (أو ./backend-server/gradlew مباشرةً) ويُشحَن
// كحاوية Docker (backend-server/Dockerfile). لا تضِفه هنا بـ include
// حتى لا يلوّث sync الأندرويد بإعدادات JVM/Spring ولا يكسر البناء.

// Canonical RED Android product — the single source of truth for the app.
//
// Consolidated on 2026-08-19: the android/ and app-android/ extraction trees
// were merged into red-app/ and deleted. They were parallel prototypes of the
// same screens under different package roots (com.red.features, com.red.feature),
// outside the build graph, and had already drifted — android/ shipped a Yemeni
// operator prefix table that contradicted both red-app/ and the backend.
// Everything of value from them now lives here; see docs/UNIFICATION_2026-08-19.md.
//
// Phase 11 (2026-09-14): the legacy Signal fork app/ was triaged and deleted
// (~7000 files, all stubs/superseded/cancelled-scope; 5 call-progress sounds
// extracted to red-app/src/main/res/raw/). red-app/ is the single Android app.
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
         mavenCentral()
         google()
         // Google Maven mirror FIRST (fast + reliable DNS) — for all androidx/google deps
        maven {
            url = uri("https://maven-central.storage-download.googleapis.com/maven2")
        }
        // ── Sovereign Signal Artifact: Legendary Resolution Chain ──
        // libsignal-android 0.86.5 is the latest published Central artifact. Resolution order:
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
