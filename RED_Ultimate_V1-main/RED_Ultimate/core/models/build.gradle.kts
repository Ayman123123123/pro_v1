/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

plugins {
  id("com.android.library")
  id("kotlin-parcelize")
  alias(libs.plugins.kotlinx.serialization)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

android {
  namespace = "org.signal.core.models"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    vectorDrawables.useSupportLibrary = true
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget(libs.versions.kotlinJvmTarget.get())
      suppressWarnings = true
    }
  }

  lint {
    targetSdk = libs.versions.targetSdk.get().toInt()
    disable += "InvalidVectorPath"
    lintConfig = rootProject.file("lint.xml")
  }
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.jackson.core)
  implementation(libs.jackson.module.kotlin)
  
  // Dependencies previously provided by signal-library plugin
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.fragment.ktx)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.appcompat)
  implementation(libs.rxjava3.rxandroid)
  implementation(libs.rxjava3.rxjava)
  implementation(libs.rxjava3.rxkotlin)
  implementation(libs.kotlin.stdlib.jdk8)
  
  coreLibraryDesugaring(libs.android.tools.desugar)
}
