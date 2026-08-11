plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    id("org.jetbrains.kotlin.kapt")
}

val redServerUrl = providers.gradleProperty("RED_SERVER_URL").orElse("http://192.168.1.50")
val redTargetAbi = providers.gradleProperty("RED_TARGET_ABI").orElse("arm64-v8a")
require(redTargetAbi.get() in setOf("arm64-v8a", "armeabi-v7a", "x86_64")) { "Unsupported RED_TARGET_ABI" }

android {
    namespace = "com.red.sovereign"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.red.sovereign"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-alpha01"
        buildConfigField("String", "RED_SERVER_URL", "\"${redServerUrl.get()}\"")
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

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging.resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.tools.desugar)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling.core)
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
    implementation(libs.androidx.media3.exoplayer)
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
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.signal.android.database.sqlcipher)

    // ─── Accompanist — أذونات وتسهيلات Compose ─────────────────────────────────
    implementation(libs.accompanist.permissions)

    testImplementation("junit:junit:4.13.2")
}
