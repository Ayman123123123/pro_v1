# RED Ultimate (red-app) — Security & Correctness Audit

**Audited project:** `C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\red-app`
**Scope:** all 176 handwritten Kotlin files (src/main + src/test; 4 generated KSP impls and the protobuf-generated `RedProtos` are excluded — they have no source file in this repo, they are produced at build time).
**App identity:** `applicationId=com.red.sovereign`, `versionName=1.0.0-alpha01`.
**Date:** 2026-08-13

---

## Executive summary

The app is a functional prototype aimed at LAN/small-network use (`RED_SERVER_URL` defaults to `http://192.168.0.181:8088`, self-hosted backend). Crypto foundations are genuinely good: Signal Protocol (libsignal) E2EE, SQLCipher databases, Keystore-backed `SecureStore`, AES-256-GCM attachment/voice/draft/backup encryption, and a per-build TLS pinning mechanism.

The **two systemic weaknesses** are configuration defaults rather than code defects:

1. Every transport defaults to cleartext in the build that is actually runnable (`debug`), and TLS pinning is **never enabled for either variant** because the default `RED_TLS_PINS` Gradle property is empty.
2. The `release` variant cannot be signed/installed (no `signingConfig`), so there is currently no production build at all — everything in practice runs on the cleartext debug path.

Beyond that, the finding that is independent of the deployment configuration (i.e. it leaks credentials *even when* pinning is correctly configured elsewhere) is the Dinstar gateway: its access token is sent over a `ws://` WebSocket built with a plain, unpinned `OkHttpClient`.

Severity counts: **2 HIGH**, **6 MEDIUM**, **9 LOW/informational**.

---

## HIGH

### H1. All default-network traffic is cleartext HTTP/WS and TLS pinning never activates

**Files:** `...\red-app\build.gradle.kts:11-12, 47, 52`

```kotlin
RED_SERVER_URL    = "http://192.168.0.181:8088"   // :11 — HTTP
RED_TLS_PINS      = ""                             // :12 — empty → pinning disabled
// debug: cleartextTrafficPermitted = true        // :47
// release: cleartextTrafficPermitted = false     // :52
```

* `YounesApplication.kt:25-27` only calls `provisionPins()` when `BuildConfig.RED_TLS_PINS.isNotBlank()`. With the default `""`, no pins are ever loaded, and `CertificatePinner.isEnabled = !BuildConfig.DEBUG` stays passive for debug builds.
* The `debug` variant (the only variant with a usable signing config — see H2) explicitly permits cleartext, **and the default URL is HTTP anyway**. Client-side cleartext ban in release is moot because release cannot be built/installed.
* **Impact:** on the only runnable build, every login (`POST /api/auth/...`), every REST call (`AuthorizedApiClient`), the main message socket, media uploads (MinIO), and the Dinstar WS handshake travel over plaintext. On a shared Wi-Fi/LAN an attacker who can sniff (rogue AP, ARP spoof) captures credentials and access tokens.

**Fix:** make the SDK default `https://`; ship real `RED_TLS_PINS` values at build time; make `CertificatePinner.provisionPins()` refuse to build in `release` when pins are blank (fail-closed), and have `ServerEndpoint.initialize` reject `http` URLs unless `BuildConfig.DEBUG` and an explicit opt-in flag is set.

### H2. Release variant has no signingConfig — the shipped "production" path is unsigned

**File:** `...\red-app\build.gradle.kts:50-54`

The `release` buildType declares neither `signingConfig` nor `minifyEnabled`; the only committed credentials are the **debug-only** `redLocalDebug` key (`build.gradle.kts:33-36`). The resulting release APK is unsigned and cannot be installed on Android.

**Impact:** there is no legitimate production artifact; combined with H1, the effective deployment surface of this codebase today is the cleartext debug path.

**Fix:** wire the `release` signingConfig to a Keystore provisioned outside the repo (env var / CI), or fail the `assembleRelease` task with a clear message until a signing config exists.

---

## MEDIUM

### M1. Dinstar access token sent over unpinned `ws://` WebSocket (independent of H1)

**Files:** `...\features\dinstar\DinstarWebSocketBridge.kt:24-26, 37-39`; caller `...\features\dinstar\DinstarViewModel.kt` (passes `tokens.accessToken`).

```kotlin
// DinstarWebSocketBridge
OkHttpClient.Builder().pingInterval(...).build()          // plain client, NO SecureOkHttpClient, NO pinning
val wsUrl = backendUrl.replace("http", "ws")               // → ws:// when backend is http
Authorization: Bearer $token                                // access token on the wire
```

This is the one path that escapes the app's own hardening story: even on a deployment where REST is correctly pinned (release + pins provisioned), the Dinstar bridge still uses a bare `OkHttpClient`, downgrades `http→ws` (`https→wss` never happens), and the endpoint scheme is not validated. The bearer token therefore leaks over cleartext regardless of pin configuration.

**Fix:** build the WS client from `SecureOkHttpClient`/`CertificatePinner`; enforce `wss` (reject `ws`) via the same policy; keep the token in an Authenticator, not a request header.

### M2. Privacy screen is cosmetic — user's privacy choices are silently discarded

**Files:** `...\features\privacy\PrivacySettingsScreen.kt`; call site `...\ui\RedDashboard.kt:312`.

`RedDashboard.kt:312` invokes `PrivacySettingsScreen()` with no `settings` state and no `onSettingChange` callback. The screen's toggles use `PrivacyLevel.EVERYONE` defaults, are never persisted, and are never synchronized anywhere (the REAL privacy settings live in `settings/SettingsScreen.kt` via the separate `SettingsViewModel`). The claim shown to users ("sovereign privacy settings never leave device") implies a working control that does nothing.

**Fix:** wire this screen to `SettingsRuntime`/`SettingsViewModel`, or delete it to avoid presenting a functional-looking but inert surface.

### M3. Identity directory trust-on-first-use (TOFU) with silent key-change acceptance

**File:** `...\crypto\IdentityDirectoryApi.kt`

The first registered identity key for a contact is trusted unconditionally; a later differing key is accepted without any user-visible verification / signal-changed flow. Within a single registration this mirrors Signal's model, but combined with the cleartext transport (H1) it means a MITM on first contact is undetectable.

**Fix:** surface key-changed events and a "verify safety number" interaction (the app already contains `crypto/SafetyQrScanner.kt` / `SafetyViewModel.kt` — thread the verification path through identity changes).

### M4. Call recording consent hardcoded

**File:** `...\calls\YounesCallService.kt` (consent flag ~line 393, and per-party consent fields around `:67`)

Call recording is enabled with consent hardcoded to `true`; there is no per-call user consent gate, no announcement tone, and the redundant `consent` field is never surfaced. Recording another party without consent is a legal/privacy exposure in most jurisdictions.

**Fix:** drive consent from `SettingsRuntime` (persisted user choice) plus an explicit per-call confirmation, and/or emit an audible beep on start.

### M5. Burn ("self-destruct") timer can be defeated and is not multi-device safe

**File:** `...\core\delivery\BurnManager.kt`

`BurnManager`'s completed-burn job is hooked to an observer scope that can be cancelled with the surrounding lifecycle (`scope.cancel()`), and burn only acts on the local device — it cannot revoke copies already materialized on the peer. Burn-after-read therefore silently degrades to "delete locally" if the host is destroyed or purged.

**Fix:** make the burn sweep independent of the calling scope and server-acknowledged (the device endpoint already exists); document that local-only disassembly guarantees are not cross-device.

### M6. Voice/attachment/media plaintext lands in app-private cache before/while encrypting-or-decrypting

**Files:** `...\calls\CallRecordingManager.kt` (audio written to a `*.m4a.enc`-named file **before** the cipher at stop), `...\media\MediaApi.kt:50-64` (`story_media`, plaintext decrypted), `...\media\EncryptedAttachment.kt:109-110` (`decrypted_attachments`), `...\media\VoiceMessageViewModel.kt:100` (temp `.m4a`).

All are inside the app-private `cacheDir` (not world-readable), so this is not an app-permission leak. It is a forensic/theft exposure: if the device is rooted or the app data is extracted during the window in which plaintext exists, call audio / decrypted media are recoverable in the clear.

**Fix:** stream encryption directly to the destination (never write cleartext to a file), scrub temporary files, and consider `SQLCipher`/Keystore-backed at-rest encryption for the cached copies (note the design already does this for `local_history` — extend it to media).

---

## LOW / informational

### L1. Release-shipped TLS pins empty → `CertificatePinner.verify(...)` falls back to system trust
`...\security\CertificatePinner.kt` + `build.gradle.kts:12`. With `""` pins, verification is a pass-through. Informational when taken together with H1; list here because on its own (i.e. once H1's default URL is HTTPS) it merely means "no added pinning".

### L2. Dead deep links: `younes://space/...` and `younes://livestream/...`
`calls/ConferenceOverlay.kt:134`, `calls/LiveStreamViewerOverlay.kt:338` write these URIs to the clipboard, but the manifest has **no `younes` intent-filter** (grep confirmed) and `MainActivity.kt` (root package, not `ui/`) has no parser. Anyone receiving the invite cannot resolve it.

### L3. `hideLastSeen` is stored but never enforced
`settings/SettingsViewModel.kt:31,57,79` only read/write the SharedPreferences value; no code path sends it to the server (`grep hideLastSeen` — setter/storage only). Users toggle a setting that has no effect. (Contrast: `readReceipts` **is** enforced at `ui/RedDashboard.kt:988`.)

### L4. Remote wipe leaves the certificate-pin store out of the "golden rule" list
`security/RemoteAppWipe.kt` `STORES` lists all app SharedPreferences/Databases **except** the `younes_certificate_pins` prefs file (SecureStore-backed). The Keystore alias sweep `red.*` still destroys the key, making the orphaned ciphertext effectively unrecoverable — so this is a completeness/consistency note, not a data leak.

### L5. Global/local search is a dead limb
`core/database/FtsSearchManager.kt` creates the FTS5 table (`RedDatabase.kt:57,96`) but `indexMessage` is never called in production code (tests only). Local search falls back to byte-`LIKE` over the decrypted `local_history.encryptedPlaintext` column (`RedDao.kt:86-87,128-129`), which is fine on small histories but does not scale with intent.

### L6. Pending credentials held in memory
`auth/AuthViewModel.kt` keeps `pendingCredentials` (including password) until the session completes. It is erased on non-success paths; erase on success too and prefer `token`-only flows.

### L7. Call log "route"/status stored but recording privacy is per-conversation
Minor consistency gap in `CallLogEntity`/`CallLogCipher` — see M4 for the substantive issue.

### L8. Test-only sensitive helpers
`src/test/**` (e.g. `security/CertificatePinnerTest.kt`) exercise pin warnings/pass-through behavior — documented, not shipped in the APK.

### L9. `CommunitiesApi`/`EventsApi` use admin-namespaced endpoints (`/api/admin/content/events`)
Client-side this is just server ACL enforcement; flagging so the backend restrict those endpoints to elevated roles.

---

## What is done right (verify is preserved in future changes)

- **Signal Protocol E2EE** with prekey pool, per-device registration, group Sender-Keys (SenderKey) distribution, and membership-hash ratchet: `crypto/…`, `groups/GroupCryptoManager.kt:33-88`.
- **At-rest encryption:** SQLCipher on all Room DBs (`RedDatabase.kt`), Keystore-backed `SecureStore` (`core/SecureStore.kt`, alias `red.secure.<name>.v1`), AES-256-GCM attachments with SHA-256 integrity + size caps (`media/EncryptedAttachment.kt:99-140`), `EncryptedMediaCache` (AES-256-GCM, Keystore), `DraftsStore` on the same, `BackupManager` AES-256-GCM.
- **Path-traversal guards** on authenticated media (`MediaApi.kt:27,41,83`), extension allowlist, size limits.
- **Fails-closed SDP** fingerprint fallback (`calls/SfuSdpFactory.kt:19`), zeroed-out secret buffers after use (`EncryptedAttachment`, `VoiceMessageViewModel`).
- **Ticket-based SFU auth** via `SecureOkHttpClient`: `calls/SfuMediaClient.kt`.
- **Masked PII** in Dinstar telemetry (`IMSI/ICCID/number` masked) and presence (`PresenceInfo`).
- **Foreground-service notification routing** with dedicated channels and no world-readable output (`network/SovereignNotificationRouter.kt`).

---

## Coverage

All 176 handwritten Kotlin files reviewed. Summaries by package:

| Package | Verdict |
|---|---|
| `auth/` (AuthViewModel, TokenStore, DeviceKeyManager, AuthorizedApiClient, AuthApi, AuthModels, DevicesApi, PstnApi) | Mostly solid (L6). TokenStore logout residue previously reported; authorize-verify present |
| `calls/` (30 files: services, signaling, recording, mesh/SFU, receivers, overlays) | M4, M5-adjacent, M6, L2; SFU/SDP/mesh good |
| `contacts/`, `social/`, `stories/`, `features/communities`, `features/contacts`, `features/devices`, `features/explore`, `features/profile`, `features/media` | REST+UI, no new findings (excl. noted) |
| `crypto/` (Signal store, sessions, identity dir, prekey pool, protocol record cipher, ack/decrypt buses) | M3; core E2EE good |
| `groups/` (CryptoManager, ViewModel, Models) | Good (Sender-Key distribution) |
| `core/database/` (Entities, RedDao, RedDatabase, LocalRepository, FtsSearchManager) | L5; `local_history` plaintext decrypted in SQLCipher |
| `core/delivery/BurnManager.kt` | M5 |
| `core/` (RedConnectionService, RedWebSocketClient, MessageStore, ServerEndpoint, SecureStore, RichMessage, TypingEventBus, UuidV7, YounesId, YounesServerSignature, LocalServerDiscovery, RedQualityManager, RedSystemLinker, utils/*) | Reviewed; readReceipts gate at `RedDashboard.kt:988`; cleartext flow H1 |
| `media/` (Events/Polls/Sticker/Attachment/Voice/EncryptedMediaCache/voice panel) | M6; encryption good |
| `security/` (CertificatePinner, SecureOkHttpClient, AppLockScreen, DebugSecurityManager, RemoteAppWipe, SecurityHeaders) | H1 interplay, L1, L4 |
| `settings/` (SettingsViewModel+Runtime, SettingsScreen, DeviceSettingsViewModel) | L3; `SettingsRuntime` object at `SettingsViewModel.kt:106` |
| `features/dinstar/` (WebSocketBridge, ViewModel, Models) | M1 — highest standalone credential exposure |
| `features/privacy/PrivacySettingsScreen.kt` | M2 |
| `ui/`, `ui/theme`, `ui/screens`, `MainActivity.kt`, `YounesApplication.kt`, `groups/`, `stories/` | Scaffolding (`ui/screens/*` are placeholders pointing at `RedDashboard.kt`) and presentation only |
| `test/` (27 files) | Unit tests, not shipped; exercise pin/cipher paths |

Generated/excluded: `build/generated/**`, `build/kspCaches/**`, protobuf `RedProtos`.