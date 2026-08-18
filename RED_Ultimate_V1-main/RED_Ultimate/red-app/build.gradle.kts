plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.ksp)
}

// ╪º┘ä┘à┘å┘ü╪░ ╪Ñ┘ä╪▓╪º┘à┘è ┘ü┘è ╪º┘ä┘é┘è┘à╪⌐ ╪º┘ä╪º┘ü╪¬╪▒╪º╪╢┘è╪⌐: ╪¿╪»┘ê┘å┘ç ┘è┘é╪╡╪» OkHttp ╪º┘ä┘à┘å┘ü╪░ 80 ╪¿┘è┘å┘à╪º
// ╪º┘ä╪«╪º╪»┘à ┘è╪│╪¬┘à╪╣ ╪╣┘ä┘ë 8088 (╪¿┘ê╪º╪¿╪⌐ Nginx) ΓÇö ┘ü┘è┘ü╪┤┘ä ┘â┘ä ╪╖┘ä╪¿ ╪¿┘Ç NETWORK_ERROR ╪¿┘ä╪º
// ╪│╪¿╪¿ ╪╕╪º┘ç╪▒. ╪º┘ä╪¿┘å╪º╪í ╪º┘ä╪¡┘é┘è┘é┘è ┘è┘à╪▒┘æ╪▒ -PRED_SERVER_URL=http://SERVER_IP:PORT.
val redServerUrl = providers.gradleProperty("RED_SERVER_URL").orElse("http://192.168.0.181:8088")
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
            // Alpha/╪Ñ╪╡╪»╪º╪▒╪º╪¬ ┘à╪º ┘é╪¿┘ä ╪º┘ä╪Ñ┘å╪¬╪º╪¼ ╪¬┘Å┘ê┘é┘Ä┘æ╪╣ ╪¿┘å┘ü╪│ ╪º┘ä┘ç┘ê┘è╪⌐ ╪º┘ä┘à╪│╪¬┘é╪▒╪⌐ ╪¿┘è┘å
            // Docker ┘êWindows ╪¡╪¬┘ë ╪¬╪¿┘é┘ë ╪¬╪¡╪»┘è╪½╪º╪¬ APK ┘à╪¬┘ê╪º┘ü┘é╪⌐. ┘ä┘ä╪¬┘ê╪▓┘è╪╣ ╪º┘ä╪▒╪│┘à┘è ┘ä╪º╪¡┘é╪º┘ï
            // ╪º╪│╪¬╪¿╪»┘ä ╪¿┘à╪º ┘è┘ä┘è: ┘é╪▒╪º╪í╪⌐ keystore ┘ê┘à╪╣╪▒┘æ┘ü╪º╪¬┘ç ┘à┘å ┘à╪¬╪║┘è╪▒╪º╪¬ ╪¿┘è╪ª╪⌐/┘à┘ä┘ü ╪║┘è╪▒ ┘à╪╣┘à┘ê┘ä
            // (┘ä╪ú┘à╪º┘å ╪ú┘ü╪╢┘ä╪î ┘ä╪º ┘è┘Å╪╣╪¬┘à╪» ┘à┘ü╪¬╪º╪¡ ┘à╪╢┘à┘æ┘å ┘ü┘è ╪º┘ä┘à╪│╪¬┘ê╪»╪╣).
            signingConfig = signingConfigs.getByName("redLocalDebug")
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

    // ΓöÇΓöÇΓöÇ ╪«╪╖┘ê╪╖ Google (Cairo + Tajawal) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.androidx.compose.ui.text.google.fonts)

    // ΓöÇΓöÇΓöÇ Coil ΓÇö ╪¬╪¡┘à┘è┘ä ┘ê╪╣╪▒╪╢ ╪º┘ä╪╡┘ê╪▒ ┘ê╪º┘ä┘ü┘è╪»┘è┘ê ╪¿┘â┘ü╪º╪í╪⌐ ╪╣╪º┘ä┘è╪⌐ ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    // ΓöÇΓöÇΓöÇ Lottie ΓÇö ╪ú┘å┘è┘à┘è╪┤┘å ╪º╪¡╪¬╪▒╪º┘ü┘è (┘à╪ñ╪┤╪▒ ╪º┘ä┘â╪¬╪º╪¿╪⌐╪î ╪▒╪»┘ê╪» ╪º┘ä┘ü╪╣┘ä) ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.lottie.compose)

    // ΓöÇΓöÇΓöÇ emoji2-emojipicker ΓÇö ┘à╪¡╪»╪» ╪º┘ä╪Ñ┘è┘à┘ê╪¼┘è ╪º┘ä╪▒╪│┘à┘è ┘à┘å Google ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.androidx.emoji2.emojipicker)

    // ΓöÇΓöÇΓöÇ Paging 3 ΓÇö ╪¬╪¡┘à┘è┘ä ╪º┘ä┘à╪¡╪º╪»╪½╪º╪¬ ┘ê╪º┘ä┘à┘å╪┤┘ê╪▒╪º╪¬ ╪¿╪¬┘â╪º╪│┘ä ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // ΓöÇΓöÇΓöÇ WorkManager ΓÇö ┘à╪▓╪º┘à┘å╪⌐ ┘ü┘è ╪º┘ä╪«┘ä┘ü┘è╪⌐ ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.androidx.work.runtime.ktx)

    // ΓöÇΓöÇΓöÇ Room ΓÇö ┘é╪º╪╣╪»╪⌐ ╪¿┘è╪º┘å╪º╪¬ ┘à╪¡┘ä┘è╪⌐ ╪│┘è╪º╪»┘è╪⌐ ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    // Room 2.7+ merged all KTX APIs into room-runtime; room-ktx is an empty
    // compatibility artifact, so one runtime dependency preserves every API.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    ksp(libs.androidx.room.compiler)
    implementation(libs.signal.android.database.sqlcipher)

    // ΓöÇΓöÇΓöÇ Accompanist ΓÇö ╪ú╪░┘ê┘å╪º╪¬ ┘ê╪¬╪│┘ç┘è┘ä╪º╪¬ Compose ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.accompanist.permissions)

    // ΓöÇΓöÇΓöÇ Biometric ΓÇö ┘é┘ü┘ä ╪º┘ä╪¬╪╖╪¿┘è┘é ╪¿╪º┘ä╪¿╪╡┘à╪⌐/╪º┘ä┘ê╪¼┘ç ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    testImplementation("junit:junit:4.13.2")
}


