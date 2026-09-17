plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.protobuf)
}

group = "com.red"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    google()
    maven { url = uri("https://repo1.maven.org/maven2/") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    // Spring Boot Starters
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.micrometer.prometheus)
    implementation(libs.spring.boot.starter.validation)

    // Database
    implementation(libs.spring.boot.starter.data.jpa)
    // Flyway is included in spring-boot-starter-data-jpa (autoconfiguration)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Kotlin
    // fasterxml Jackson 2.x pinned explicitly (2.19.2 = catalog choice): Boot 4 BOM
    // manages Jackson 3 only, so unversioned fasterxml never resolves.
    // Jackson 3 (tools.jackson) — الوحيد منذ 2026-09-15 (هاجر الكود كله من fasterxml 2).
    // The module brings databind transitively; both are needed by JacksonConfig.
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines.core)

    // One authoritative RED protocol shared by Android and the backend
    implementation(project(":shared-proto"))

    // Argon2id password hashing
    implementation(libs.bouncycastle)

    // Local S3-compatible object storage
    implementation(libs.minio)
    implementation(libs.jsoup) // LinkCard Open Graph

    // JWT
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Protobuf & gRPC
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    // implementation(libs.grpc.kotlin.stub) // Temporarily disabled - version not found
    implementation(libs.grpc.protobuf)

    // Observability
    // implementation(libs.micrometer.tracing.brave) // Temporarily disabled - version not found
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)

    // OpenAPI 3.1 Documentation
    implementation(libs.springdoc.openapi.starter.webmvc)

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.kotlin)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Produce one deterministic runtime artifact. Disabling the plain JVM JAR also
// prevents Docker COPY globs from selecting two different files accidentally.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("red-backend.jar")
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    enabled = false
}