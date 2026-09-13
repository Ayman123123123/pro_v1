// Root build logic for Android Studio IDE integration
tasks.register("assembleDebug") {
    dependsOn(gradle.includedBuild("RED-Ultimate").task(":app:assembleDebug"))
}

tasks.register("installDebug") {
    dependsOn(gradle.includedBuild("RED-Ultimate").task(":app:installDebug"))
}
