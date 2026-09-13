dependencies {
  lintChecks(project(":lintchecks"))
  coreLibraryDesugaring(libs.android.tools.desugar)

  // RED Sovereign Core
  implementation(project(":lib:libsignal-service"))
  implementation(project(":core:util"))
  implementation(project(":core:ui"))
  implementation(libs.libsignal.android)
  implementation(libs.signal.android.database.sqlcipher)

  // System A: 1080p VoIP
  implementation(libs.signal.ringrtc)
  implementation(libs.bundles.media3)

  // System B: PSTN Dinstar
  implementation("org.asteriskjava:asterisk-java:3.40.0")

  // System C: Messaging
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.kotlinx.serialization.json)

  // Master UI
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.material.material)
}
