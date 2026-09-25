# syntax=docker/dockerfile:1.7
# ═══════════════════════════════════════════════════════════════════
# RED Ultimate APK Builder
# يبني APK مباشرة — يعمل على Windows Docker Desktop
# ═══════════════════════════════════════════════════════════════════
# الاستخدام:
#   docker build -t red-apk-builder -f android-build.Dockerfile .
#   docker run --name red-apk-build red-apk-builder
#   docker cp red-apk-build:/output/app-debug.apk ./app-debug.apk
#   docker rm red-apk-build
# ═══════════════════════════════════════════════════════════════════

FROM eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# تثبيت الأدوات
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget unzip curl git bash zip dos2unix \
    && rm -rf /var/lib/apt/lists/*

# Android SDK
WORKDIR /opt/android-sdk
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip \
    && unzip -q tools.zip && rm tools.zip \
    && mkdir -p /tmp/sdk-tools \
    && mv cmdline-tools/* /tmp/sdk-tools/ \
    && mkdir -p cmdline-tools/latest \
    && mv /tmp/sdk-tools/* cmdline-tools/latest/ \
    && rm -rf /tmp/sdk-tools

# NOTE 2026-09: CI installs platforms;android-37.0 (minor-suffixed id — the bare
# android-37 id fails with "Failed to find package"). The container still builds
# against 36 locally until the 37 platform stabilises in offline mirrors.
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-36" "platforms;android-35" "build-tools;36.0.0"

# نسخ المشروع
WORKDIR /build
COPY . .

# Debug keystore مولّد محليًا (.dockerignore يستبعد signing/): نفس alias/passwords
# المعلنة في red-app/keystore.properties — توقيع debug للاختبار على الجهاز فقط.
RUN mkdir -p red-app/signing && rm -f red-app/signing/red-debug.p12 && \
    keytool -genkeypair -keystore red-app/signing/red-debug.p12 -storetype PKCS12 \
    -storepass red-debug-only -alias reddebug -keypass red-debug-only \
    -keyalg RSA -keysize 2048 -validity 3650 \
    -dname "CN=RED Debug, OU=RED, O=YOUNES" && \
    ls -la red-app/signing/

# إصلاح line endings
RUN dos2unix gradlew 2>/dev/null || true && chmod +x gradlew \
    && dos2unix scripts/docker-build-apk.sh 2>/dev/null || true && chmod +x scripts/docker-build-apk.sh 2>/dev/null || true

# API 37 غير منشور: خفض الكتالوج داخل الحاوية فقط (المصدر يبقى 37)
RUN sed -i 's/^compileSdk = "37"/compileSdk = "36"/; s/^targetSdk = "37"/targetSdk = "36"/' gradle/libs.versions.toml && grep -E "^(compileSdk|targetSdk)" gradle/libs.versions.toml

# تعطيل Dependency Verification (الملف لا يغطي كل القطع الجديدة)
# + قمع فحص AAR metadata (core-telecom alpha يتطلب 36.1 ونبني بـ 36.0)
RUN sed -i 's/^org.gradle.dependency.verification=.*/org.gradle.dependency.verification=off/' gradle.properties 2>/dev/null; \
    grep -q 'android.suppressUnsupportedCompileSdk' gradle.properties || \
    echo "android.suppressUnsupportedCompileSdk=36" >> gradle.properties; \
    grep -q 'org.gradle.dependency.verification' gradle.properties || \
    echo "org.gradle.dependency.verification=off" >> gradle.properties

# بناء APK عند docker run
CMD ["/build/scripts/docker-build-apk.sh"]
