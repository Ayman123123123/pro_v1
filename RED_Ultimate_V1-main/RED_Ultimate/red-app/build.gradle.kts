plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

// المنفذ إلزامي في القيمة الافتراضية: بدونه يقصد OkHttp المنفذ 80 بينما
// الخادم يستمع على 8088 (بوابة Nginx) — فيفشل كل طلب بـ NETWORK_ERROR بلا
// سبب ظاهر. البناء الحقيقي يمرّر -PRED_SERVER_URL=http://SERVER_IP:PORT.
val redServerUrl = providers.gradleProperty("RED_SERVER_URL").orElse("http://192.168.1.50:8088")
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
        versionCode = 1
        versionName = "1.0.0-alpha01"
        buildConfigField("String", "RED_SERVER_URL", "\"${redServerUrl.get()}\"")
        val escapedPins = redTlsPins.get().replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "RED_TLS_PINS", "\"$escapedPins\"")
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
        }
        release {
            isMinifyEnabled = true
            manifestPlaceholders["usesCleartext"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

dependencies {
    coreLibraryDesugaring(libs.android.tools.desugar)

    // Keep the Kotlin runtime and Compose artifacts on one coherent line.
    implementation(platform(libs.kotlin.bom))
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

    // ─── خطوط Google (Cairo + Tajawal) ───────────────────────────────────────
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // ─── Coil — تحميل وعرض الصور والفيديو بكفاءة عالية ───────────────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ─── Lottie — أنيميشن احترافي (مؤشر الكتابة، ردود الفعل) ─────────────────
    implementation(libs.lottie.compose)

    // ─── emoji2-emojipicker — محدد الإيموجي الرسمي من Google ──────────────────
    implementation(libs.androidx.emoji2.emojipicker)

    // ─── Paging 3 — تحميل المحادثات والمنشورات بتكاسل ───────────────────────
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ─── WorkManager — مزامنة في الخلفية ─────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)

    // ─── Room — قاعدة بيانات محلية سيادية ────────────────────────────────────
    // Room 2.7+ merged all KTX APIs into room-runtime; room-ktx is an empty
    // compatibility artifact, so one runtime dependency preserves every API.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)
    implementation(libs.signal.android.database.sqlcipher)

    // ─── Accompanist — أذونات وتسهيلات Compose ─────────────────────────────────
    implementation(libs.accompanist.permissions)

    // ─── Biometric — قفل التطبيق بالبصمة/الوجه ────────────────────────────────
    implementation(libs.androidx.biometric)

    testImplementation("junit:junit:4.13.2")
}
