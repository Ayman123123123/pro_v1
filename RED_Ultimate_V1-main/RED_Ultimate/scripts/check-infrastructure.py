#!/usr/bin/env python3
"""Dependency-free regression checks for Docker, Nginx, TLS and Flyway wiring.

These checks do not pretend to replace `docker compose config`, `nginx -t`, or a
real container smoke test. They catch the exact high-impact regressions that can
otherwise survive generic source linting, and run even on hosts without Docker.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        errors.append(message)


def text(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


# ── Backend image / BuildKit ────────────────────────────────────────────────
dockerfile = text("backend-server/Dockerfile")
lines = dockerfile.splitlines()
# BuildKit cache mounts are an optimization, not a correctness requirement. This
# repo builds offline on a Windows host whose legacy Docker builder cannot fetch
# the `# syntax=` frontend from Docker Hub, so the mounts were removed on purpose
# (documented at the top of the Dockerfile). What must stay true is that *if* the
# cache mount is used, it targets the gradle user's home — never /root — because
# the build runs as user `gradle`.
uses_buildkit_cache = "--mount=type=cache" in dockerfile
if uses_buildkit_cache:
    check(bool(lines) and lines[0].strip().startswith("# syntax=docker/dockerfile:"),
          "backend Dockerfile uses RUN --mount but does not declare a modern Dockerfile frontend")
    check("--mount=type=cache,target=/home/gradle/.gradle" in dockerfile,
          "Gradle BuildKit cache must target /home/gradle/.gradle")
    check("sharing=locked" in dockerfile,
          "Gradle BuildKit cache must use sharing=locked for concurrent builds")
else:
    # Offline/legacy-builder path: the build must still run as the gradle user
    # and produce the same fixed artifact.
    check("USER gradle" in dockerfile,
          "backend build stage must drop to the gradle user even without BuildKit cache")
check("target=/root/.gradle" not in dockerfile,
      "Gradle image cache must not target /root while the build runs as user gradle")
check('ENTRYPOINT ["java", "-jar", "/app/app.jar"]' in dockerfile,
      "backend runtime must use exec-form java entrypoint for correct signal handling")
check("red-backend.jar" in dockerfile and "build/libs/*.jar" not in dockerfile,
      "backend image must copy one fixed bootJar, not an ambiguous wildcard")
for line_number, line in enumerate(lines, 1):
    if line.lstrip().startswith("COPY "):
        check(not re.search(r"(?:\|\||&&|[<>])", line),
              f"Dockerfile COPY cannot contain shell operators (line {line_number})")

# ── Nginx proxy correctness / trusted forwarding boundary ─────────────────
nginx = text("nginx.conf")
upstreams = re.findall(r"(?m)^\s*upstream\s+([^\s{]+)\s*\{", nginx)
check(bool(upstreams), "nginx.conf defines no upstreams")
for upstream in upstreams:
    check(bool(re.fullmatch(r"[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", upstream)),
          f"Nginx upstream identifier is not hostname-safe: {upstream}")
refs = re.findall(r"proxy_pass\s+http://([^/;\s]+)", nginx)
for ref in refs:
    host = ref.rsplit(":", 1)[0]
    check(host in upstreams or host in {"backend", "media-sfu", "admin-panel", "minio"},
          f"proxy_pass references undefined upstream/host: {ref}")
check("$proxy_add_x_forwarded_for" not in nginx and "$http_x_forwarded_for" not in nginx,
      "Nginx must replace untrusted X-Forwarded-For before backend trust is enabled")
check("proxy_set_header X-Forwarded-For $remote_addr;" in nginx,
      "Nginx is not setting a trusted client IP boundary")
check("location /storage" not in nginx,
      "Direct MinIO /storage proxy bypasses authenticated MediaAccessService")
check("proxy_read_timeout 3600s;" in nginx and "location /ws/" in nginx,
      "Canonical WebSocket route/idle timeout is missing")
check(nginx.count("{") == nginx.count("}"), "nginx.conf braces are unbalanced")
check(all("_" not in name for name in upstreams),
      "Nginx upstream names containing underscore can become invalid Host headers")

# ── Compose trust, TLS ownership and exposure ──────────────────────────────
compose = text("docker-compose.yml")
check("RED_TRUST_X_FORWARDED_FOR=true" in compose,
      "Compose must explicitly enable proxy trust only inside the private network")
check("JAVA_TOOL_OPTIONS=" in compose and "JAVA_OPTS=" not in compose,
      "Compose must use JAVA_TOOL_OPTIONS with an exec-form JVM entrypoint")
check("red-certs:/etc/ssl/red:ro" in compose,
      "Nginx private-key volume must be read-only")
check("identity-secrets:/run/secrets:ro" in compose and "identity-init:" in compose,
      "Host-owned authority keys must be staged for the non-root backend")
check("identity-init: { condition: service_completed_successfully }" in compose,
      "Backend must wait for authority-key staging")
check("certs-init: { condition: service_completed_successfully }" in compose,
      "Nginx must wait for successful TLS initialization")
check('127.0.0.1:${MINIO_API_PORT:-9000}:9000' in compose,
      "MinIO API must not bind to every host interface")
check("openssl pkey" in compose and "-checkend" in compose,
      "certs-init must validate key integrity and certificate expiry")
check("IP:$$TLS_SAN_IP" in compose and "TLS_SAN_IP=" in compose,
      "Development TLS certificate must include the configured LAN IP SAN")
check("apk add" not in compose and (ROOT / "infrastructure/tls-init.Dockerfile").is_file(),
      "TLS repair must not download packages during container startup")

# Every required interpolation must have a documented template key.
env_keys = set(re.findall(r"(?m)^([A-Z][A-Z0-9_]*)=", text(".env.example")))
required_vars = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*):\?", compose))
for missing in sorted(required_vars - env_keys):
    check(False, f"Required Compose variable is missing from .env.example: {missing}")

# ── Readiness and Actuator configuration ──────────────────────────────────
application = text("backend-server/src/main/resources/application.yml")
health = text("backend-server/src/main/kotlin/com/red/server/controllers/HealthController.kt")
check(re.search(r"(?m)^management:\s*$", application) is not None,
      "Actuator must be configured under top-level management")
check("  actuator:" not in application,
      "spring.actuator is not a valid Spring Boot Actuator configuration prefix")
check("HttpStatus.SERVICE_UNAVAILABLE" in health,
      "/health must return HTTP 503 when mandatory dependencies are down")
check('Document("ping", 1)' in health,
      "Mongo readiness must execute ping; reading MongoDatabase.name is lazy")
check("connection.close()" in health,
      "Redis readiness connection must be closed")
check("private-key-path: ${RED_IDENTITY_PRIVATE_KEY_PATH:}" in application,
      "Authority private-key environment variable is not bound to the property read by Spring")
check("public-key-path: ${RED_IDENTITY_PUBLIC_KEY_PATH:}" in application,
      "Authority public-key environment variable is not bound to the property read by Spring")

# ── Flyway migration identity ──────────────────────────────────────────────
migration_dir = ROOT / "backend-server/src/main/resources/db/migration"
versions: dict[str, list[str]] = {}
for migration in migration_dir.glob("V*__*.sql"):
    # Flyway treats a single underscore as a version separator, so V28_1 is
    # version 28.1 — a distinct, valid version, not a duplicate of V28.
    match = re.match(r"V(\d+(?:_\d+)*)__", migration.name)
    if match:
        versions.setdefault(match.group(1), []).append(migration.name)
check(bool(versions), "No versioned Flyway migrations found")
for version, names in sorted(versions.items()):
    check(len(names) == 1, f"Duplicate Flyway V{version}: {', '.join(names)}")
# Contiguity is NOT a Flyway requirement: gaps are legal and this repo has a
# deliberate one (V35 was renumbered to V38 in ab595581 after a parallel session
# had already applied its own V35, which would have caused a checksum conflict).
# Enforcing contiguity here failed the whole quality gate for a healthy schema.
# What actually breaks startup is a duplicate version, checked above.
if versions:
    majors = sorted({int(v.split("_")[0]) for v in versions})
    check(majors[0] == 1, "Flyway migrations must start at V1")

if errors:
    print(f"Infrastructure regression checks: {checks - len(errors)}/{checks} passed")
    for issue in errors:
        print(f"  FAIL: {issue}")
    sys.exit(1)

print(f"Infrastructure regression checks: {checks}/{checks} passed")
