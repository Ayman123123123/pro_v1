plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
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

dependencies {
  compileOnly(libs.lint.api)
  compileOnly(libs.lint.checks)

  testImplementation(libs.lint.tests)
  testImplementation(libs.lint.api)
  testImplementation(testLibs.junit.junit)
}

tasks.jar {
  manifest {
    attributes(
      "Lint-Registry-v2" to "org.signal.lint.Registry"
    )
  }
}
