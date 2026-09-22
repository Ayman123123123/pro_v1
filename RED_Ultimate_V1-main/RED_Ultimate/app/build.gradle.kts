plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.red.sovereign"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.red.sovereign"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0-RED"

        // Maps API key placeholder (required by AndroidManifest.xml)
        manifestPlaceholders["mapsKey"] = "YOUR_MAPS_API_KEY_HERE"

        // BuildConfig fields for server URLs
        buildConfigField("String", "SIGNAL_URL", "\"https://chat.red.local\"")
        buildConfigField("String", "STORAGE_URL", "\"https://storage.red.local\"")
        buildConfigField("String", "SIGNAL_CDN_URL", "\"https://cdn.red.local\"")
        buildConfigField("String", "SIGNAL_CDN2_URL", "\"https://cdn2.red.local\"")
        buildConfigField("String", "SIGNAL_SFU_URL", "\"https://sfu.red.local\"")
        buildConfigField("String", "SIGNAL_STORAGE_URL", "\"https://storage.red.local\"")
        buildConfigField("String", "GIPHY_API_KEY", "\"\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    packaging {
        resources {
            excludes += setOf(
                "**/*.kotlin_metadata",
                "META-INF/*.kotlin_module",
                "META-INF/*.version"
            )
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = true
    }
}

// Apply the dependencies file
apply(from = "dependencies.gradle.kts")
