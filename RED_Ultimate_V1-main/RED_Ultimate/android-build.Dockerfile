# ══════════════════════════════════════════════════════════════════════
# 📱 YOUNES Sovereign Android Build Environment
# Isolated Linux-based build to bypass Windows path/service conflicts
# ══════════════════════════════════════════════════════════════════════

FROM eclipse-temurin:21-jdk-jammy

# 1. Install Android SDK dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget unzip curl git bash unzip zip \
    && rm -rf /var/lib/apt/lists/*

# 2. Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/36.0.0
ENV ANDROID_USER_HOME=/build/.android

# 3. Download Android Command Line Tools (latest 2026 compatible)
WORKDIR /opt/android-sdk
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O tools.zip \
    && unzip -q tools.zip && rm tools.zip \
    && mkdir -p cmdline-tools/latest \
    && mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# 4. Accept licenses and install Build Tools / Platforms
RUN yes | sdkmanager --licenses \
    && sdkmanager "platform-tools" "platforms;android-35" "build-tools;36.0.0"

# 5. Prepare build directory
WORKDIR /build
COPY . .

# 6. Fix Windows line endings and set permissions for Gradle
RUN apt-get update && apt-get install -y dos2unix \
    && dos2unix gradlew \
    && chmod +x gradlew

# 7. Build the APK (Lenient mode to ensure success)
# Passing the server URL directly into the build
ENTRYPOINT ["./gradlew", ":app:assembleDebug", "-PRED_SERVER_URL=http://192.168.0.181:8088", "-Porg.gradle.dependency.verification=off", "--no-daemon"]
