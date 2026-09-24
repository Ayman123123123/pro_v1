# NOVA Connect

NOVA Connect is an Android messaging platform being built as a real internet-connected
client/server product. The Android client uses Kotlin, Jetpack Compose, Material 3,
Gradle Kotlin DSL, and a version catalog. Its application ID is `com.nova.connect`.

> **Current delivery: Phase 0 — Project Bootstrap.** The project deliberately contains
> no mock messaging path, local-only replacement backend, or production credentials.
> The server, authentication, persistence, realtime gateway, and product features are
> introduced in their respective later phases.

## Module layout

```text
app
core:common             core:designsystem       core:network
core:database           core:security           core:analytics
domain                  data
feature:onboarding      feature:authentication  feature:chat
feature:calls           feature:communities     feature:profile
feature:settings
```

The Phase 0 dependency direction is intentionally one-way:

```text
app → feature modules / data → domain → core
                         data → core
feature modules → domain / core:designsystem
```

This is a structural bootstrap, not the completion of the Clean Architecture or NOVA
Design System phases. The app launches a minimal Compose bootstrap surface only.

## Prerequisites

- JDK 17
- Android SDK Platform 35 and Build Tools 35.x
- An Android device or emulator for installation (not required for `assembleDebug`)

Set `ANDROID_HOME` (or add `sdk.dir=/path/to/android-sdk` to a local, ignored
`local.properties`) before building.

## Build

```bash
./gradlew assembleDebug
```

Expected debug APK after a successful build:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Documentation

- [Phase 0 bootstrap report](docs/PHASE_0_PROJECT_BOOTSTRAP.md)
- [Roadmap](docs/ROADMAP.md)

## Security baseline

No development or production secret is committed. The Android application has only the
baseline `INTERNET` and network-state permissions at this stage. A public HTTPS API,
WSS, PostgreSQL, Redis, object storage, and deployment secrets belong to subsequent
server and deployment phases; none are simulated by on-device storage.
