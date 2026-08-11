plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.jetbrains.kotlin.jvm) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlinx.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.ktlint)
  alias(libs.plugins.hilt) apply false
  alias(benchmarkLibs.plugins.baselineprofile) apply false
}

val buildLogicTasks = setOf("buildQa", "qa", "qaRemote", "ci", "ciRemote", "qualityGate", "format")
val buildLogicRequested = gradle.startParameter.taskNames.any { it.substringAfterLast(':') in buildLogicTasks }
val buildLogicIncluded = providers.gradleProperty("RED_SKIP_BUILD_LOGIC")
  .map { it.toBooleanStrictOrNull() ?: error("RED_SKIP_BUILD_LOGIC must be true or false") }
  .map { !it }
  .orElse(buildLogicRequested)

tasks.withType<Wrapper> {
  distributionType = Wrapper.DistributionType.ALL
}

subprojects {
  if (JavaVersion.current().isJava8Compatible) {
    allprojects {
      tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
      }
    }
  }

  tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 4).coerceAtLeast(1)
  }
}

tasks.register("buildQa") {
  group = "Verification"
  description = "Quality Assurance for build logic."
  if (buildLogicIncluded.get()) {
    dependsOn(
      gradle.includedBuild("build-logic").task(":tools:test"),
      gradle.includedBuild("build-logic").task(":tools:ktlintCheck"),
      gradle.includedBuild("build-logic").task(":plugins:ktlintCheck")
    )
  }
}

tasks.register("androidCheck") {
  group = "Verification"
  description = "Runs canonical Android unit tests, lint, and a debug APK build."
  dependsOn(":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug")
}

tasks.register<Exec>("backendCheck") {
  group = "Verification"
  description = "Runs the independent Spring backend test suite with its pinned wrapper."
  workingDir("backend-server")
  val wrapper = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "gradlew.bat" else "./gradlew"
  commandLine(wrapper, "test", "--no-daemon", "--stacktrace")
}

tasks.register("qa") {
  group = "Verification"
  description = "Full Android and build-logic quality assurance. Run before release."
  dependsOn("clean", "androidCheck")
}

tasks.register("qualityGate") {
  group = "Verification"
  description = "Runs Android, backend, static, build-logic, and fast-lint release gates."
  dependsOn("ci", "backendCheck")
}

tasks.register("ci") {
  group = "Verification"
  description = "PR gate: canonical Android build/tests plus fast lint and build-logic checks."
  dependsOn("clean", "androidCheck")
}

tasks.register("validateScreenshots") {
  group = "Verification"
  description = "Validates Compose screenshot tests. Intended to run only on CI, not local builds."
}

tasks.register("qaRemote") {
  group = "Verification"
  description = "Full qa plus screenshot validation. Intended to run on CI, not local builds."
  dependsOn("qa")
  dependsOn("validateScreenshots")
}

tasks.register("ciRemote") {
  group = "Verification"
  description = "Faster PR verification (ci) plus screenshot validation. Intended to run on CI, not local builds."
  dependsOn("ci")
  dependsOn("validateScreenshots")
}

// Wire up QA dependencies after all projects are evaluated
gradle.projectsEvaluated {
  val appTestTask = tasks.findByPath(":app:testDebugUnitTest")
  val appLintTask = tasks.findByPath(":app:lintDebug")
  val appCompileInstrumentationTask = tasks.findByPath(":app:compileDebugAndroidTestSources")

  tasks.named("qa") {
    dependsOn("ktlintCheck")
    dependsOn("buildQa")
    dependsOn("checkStopship")

    // Main app tasks
    appTestTask?.let { dependsOn(it) }
    appLintTask?.let { dependsOn(it) }

    // Instrumentation
    appCompileInstrumentationTask?.let { dependsOn(it) }

    // All subproject ktlint checks
    subprojects.forEach { subproject ->
      subproject.tasks.findByName("ktlintCheck")?.let { dependsOn(it) }
    }

    // Library module tasks
    subprojects.filter { it.name != "app" }.forEach { subproject ->
      val testTask = subproject.tasks.findByName("testDebugUnitTest") ?: subproject.tasks.findByName("test")
      testTask?.let { dependsOn(it) }

      subproject.tasks.findByName("lintDebug")?.let { dependsOn(it) }
    }
  }

  tasks.named("validateScreenshots") {
    subprojects.filter { it.name != "app" }.forEach { subproject ->
      subproject.tasks.findByName("validateDebugScreenshotTest")?.let { dependsOn(it) }
    }
  }

  tasks.named("ci") {
    dependsOn("ktlintCheck")
    dependsOn("buildQa")
    dependsOn("checkStopship")

    appTestTask?.let { dependsOn(it) }
    appCompileInstrumentationTask?.let { dependsOn(it) }

    dependsOn(":fast-lint:fastLint")

    subprojects.forEach { subproject ->
      subproject.tasks.findByName("ktlintCheck")?.let { dependsOn(it) }
    }

    subprojects.filter { it.name != "app" }.forEach { subproject ->
      val testTask = subproject.tasks.findByName("testDebugUnitTest") ?: subproject.tasks.findByName("test")
      testTask?.let { dependsOn(it) }
    }
  }

  // Ensure clean runs before everything else
  rootProject.allprojects.forEach { project ->
    project.tasks.matching { it.name != "clean" }.configureEach {
      mustRunAfter("clean")
    }
  }

  // If you let all of these things run in parallel, gradle will likely OOM.
  // To avoid this, we put non-app tests and lints behind the much heavier app tests and lints.
  subprojects.filter { it.name != "app" }.forEach { subproject ->
    appTestTask?.let { task ->
      subproject.tasks.findByName("testDebugUnitTest")?.mustRunAfter(task)
      subproject.tasks.findByName("test")?.mustRunAfter(task)
    }
    appLintTask?.let { task ->
      subproject.tasks.findByName("lintDebug")?.mustRunAfter(task)
    }
  }
}

tasks.register("clean", Delete::class) {
  delete(rootProject.layout.buildDirectory)
}

tasks.register("format") {
  group = "Formatting"
  description = "Runs the ktlint formatter on all sources in this project and included builds"
  dependsOn(*subprojects.mapNotNull { tasks.findByPath(":${it.path}:ktlintFormat") }.toTypedArray())
  if (buildLogicIncluded.get()) {
    dependsOn(
      gradle.includedBuild("build-logic").task(":plugins:ktlintFormat"),
      gradle.includedBuild("build-logic").task(":tools:ktlintFormat")
    )
  }
}

tasks.register("checkStopship") {
  val cachedProjectDir = projectDir
  doLast {
    val excludedFiles = listOf(
      "build.gradle.kts",
      "lint.xml"
    )

    val excludedDirectories = listOf(
      ".idea"
    )

    val allowedExtensions = setOf("kt", "kts", "java", "xml")

    val allFiles = cachedProjectDir.walkTopDown()
      .onEnter { it.name != "build" || it.relativeTo(cachedProjectDir).path.contains("src") }
      .asSequence()
      .filter { it.isFile && it.extension in allowedExtensions }
      .filterNot {
        val path = it.relativeTo(cachedProjectDir).path
        excludedFiles.contains(path) || excludedDirectories.any { d -> path.startsWith(d) }
      }
      .toList()

    println("[STOPSHIP Check] There are ${allFiles.size} relevant files.")

    // Avoid a hidden buildscript dependency on kotlinx-coroutines. This task is
    // I/O-light compared with compilation and deterministic sequential scanning
    // also keeps configuration/build diagnostics reproducible across hosts.
    val stopshipFiles = allFiles.asSequence()
      .filter { file -> runCatching { file.readText().contains("STOPSHIP") }.getOrDefault(false) }
      .map { it.relativeTo(cachedProjectDir).path }
      .toSortedSet()

    if (stopshipFiles.isNotEmpty()) {
      throw GradleException("STOPSHIP found! Files: $stopshipFiles")
    }
  }
}
