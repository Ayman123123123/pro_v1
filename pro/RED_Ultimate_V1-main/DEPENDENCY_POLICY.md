# RED dependency policy — 2026-08-05

RED uses current stable maintenance lines, not untested major upgrades.

- Backend: Spring Boot 3.5.16 (latest 3.x maintenance line); Spring Boot 4.1 is deferred until integration tests pass.
- Backend Kotlin: 2.4.10.
- Android Kotlin/AGP remain pinned to the verified repository catalog until an APK build can update verification metadata.
- MinIO Java: 8.6.0 (latest compatible 8.x); 9.x is deferred because it is a major API transition.
- libsignal Android remains pinned to 0.86.5 because the persistent store implementation was matched to that exact tag.
- Node: 22 LTS; React 19; Vite 7; mediasoup 3.24.0.

Every update must pass backend tests, Android compilation, dashboard production build and Docker integration before merge. “Newest” alone is not acceptance criteria; security support, API compatibility and reproducibility are.
