# Runtime-only backend image from the prebuilt app.jar.
# Used because the Kotlin source in this snapshot does not compile
# (DinstarHardwareService.kt has hundreds of unresolved references).
# Mirrors the `runtime` stage of backend-server/Dockerfile.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN if ! getent group redserver >/dev/null 2>&1; then groupadd --system --gid 10001 redserver; fi \
    && if ! id redserver >/dev/null 2>&1; then useradd --system --uid 10001 --gid redserver --home-dir /app --shell /usr/sbin/nologin redserver; fi \
    && mkdir -p /app/logs \
    && chown -R redserver:redserver /app

COPY --chown=redserver:redserver app.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+UseContainerSupport"

USER redserver
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=180s \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
