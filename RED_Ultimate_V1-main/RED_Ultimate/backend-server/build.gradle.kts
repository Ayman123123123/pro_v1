plugins {
    alias(libs.plugins.kotlin-jvm)
    alias(libs.plugins.kotlin-spring)
    alias(libs.plugins.kotlin-jpa)
    alias(libs.plugins.spring-boot)
    alias(libs.plugins.spring-dependency-management)
}

group = "com.red"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
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
    implementation(libs.spring.boot.starter.webflux)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.data.mongodb)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.aop)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.threads)

    // OpenAPI 3.1 (springdoc-openapi)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Rate limiting (Bucket4j)
    implementation("com.bucket4j:bucket4j-spring-boot3-starter:8.12.0")
    implementation("com.bucket4j:bucket4j-redis:8.12.0")

    // Database
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.mongodb.driver.sync)
    implementation(libs.mongodb.driver.reactivestreams)
    implementation(libs.redis.client)

    // Kotlin
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.core)

    // One authoritative RED protocol shared by Android and the backend
    implementation(project(":shared-proto"))

    // Security
    implementation(libs.spring.security.crypto)
    implementation(libs.spring.security.oauth2.resource.server)
    implementation(libs.spring.security.oauth2.jose)
    implementation(libs.nimbus.jose.jwt)

    // Protobuf & Serialization
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.grpc.protobuf)

    // Observability
    implementation(libs.micrometer.prometheus)
    implementation(libs.micrometer.tracing.brave)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation("io.opentelemetry:opentelemetry-api:1.45.0")
    implementation("io.opentelemetry:opentelemetry-semconv:1.26.0-alpha")

    // Structured logging (Logstash Logback Encoder)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")

    // Argon2id password hashing
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // Local S3-compatible object storage
    implementation(libs.minio)
    implementation(libs.jsoup) // LinkCard Open Graph

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // Testing
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mongodb)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.minio)
    testImplementation("au.com.dius.pact:consumer-junit5:4.6.13")
    testImplementation("au.com.dius.pact:provider-junit5:4.6.13")
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

