plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

// The only safe generic default is the Android-emulator alias.  A private LAN
// address from one developer's network makes every other installation fail
// before discovery or the server settings screen can help.
val redServerUrl = providers.gradleProperty("RED_SERVER_URL").orElse("http://10.0.2.2:8088")
val redServerCandidates = providers.gradleProperty("RED_SERVER_CANDIDATES")
    .orElse("http://10.0.2.2:8088,http://127.0.0.1:8088")
val redTlsPins = providers.gradleProperty("RED_TLS_PINS").orElse("")
val redTargetAbi = providers.gradleProperty("RED_TARGET_ABI").orElse("arm64-v8a")
require(redTargetAbi.get() in setOf("arm64-v8a", "armeabi-v7a", "x86_64")) { "Unsupported RED_TARGET_ABI" }

android {
    namespace = "com.red.sovereign"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.red.sovereign"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        // بوابة الستور: الإصدار الحالي 1.0.0-alpha02 — الترقية القادمة للستور → 1.0.0 (stable).
        // خطة الإنتاج المجمدة: versionName = "1.0.0" مع versionCode = 3 — لا تطبق الآن،
        // ابقَ على alpha02 حتى اكتمال بوابة الستور (التوقيع الخاص + ملاحظات الإصدار العربية).
        // لا ترفع alpha للستور؛ ارفع versionCode مع كل حزمة وجهّز ملاحظات الإصدار العربية.
        versionName = "1.0.0-alpha02"
        buildConfigField("String", "RED_SERVER_URL", "\"${redServerUrl.get()}\"")
        val escapedCandidates = redServerCandidates.get().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "RED_SERVER_CANDIDATES", "\"$escapedCandidates\"")
        val escapedPins = redTlsPins.get().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "RED_TLS_PINS", "\"$escapedPins\"")
        // بوابة الستور: إخفاء الشارات المؤقتة في release عبر BuildConfig (الأصل true للـ debug فقط).
        buildConfigField("boolean", "SHOW_PLACEHOLDERS", "true")
        ndk { abiFilters += redTargetAbi.get() }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("redLocalDebug") {
            // Public, debug-only key: stable across Docker/Windows builds so Alpha APK updates work.
            // Production release signing must use an offline private key and a separate applicationId/version policy.
            storeFile = file("signing/red-debug.p12")
            storePassword = "red-debug-only"
            keyAlias = "reddebug"
            keyPassword = "red-debug-only"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartext"] = "true"
            signingConfig = signingConfigs.getByName("redLocalDebug")
            buildConfigField("boolean", "SHOW_PLACEHOLDERS", "true")
        }
        release {
            isMinifyEnabled = true
            // LEGENDARY FIX: تقليص الموارد + ABI واحد (كان APK ~259MB مستحيل التثبيت)
            isShrinkResources = true
            manifestPlaceholders["usesCleartext"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // بوابة الستور: الشارات المؤقتة مخفية تمامًا في release.
            buildConfigField("boolean", "SHOW_PLACEHOLDERS", "false")
            // Release signing comes from env/props (RED_KEYSTORE_*) — never
            // hardcode secrets in version control. بوابة الستور: يمنع منعًا باتًا
            // التوقيع بمفتاح debug العلني في release — يجب توفير RED_KEYSTORE_*.
            // 2026-09-10: فشل صريح بدون مفتاح خاص — ممنوع السقوط لمفتاح debug.
            // (يُفحص فقط عند طلب مهام release حتى لا يكسر بناءات debug اليومية)
            val keystoreFile = providers.gradleProperty("RED_KEYSTORE_FILE").orElse("").get()
            val wantsRelease = gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true) || it.contains("Bundle", ignoreCase = true)
            }
            if (keystoreFile.isNotBlank()) {
                signingConfig = signingConfigs.create("redRelease") {
                    storeFile = file(keystoreFile)
                    storePassword = providers.gradleProperty("RED_KEYSTORE_PASSWORD").orElse("").get()
                    keyAlias = providers.gradleProperty("RED_KEY_ALIAS").orElse("").get()
                    keyPassword = providers.gradleProperty("RED_KEY_PASSWORD").orElse("").get()
                    storeType = "PKCS12"
                }
            } else if (wantsRelease) {
                error("RED release signing requires private key: set RED_KEYSTORE_FILE/RED_KEYSTORE_PASSWORD/RED_KEY_ALIAS/RED_KEY_PASSWORD (see keystore.properties — never use red-debug.p12 for release)")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

// AGP 9 supplies Kotlin compilation itself. Keep one compilerOptions block;
// legacy option blocks and task-level overrides would create conflicting JVM
// targets and are intentionally not used.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get()))
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.javaVersion.get().toInt()))
    }
}

configurations.all {
    // Drop the standalone core jar — its classes live inside tink-android (see above).
    exclude(group = "com.google.crypto.tink", module = "tink")
}

dependencies {
    coreLibraryDesugaring(libs.android.tools.desugar)

    // Keep the Kotlin runtime and Compose artifacts on one coherent line.
    implementation(platform(libs.kotlin.bom))
    // Sovereign push: UnifiedPush connector (self-hosted ntfy distributor, zero Google services).
    implementation(libs.unifiedpush.connector)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling.core)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.square.okhttp3)
    implementation(libs.libsignal.android)
    implementation(libs.google.zxing.core)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.core.telecom)
    implementation(libs.webrtc.android)
    implementation(project(":shared-proto"))

    // DataStore for ScheduledCalls
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)

    // UI dependencies for Overlays and IncomingCallActivity
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.material.material)
    implementation(libs.androidx.core.splashscreen)

    // الخطوط مضمّنة محلياً (res/font/plex_arabic — SIL OFL) — لا خطوط Google الشبكية.

    // ───── Coil 3.x — تحميل وعرض الصور والفيديو (3.6.0، يخلف 2.7.0 المجمّد) ─────
    implementation(libs.coil3.compose)
    implementation(libs.coil3.video)
    implementation(libs.coil3.network.okhttp)

    // ───── Haze — ضبابية خلفية حقيقية للأشرطة الزجاجية ─────
    implementation(libs.haze.compose)

    // ───── Lottie — أنيميشن احترافي (مؤشر الكتابة، ردود الفعل) ─────
    implementation(libs.lottie.compose)

    // ───── Vosk — تفريغ صوتي دون اتصال (نماذج تُنزَّل عند الطلب) ─────
    implementation(libs.vosk.android)

    // ───── emoji2-emojipicker — محدد الإيموجي الرسمي من Google ─────
    implementation(libs.androidx.emoji2.emojipicker)

    // ───── Paging 3 — تحميل المحادثات والمنشورات بتكاسل ─────
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ───── WorkManager — مزامنة في الخلفية ─────
    implementation(libs.androidx.work.runtime.ktx)

    // ───── Room — قاعدة بيانات محلية سيادية ─────
    // Room 2.7+ merged all KTX APIs into room-runtime; room-ktx is an empty
    // compatibility artifact, so one runtime dependency preserves every API.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)
    implementation(libs.signal.android.database.sqlcipher)

    // ───── Accompanist — أذونات وتسهيلات Compose ─────
    implementation(libs.accompanist.permissions)

    // ───── Biometric — قفل التطبيق بالبصمة/الوجه ─────
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    // Tink single-source (CI-proven): tink-android AAR *bundles* core classes at every
    // version (1.8.0 AND 1.23.0 both duplicate tink-core), so the graph must carry exactly
    // one of them. The android AAR is the superset (core + AndroidKeystore), pinned modern.
    implementation("com.google.crypto.tink:tink-android:1.23.0")

    testImplementation("junit:junit:4.13.2")
}


