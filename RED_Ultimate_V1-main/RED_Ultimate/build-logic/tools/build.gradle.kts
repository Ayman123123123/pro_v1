plugins {
  alias(libs.plugins.jetbrains.kotlin.jvm)
  id("java-library")
  alias(libs.plugins.ktlint)
  id("checkstyle")
}

java {
  sourceCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
  targetCompatibility = JavaVersion.toVersion(libs.versions.javaVersion.get())
}

kotlin {
  jvmToolchain {
    languageVersion = JavaLanguageVersion.of(libs.versions.kotlinJvmTarget.get())
  }
}

// NOTE: For now, in order to run ktlint on this project, you have to manually run ./gradlew :build-logic:tools:ktlintFormat
//       Gotta figure out how to get it auto-included in the normal ./gradlew ktlintFormat
ktlint {
  version.set("1.5.0")
}

checkstyle {
  toolVersion = "10.21.0"
  config = resources.text.fromFile("$rootDir/config/checkstyle/checkstyle.xml")
  configProperties = [
    "checkstyle.config.loc" : "$rootDir/config/checkstyle"
  ]
  reports {
    xml.required.set(true)
    html.required.set(true)
  }
}

dependencies {
  implementation(gradleApi())

  implementation(libs.dnsjava)
  api(libs.square.okhttp3)

  testImplementation(testLibs.junit.junit)
  testImplementation(testLibs.mockk)
  testImplementation(testLibs.square.mockwebserver)
}
