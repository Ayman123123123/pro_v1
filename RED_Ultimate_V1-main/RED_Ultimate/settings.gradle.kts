// Must run BEFORE pluginManagement: AGP Isolated Projects reads prefs while
// resolving the Android plugin. Gradle 9.4+ throws if ANDROID_PREFS_ROOT and
// ANDROID_USER_HOME disagree (Docker .android_home vs Windows %USERPROFILE%\\.android).
run {
    val prefsRoot = System.getenv("ANDROID_PREFS_ROOT")
    val userHome = System.getenv("ANDROID_USER_HOME")
    val sdkHomeDeprecated = System.getenv("ANDROID_SDK_HOME")
    val androidHome = System.getenv("ANDROID_HOME")
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
    val fallback = userHome
        ?: prefsRoot
        ?: (System.getProperty("user.home") + "/.android")

    fun unify(to: String, why: String) {
        System.setProperty("android.prefs.root", to)
        System.setProperty("android.user.home", to)
        println("⚠️ $why — unifying Android prefs to $to")
    }

    when {
        sdkHomeDeprecated != null && (sdkHomeDeprecated == androidHome || sdkHomeDeprecated == sdkRoot) ->
            unify(fallback, "ANDROID_SDK_HOME is set to the SDK root (deprecated)")
        prefsRoot != null && userHome != null && prefsRoot != userHome ->
            unify(userHome, "ANDROID_PREFS_ROOT ($prefsRoot) conflicts with ANDROID_USER_HOME ($userHome)")
        prefsRoot != null && userHome == null ->
            unify(prefsRoot, "ANDROID_PREFS_ROOT is set but ANDROID_USER_HOME is not")
        prefsRoot == null && userHome != null -> {
            System.setProperty("android.prefs.root", userHome)
            System.setProperty("android.user.home", userHome)
        }
        else -> {
            System.setProperty("android.prefs.root", fallback)
            System.setProperty("android.user.home", fallback)
        }
    }
    println("✅ Android prefs resolved to: ${System.getProperty("android.prefs.root")} (Gradle 9.7 strict mode)")
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
