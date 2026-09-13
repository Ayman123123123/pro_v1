#!/usr/bin/env bash
# RED backend-server — بناء منفصل عن بناء Android (انظر settings.gradle.kts).
# لا تُبنَ الواجهة والخادم معًا: لكلٍّ منهما Gradle و-toolchain مختلفان.
#
# الاستعمال:
#   ./scripts/build-backend.sh            # bootJar بالمهام الافتراضية
#   BACKEND_TASKS=build ./scripts/build-backend.sh   # بناء كامل مع الاختبارات
#   ./scripts/build-backend.sh --info     # تُمرَّر أي وسائط إضافية لـ Gradle
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/backend-server"
exec ./gradlew ${BACKEND_TASKS:-bootJar} "$@"
