plugins {
    id("java-library")
    id("com.google.protobuf") version "0.10.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    api("com.google.protobuf:protobuf-java:3.25.5")
    api("com.google.protobuf:protobuf-kotlin:3.25.5")
    api("io.grpc:grpc-protobuf:1.62.2")
    api("io.grpc:grpc-stub:1.62.2")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.5"
    }
    // generatedFilesBaseDir is read-only in protobuf-gradle-plugin 0.10.0;
    // default output (build/generated/source/proto) is compiled automatically.
}

// The protobuf plugin automatically creates tasks with protoc configured from the extension above
// No additional task configuration needed
