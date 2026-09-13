pluginManagement {
    repositories {
        google()
        maven { url = java.net.URI.create("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "pro_new"

// ربط مشروع RED Ultimate الرئيسي بأدوات البناء ومطرقة أندرويد ستوديو
includeBuild("RED_Ultimate_V1-main/RED_Ultimate")
