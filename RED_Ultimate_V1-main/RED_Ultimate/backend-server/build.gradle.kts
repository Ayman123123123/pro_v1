plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.0.8"
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
    // Boot 4 moved Flyway autoconfiguration out of spring-boot-autoconfigure into
    // its own module. With flyway-core alone there is no FlywayAutoConfiguration on
    // the classpath, so `spring.flyway.*` is read by nobody and NO migration ever
    // runs ó the schema only ever changed when someone applied SQL by hand. This
    // starter brings spring-boot-flyway (the autoconfiguration) plus flyway-core.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin
    // fasterxml Jackson 2.x pinned explicitly (2.19.2 = catalog choice): Boot 4 BOM
    // manages Jackson 3 only, so unversioned fasterxml never resolves.
    // Jackson 3 (tools.jackson) ó «·ÊÕÌœ „‰– 2026-09-15 (Â«Ã— «·ﬂÊœ ﬂ·Â „‰ fasterxml 2).
    // The module brings databind transitively; both are needed by JacksonConfig.
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")

    // One authoritative RED protocol shared by Android and the backend
    implementation(project(":shared-proto"))

    // Argon2id password hashing
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

    // Local S3-compatible object storage
    implementation("io.minio:minio:8.6.0")
    implementation("org.jsoup:jsoup:1.23.1") // LinkCard Open Graph

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

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

