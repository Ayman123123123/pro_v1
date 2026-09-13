# Applications

## Canonical Android app

`red-app/` is the active Gradle `:app` module. It contains the RED identity flow, encrypted messaging, groups, stories, media, notifications and WebRTC calls.

Build it with:

```bash
./gradlew :app:assembleDebug \
  -PRED_SERVER_URL=http://SERVER_IP:8088 \
  --dependency-verification strict
```

## Legacy sources

`app/`, `core/`, `lib/` and inactive feature directories are reference material outside the canonical build graph. Do not add new product code there. Changes needed by the product belong in `red-app/` or the backend.

## Validation

Run unit tests for the relevant module, then the dashboard checks and Compose configuration validation. A feature is not complete until the applicable build and runtime checks pass.
