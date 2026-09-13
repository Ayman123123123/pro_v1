val androidPrefsDir = System.getenv("ANDROID_USER_HOME")
    ?: System.getenv("ANDROID_PREFS_ROOT")
    ?: (System.getProperty("user.home") + "/.android")
System.clearProperty("android.prefs.root")
System.setProperty("android.user.home", androidPrefsDir)

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
// The legacy Signal fork remains in app/ as an extraction source only; it is
// deliberately outside the build graph. Note that app/ is overwhelmingly
// upstream Signal (org.thoughtcrime.securesms); the RED-authored files under
// com.red.sovereign there are superseded by their red-app/ counterparts.
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
