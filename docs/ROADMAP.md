# NOVA Connect — Delivery Roadmap

The implementation follows the requested phase order and uses this delivery loop for
**one phase at a time**:

```text
PLAN → IMPLEMENT → BUILD → TEST → FIX → VERIFY → DOCUMENT → REPORT
```

## Current position

- **Phase 0 — Project Bootstrap:** implementation complete; build verification required.
- **Phase 1 — NOVA Design System:** not started.

## Guardrails

- A phase is not marked complete merely because it compiles.
- Android Room will be a local UI source of truth in its later phase; it will never be
  represented as a replacement for the central PostgreSQL server database.
- A Socket.IO connection will not be represented as durable storage, and FCM will not be
  represented as a replacement for realtime delivery.
- Production secrets, private signing keys, and production credentials must never be
  embedded in the APK or committed to the repository.
- End-to-end encryption is a distinct future phase and will use a reviewed protocol or
  library rather than a custom cryptographic design.
