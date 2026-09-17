#!/bin/sh
# RED backend entrypoint — unify Docker secrets (*_FILE) into plain env for Spring Boot.
#
# Why: postgres/minio images resolve *_FILE natively, but Spring Boot does NOT.
# Compose mounts every credential as /run/secrets/<name> and exports only the
# *_FILE pointer (see docker-compose.yml `secrets:` + `environment:`). This script
# materializes each pointer into the plain variable Spring actually reads
# (application.yml: ${DB_PASSWORD}, ${MONGO_PASSWORD}, ${REDIS_PASSWORD},
#  ${JWT_SECRET}, ${SFU_TICKET_SECRET}, ${RED_ADMIN_PASSWORD}, ${MINIO_PASSWORD},
#  ${TURN_SECRET}, plus SPRING_* relaxed-binding names) BEFORE the JVM boots.
# Values are never printed or logged.
#
# Wiring (one Dockerfile line, intentionally NOT changed here):
#   COPY entrypoint.sh /app/entrypoint.sh
#   ENTRYPOINT ["/app/entrypoint.sh"]
# Until then the file is also bind-mounted at /app/entrypoint.sh (compose `volumes:`)
# for inspection and manual testing.
set -eu

# file_env PLAIN FILEVAR [ALIAS...]
# Export PLAIN (+ each ALIAS) from the file named by $FILEVAR, only when PLAIN
# is currently unset/empty. Missing pointer, unreadable file, or empty file are
# warnings — never fatal — so `docker run` without secrets still boots defaults.
file_env() {
    plain="$1"; filevar="$2"; shift 2
    eval "cur=\${${plain}:-}"
    if [ -z "$cur" ]; then
        eval "f=\${${filevar}:-}"
        if [ -n "$f" ]; then
            if [ ! -r "$f" ]; then
                echo "[entrypoint] WARN: $filevar points to unreadable file: $f" >&2
            else
                cur="$(cat "$f")"
                if [ -z "$cur" ]; then
                    echo "[entrypoint] WARN: secret file is empty: $f" >&2
                else
                    export "$plain=$cur"
                fi
            fi
        fi
        eval "cur=\${${plain}:-}"
    fi
    # Aliases always mirror the effective plain value (explicit env still wins overall).
    for a in "$@"; do
        eval "acur=\${$a:-}"
        if [ -z "$acur" ] && [ -n "$cur" ]; then
            export "$a=$cur"
        fi
    done
    unset val acur cur f
}

file_env SPRING_DATASOURCE_PASSWORD SPRING_DATASOURCE_PASSWORD_FILE DB_PASSWORD
file_env MONGO_INITDB_ROOT_PASSWORD MONGO_PASSWORD_FILE MONGO_PASSWORD
file_env SPRING_DATA_REDIS_PASSWORD SPRING_DATA_REDIS_PASSWORD_FILE REDIS_PASSWORD
file_env JWT_SECRET JWT_SECRET_FILE
file_env SFU_TICKET_SECRET SFU_TICKET_SECRET_FILE
file_env RED_ADMIN_PASSWORD RED_ADMIN_PASSWORD_FILE
file_env MINIO_PASSWORD MINIO_PASSWORD_FILE
file_env TURN_SECRET TURN_SECRET_FILE

echo "[entrypoint] secrets materialized (*_FILE -> env); starting backend" >&2

if [ "$#" -eq 0 ]; then
    set -- java -jar /app/app.jar
fi
exec "$@"
