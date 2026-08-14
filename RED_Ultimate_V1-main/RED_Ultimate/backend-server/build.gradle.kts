plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.0.7"
    id("io.spring.dependency-management") version "1.1.7"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // JavaTimeModule used by JacksonConfig — no longer pulled in transitively
    // by Spring Boot 4 starters (Jackson 2 artifacts are opt-in there), so it
    // must be declared explicitly or the backend fails to compile/boot.
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // PSTN / Asterisk
    implementation("org.asteriskjava:asterisk-java:3.41.0")

    // One authoritative RED protocol shared by Android and the backend
    implementation(project(":shared-proto"))

    // Argon2id password hashing
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    // Local S3-compatible object storage
    implementation("io.minio:minio:8.6.0")
    implementation("org.jsoup:jsoup:1.18.3") // LinkCard Open Graph

    // OkHttp for Dinstar API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.github.rburgst:okhttp-digest:3.1.1")  // HTTP Digest auth (Dinstar New API ≥1102)

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
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
