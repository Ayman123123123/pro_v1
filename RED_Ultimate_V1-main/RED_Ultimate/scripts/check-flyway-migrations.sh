#!/usr/bin/env sh
# Validate Flyway versioned migration names before a local or CI build.
set -eu

MIGRATIONS_DIR="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/../backend-server/src/main/resources/db/migration" && pwd)}"
[ -d "$MIGRATIONS_DIR" ] || {
  printf '%s\n' "Migration directory not found: $MIGRATIONS_DIR" >&2
  exit 64
}

seen_file="$(mktemp)"
trap 'rm -f "$seen_file"' EXIT HUP INT TERM
count=0

for file in "$MIGRATIONS_DIR"/V*__*.sql; do
  [ -f "$file" ] || continue
  name="$(basename "$file")"
  version="${name%%__*}"
  case "$name" in
    V[0-9]*__*.sql) ;;
    *)
      printf '%s\n' "Invalid Flyway migration filename: $name" >&2
      exit 65
      ;;
  esac
  # A version is numeric segments separated by underscores (for example V25_1).
  if ! printf '%s' "${version#V}" | grep -Eq '^[0-9]+(_[0-9]+)*$'; then
    printf '%s\n' "Invalid Flyway migration version: $name" >&2
    exit 65
  fi
  if grep -Fxq "$version" "$seen_file"; then
    printf '%s\n' "Duplicate Flyway migration version: $version" >&2
    exit 66
  fi
  printf '%s\n' "$version" >> "$seen_file"
  count=$((count + 1))
done

[ "$count" -gt 0 ] || {
  printf '%s\n' "No Flyway migrations found in: $MIGRATIONS_DIR" >&2
  exit 67
}
printf 'Flyway migration naming check passed: %s versioned migrations\n' "$count"
