# syntax=docker/dockerfile:1
# ══════════════════════════════════════════════════════════════════
# 🏗️ RED Ultimate — بناء سلسلة الأدوات داخل BuildKit (بلا JDK/SDK على المضيف)
#
# الاستخدام (عبر السكربت الجاهز):
#   scripts/build-android-local.ps1 -ServerIp 192.168.1.50
# ما يعادل:
#   docker build --target android-artifact --output type=local,dest=local-artifacts .
#
# المرحلة النهائية android-artifact تحتوي red-app-debug.apk فقط،
# فيصدّره BuildKit مباشرة إلى المضيف.
# ══════════════════════════════════════════════════════════════════

# ─── المرحلة 1: JDK 21 + Android SDK (platform 37 + أحدث build-tools) ───
FROM eclipse-temurin:21-jdk-jammy AS android-sdk
ARG ANDROID_CMDTOOLS=11076708
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p ${ANDROID_HOME}/cmdline-tools \
 && curl -fsSL -o /tmp/cmdtools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDTOOLS}_latest.zip" \
 && unzip -q /tmp/cmdtools.zip -d ${ANDROID_HOME}/cmdline-tools \
 && mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest \
 && rm /tmp/cmdtools.zip \
 && yes | sdkmanager --licenses >/dev/null \
 && sdkmanager "platforms;android-37" "platform-tools" \
 && BT="$(sdkmanager --list | grep -oE 'build-tools;3[0-9]+\.[0-9]+\.[0-9]+' | sort -uV | tail -1)" \
 && echo "Installing ${BT}" && sdkmanager "${BT}"

# ─── المرحلة 2: بناء APK (red-app هو :app حسب settings.gradle.kts) ───
FROM android-sdk AS android-build
ARG RED_SERVER_URL=http://192.168.1.50:8088
ARG RED_TARGET_ABI=arm64-v8a
WORKDIR /build
# السياق الأدنى أولاً لتحسين كاش الطبقات؛ local-maven يحمل libsignal المُتحقق منه مسبقاً
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY local-maven/ local-maven/
COPY shared-proto/ shared-proto/
COPY red-app/ red-app/
# ويندوز قد يحوّل نهايات الأسطر إلى CRLF — نعيدها إلى LF داخل الصورة فقط
RUN sed -i 's/\r$//' gradlew
RUN --mount=type=cache,target=/root/.gradle \
    bash gradlew :app:dependencies --configuration debugRuntimeClasspath \
      -PRED_SKIP_BUILD_LOGIC=true \
      "-Dorg.gradle.jvmargs=-Xmx6g -Xms512m -XX:MaxMetaspaceSize=1g" \
      --no-daemon >/dev/null || true
RUN --mount=type=cache,target=/root/.gradle \
    bash gradlew :app:assembleDebug \
      -PRED_SKIP_BUILD_LOGIC=true \
      "-PRED_SERVER_URL=${RED_SERVER_URL}" \
      "-PRED_TARGET_ABI=${RED_TARGET_ABI}" \
      "-Dorg.gradle.jvmargs=-Xmx6g -Xms512m -XX:MaxMetaspaceSize=1g" \
      --no-daemon --stacktrace \
 && mkdir -p /out \
 && cp red-app/build/outputs/apk/debug/app-debug.apk /out/red-app-debug.apk

# ─── المرحلة 3: سطح التصدير (APK فقط) ───
FROM scratch AS android-artifact
COPY --from=android-build /out/red-app-debug.apk /red-app-debug.apk
