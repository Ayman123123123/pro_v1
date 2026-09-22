plugins {
  id("com.android.library")
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "org.signal.core.ui"

  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    vectorDrawables.useSupportLibrary = true
  }

  buildFeatures {
    compose = true
  }

  testFixtures {
    enable = true
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
    targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  }

  kotlin {
    compilerOptions {
      jvmTarget = libs.versions.kotlinJvmTarget.get()
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
  lintChecks(project(":lintchecks"))

  api(project(":core:util"))

  api(platform(libs.androidx.compose.bom))
  androidTestImplementation(platform(libs.androidx.compose.bom))

  api(libs.androidx.compose.material3)
  api(libs.androidx.compose.material3.adaptive)
  api(libs.androidx.compose.material3.adaptive.layout)
  api(libs.androidx.compose.material3.adaptive.navigation)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  api(libs.androidx.compose.ui.tooling.preview)
  api(libs.androidx.activity.compose)
  debugApi(libs.androidx.compose.ui.tooling.core)
  api(libs.androidx.fragment.compose)
  implementation(libs.kotlinx.serialization.json)
  api(libs.google.zxing.core)
  api(libs.material.material)
  api(libs.androidx.window.window)
  api(libs.accompanist.permissions)

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

  // JUnit is used by test fixtures
  testFixturesImplementation(testLibs.junit.junit)
}
