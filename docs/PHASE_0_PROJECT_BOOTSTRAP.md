# Phase 0 — Project Bootstrap

**Status:** Implementation complete, but **not build-verified**. The required build is currently blocked by the execution environment; see the verification record below. Per the delivery rule, Phase 0 is not marked complete until that command succeeds.

## Scope completed

- Created a new root Android project named **NOVA Connect**.
- Set the Android package/application ID to `com.nova.connect`.
- Configured Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL, and a Gradle
  version catalog (`gradle/libs.versions.toml`).
- Added the requested initial multi-module graph:
  - `app`
  - `core:common`, `core:designsystem`, `core:network`, `core:database`,
    `core:security`, `core:analytics`
  - `domain`, `data`
  - `feature:onboarding`, `feature:authentication`, `feature:chat`, `feature:calls`,
    `feature:communities`, `feature:profile`, `feature:settings`
- Added a tracked Gradle wrapper and an Android-focused `.gitignore` policy.
- Added a minimal, real Compose launcher activity. It is intentionally a bootstrap
  surface, not a local messaging demo or a fake production flow.
- Documented the module direction, prerequisites, and build command in the README.

## Deliberately deferred

The NOVA visual system, Hilt, repositories/use cases, Room, networking, NestJS,
PostgreSQL, Socket.IO, authentication, realtime events, and deployment are not claimed
by Phase 0. They remain scoped to their named later phases.

## Build contract

Run from the repository root:

```bash
./gradlew assembleDebug
```

The build requires JDK 17 plus Android SDK Platform 35 / Build Tools 35.x. A successful
run produces `app/build/outputs/apk/debug/app-debug.apk`.

## Verification record — 2026-09-24 UTC

| Check | Result |
| --- | --- |
| Module/build contract inspection | **Passed** — all 16 requested modules have Gradle build files; each library has a manifest; the app ID and namespace are `com.nova.connect`; the version catalog and wrapper are present. |
| XML parsing | **Passed** — bootstrap manifests and resources are well-formed. |
| `git diff --check` | **Passed** — no whitespace errors. |
| `./gradlew assembleDebug --stacktrace` | **Blocked before Gradle starts** — exit code `1`: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` |

The sandbox currently has neither a Java runtime nor Android SDK installation available.
Network access needed to install them was also unavailable during this run. Install JDK 17
and Android SDK Platform 35 / Build Tools 35.x (or make an existing installation available
through `JAVA_HOME` and `ANDROID_HOME`), then rerun the required command.

Phase 0 **must not be represented as build-verified or complete** until
`./gradlew assembleDebug` exits successfully.
