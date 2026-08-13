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
    && mkdir -p cmdline-tools/latest \
    && mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-37" "platforms;android-35" "build-tools;36.0.0"

# نسخ المشروع
WORKDIR /build
COPY . .

# إصلاح line endings
RUN dos2unix gradlew 2>/dev/null || true && chmod +x gradlew \
    && dos2unix scripts/docker-build-apk.sh 2>/dev/null || true && chmod +x scripts/docker-build-apk.sh 2>/dev/null || true

# تعطيل Dependency Verification (الملف لا يغطي كل القطع الجديدة)
RUN sed -i 's/^org.gradle.dependency.verification=.*/org.gradle.dependency.verification=off/' gradle.properties 2>/dev/null; \
    grep -q 'org.gradle.dependency.verification' gradle.properties || \
    echo "org.gradle.dependency.verification=off" >> gradle.properties

# بناء APK عند docker run
CMD ["/build/scripts/docker-build-apk.sh"]
