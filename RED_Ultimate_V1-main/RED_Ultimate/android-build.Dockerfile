# syntax=docker/dockerfile:1.7
# SDK image for building :app (red-app) on Windows when local Gradle/Maven Central is blocked.
# Installs API 37 to match libs.versions.toml compileSdk.
FROM eclipse-temurin:21-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

RUN apt-get update && apt-get install -y --no-install-recommends \
        wget unzip curl git bash zip dos2unix \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/android-sdk
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip \
    && unzip -q tools.zip && rm tools.zip \
    && mkdir -p cmdline-tools/latest \
    && mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-37" "platforms;android-35" "build-tools;36.0.0"

WORKDIR /src
CMD ["bash"]
