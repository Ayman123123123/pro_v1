# syntax=docker/dockerfile:1.7
# Root CI image: compile and test the canonical RED backend with the shared protocol.
FROM gradle:8.12-jdk21 AS builder
WORKDIR /build
COPY RED_Ultimate/backend-server/ backend-server/
COPY RED_Ultimate/shared-proto/ shared-proto/
WORKDIR /build/backend-server
RUN --mount=type=cache,target=/home/gradle/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/home/gradle/.gradle/wrapper,sharing=locked \
    gradle clean build --no-daemon > /tmp/gradle-build.log 2>&1 \
    || (echo '=== RED_BACKEND_GRADLE_FAILURE ==='; tail -n 120 /tmp/gradle-build.log; exit 1)

FROM ghcr.io/cirruslabs/android-sdk:35 AS android-builder
ARG RED_SERVER_URL=http://127.0.0.1
ARG RED_TARGET_ABI=arm64-v8a
WORKDIR /build/RED_Ultimate
COPY RED_Ultimate/build.gradle.kts RED_Ultimate/settings.gradle.kts RED_Ultimate/gradle.properties RED_Ultimate/gradlew ./
COPY RED_Ultimate/gradle/ gradle/
COPY RED_Ultimate/local-maven/ local-maven/
COPY RED_Ultimate/build-logic/ build-logic/
COPY RED_Ultimate/wire-handler/ wire-handler/
COPY RED_Ultimate/red-app/ red-app/
COPY RED_Ultimate/shared-proto/ shared-proto/
# المشروع يطلب compileSdk=37 وقاعدة الصورة تحمل 35 فقط — نثبّت platform 37 + build-tools 36
# استباقياً حتى لا يضطر AGP للتنزيل أثناء البناء (شبكة الهدف متقلبة). المحاولة محمية:
# إن غاب sdkmanager من الصورة نكمل ونعتمد على التنزيل التلقائي.
RUN (command -v sdkmanager >/dev/null 2>&1 \
      && yes | sdkmanager --licenses >/dev/null 2>&1 \
      && sdkmanager "platforms;android-37" "build-tools;36.0.0") \
    || echo "sdkmanager unavailable — relying on AGP auto-download"
RUN --mount=type=cache,target=/root/.gradle/caches,sharing=locked \
    --mount=type=cache,target=/root/.gradle/wrapper,sharing=locked \
    sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && case "$RED_SERVER_URL" in http://*|https://*) ;; *) echo 'RED_SERVER_URL must be an absolute HTTP(S) URL' >&2; exit 64 ;; esac \
    && sh ./gradlew \
       -Dorg.gradle.jvmargs="-Xmx3g -Xms256m -XX:MaxMetaspaceSize=768m" \
       -Dkotlin.daemon.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m" \
       :app:testDebugUnitTest :app:assembleDebug -PRED_SERVER_URL="$RED_SERVER_URL" -PRED_TARGET_ABI="$RED_TARGET_ABI" -PRED_SKIP_BUILD_LOGIC=true \
       --dependency-verification strict --no-configuration-cache --no-daemon > /tmp/android-build.log 2>&1 \
    || (echo '=== RED_ANDROID_GRADLE_FAILURE ==='; tail -n 160 /tmp/android-build.log; exit 1)

# Lightweight local export target: builds only Android and writes the APK directly to --output.
FROM scratch AS android-artifact
COPY --from=android-builder /build/RED_Ultimate/red-app/build/outputs/apk/debug/*.apk /red-app-debug.apk

FROM node:22-alpine AS admin-builder
WORKDIR /build/admin
COPY RED_Ultimate/admin_dashboard/package.json RED_Ultimate/admin_dashboard/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY RED_Ultimate/admin_dashboard/ ./
RUN npm run build

FROM node:22-bookworm AS sfu-check
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 build-essential pkg-config \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build/sfu
COPY RED_Ultimate/media-sfu/package.json RED_Ultimate/media-sfu/package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund
COPY RED_Ultimate/media-sfu/server.js ./
RUN npm run check && touch /tmp/red-sfu-check-ok

FROM andrius/asterisk AS pstn-check
COPY RED_Ultimate/pstn-asterisk/extensions.conf /etc/asterisk/extensions.conf
COPY RED_Ultimate/pstn-asterisk/docker-entrypoint.sh /usr/local/bin/red-asterisk-entrypoint
COPY RED_Ultimate/scripts/local-first-run.sh /tmp/local-first-run.sh
# Normalize shell files defensively because Windows worktrees may already contain CRLF
# before .gitattributes is applied.
RUN sed -i 's/\r$//' /tmp/local-first-run.sh /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/extensions.conf \
    && sh -n /tmp/local-first-run.sh \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint \
    && AMI_PASSWORD=Ci_safe-secret DINSTAR_IP=192.168.11.1 \
       ASTERISK_CONFIG_DIR=/tmp/red-asterisk RED_ASTERISK_CONFIG_ONLY=1 \
       /usr/local/bin/red-asterisk-entrypoint \
    && grep -q 'secret = Ci_safe-secret' /tmp/red-asterisk/manager.conf \
    && grep -q 'contact=sip:192.168.11.1:5060' /tmp/red-asterisk/pjsip.conf \
    && ! grep -q 'webrtc-client' /tmp/red-asterisk/pjsip.conf \
    && touch /tmp/red-pstn-check-ok

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /build/backend-server/build/libs/*.jar app.jar
COPY --from=android-builder /build/RED_Ultimate/red-app/build/outputs/apk/debug/*.apk /opt/red-app-debug.apk
COPY --from=admin-builder /build/admin/dist /opt/red-admin-dashboard
COPY --from=sfu-check /tmp/red-sfu-check-ok /opt/verification/red-sfu-check-ok
COPY --from=pstn-check /tmp/red-pstn-check-ok /opt/verification/red-pstn-check-ok
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD curl -f http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
