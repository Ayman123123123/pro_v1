plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

// المنفذ الإلزامي في القيمة الافتراضية: بدونه يقصد OkHttp المنفذ 80 بينما الخادم
// يستمع على 8088 (بوابة Nginx) â€” فيفشل كل طلب بـ NETWORK_ERROR بلا سبب ظاهر.
// البناء الحقيقي يمرّر -PRED_SERVER_URL=http://SERVER_IP:PORT.
// Default to the host Wi-Fi interface verified for physical Android clients.
// CI/production may override this with -PRED_SERVER_URL=https://your-domain.
val redServerUrl = providers.gradleProperty("RED_SERVER_URL").orElse("http://192.168.11.131:8088")
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
            // التوقيع الرسمي يُقرأ من متغيرات بيئة/خصائص (RED_KEYSTORE_*) â€” لا
            // مفاتيح إنتاج مضمنة في المستودع. إن لم تُضبط تُستخدم هوية alpha
            // المؤقتة (مفتاح debug عام) حتى يُكمل سير العمل المحلي.
            val keystoreFile = providers.gradleProperty("RED_KEYSTORE_FILE").orElse("").get()
            if (keystoreFile.isNotBlank()) {
                signingConfig = signingConfigs.create("redRelease") {
                    storeFile = file(keystoreFile)
                    storePassword = providers.gradleProperty("RED_KEYSTORE_PASSWORD").orElse("").get()
                    keyAlias = providers.gradleProperty("RED_KEY_ALIAS").orElse("").get()
                    keyPassword = providers.gradleProperty("RED_KEY_PASSWORD").orElse("").get()
                    storeType = "PKCS12"
                }
            } else {
                signingConfig = signingConfigs.getByName("redLocalDebug")
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

dependencies {
    coreLibraryDesugaring(libs.android.tools.desugar)

    // Keep the Kotlin runtime and Compose artifacts on one coherent line.
    implementation(platform(libs.kotlin.bom))
    // FCM — يعمل بلا google-services.json؛ التهيئة تتم يدوياً عند توفر الملف
    implementation("com.google.firebase:firebase-messaging:24.1.0")
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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
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

    // â”€â”€â”€â”€â”€ خطوط Google (Cairo + Tajawal) â”€â”€â”€â”€â”€
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // â”€â”€â”€â”€â”€ Coil â€” تحميل وعرض الصور والفيديو بكفاءة عالية â”€â”€â”€â”€â”€
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // â”€â”€â”€â”€â”€ Lottie â€” أنيميشن احترافي (مؤشر الكتابة، ردود الفعل) â”€â”€â”€â”€â”€
    implementation(libs.lottie.compose)

    // â”€â”€â”€â”€â”€ emoji2-emojipicker â€” محدد الإيموجي الرسمي من Google â”€â”€â”€â”€â”€
    implementation(libs.androidx.emoji2.emojipicker)

    // â”€â”€â”€â”€â”€ Paging 3 â€” تحميل المحادثات والمنشورات بتكاسل â”€â”€â”€â”€â”€
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // â”€â”€â”€â”€â”€ WorkManager â€” مزامنة في الخلفية â”€â”€â”€â”€â”€
    implementation(libs.androidx.work.runtime.ktx)

    // â”€â”€â”€â”€â”€ Room â€” قاعدة بيانات محلية سيادية â”€â”€â”€â”€â”€
    // Room 2.7+ merged all KTX APIs into room-runtime; room-ktx is an empty
    // compatibility artifact, so one runtime dependency preserves every API.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)
    implementation(libs.signal.android.database.sqlcipher)

    // â”€â”€â”€â”€â”€ Accompanist â€” أذونات وتسهيلات Compose â”€â”€â”€â”€â”€
    implementation(libs.accompanist.permissions)

    // â”€â”€â”€â”€â”€ Biometric â€” قفل التطبيق بالبصمة/الوجه â”€â”€â”€â”€â”€
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    testImplementation("junit:junit:4.13.2")
}


