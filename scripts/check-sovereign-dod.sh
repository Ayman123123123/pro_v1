#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════
# حارس السيادة — يمنع عودة النطاقات الملغية (2026-09-15، إكمال أسطوري).
# يُشغَّل في CI (job sovereign-dod) وقبل أي دمج. خروج 0 = نظيف.
#
# القواعد:
#  1) صفر Firebase/FCM في الكود المشحون (مرفوض من المالك — كل شيء محلي).
#  2) صفر نطاق PSTN/Dinstar/SIP/USSD/SMPP في الكود المشحون (ملغي مرحلة 8).
#  3) ممنوع ملفات جديدة بأسماء dinstar/pstn/yemen/asterisk خارج الأرشيف.
#
# مناطق التاريخ المعفاة عمدًا (موثقة لا منسية):
#  - الأرشيف/ ، docs/الأرشيف/ ، docs/archive/ ، red-app/.../_archive/ (تاريخ محفوظ)
#  - backend-server/.../db/migration/ (سلسلة Flyway مجمّدة؛ الإغلاق V52+V53)
#  - سطور provenance الأربعة (تعليقات «حُذف في المرحلة 8») — مثبتة أدناه نصًا
#  - android.telecom في AndroidManifest (واجهة نظام للرنين — ليست PSTN)
#  - استثناء 2026-09-24: docs/الأرشيف/telecom-removed-2026-09-24 تاريخ إزالة التيليكوم — وإلا الأرشيف نفسه يُفشل الحارس
# ═══════════════════════════════════════════════════════════════════════
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
P="$ROOT/RED_Ultimate_V1-main/RED_Ultimate"
FAIL=0

say()  { printf '%s\n' "$*"; }
fail() { say "❌ DoD: $*"; FAIL=1; }

# ── 1) Firebase/FCM: صفر مطلق في الكود المشحون ──────────────────────────
# استثناء موثق 2026-09-15: younes_icon_master.png أيقونة مستخدمة (TopBar) — تطابق fcm
# هو بايتات IDAT مضغوطة عرضية + مقطعا tEXt:date فقط، لا FCM حقيقي (فُحصت البنية بايتًا ببايت).
FIREBASE_HITS=$(grep -rni -E "firebase|fcm[^a-z]|googleapis\.com.*fcm|google-services" \
  "$P/backend-server/src" "$P/red-app/src" "$P/admin_dashboard/src" \
  "$P/media-sfu" "$P/shared-proto" 2>/dev/null \
  | grep -v "/_archive/" | grep -v "db/migration" | grep -v "docs/الأرشيف/" \
  | grep -v "res/drawable/younes_icon_master.png" \
  | grep -v "younes_icon_clean_pro.png" | grep -v "younes_icon_ultimate.png" \
  | grep -v "younes_icon_8k_new.png" | grep -v "mipmap-.*ic_launcher\.png" \
  || true)
if [ -n "$FIREBASE_HITS" ]; then
  fail "Firebase/FCM tokens in shipped code:"; say "$FIREBASE_HITS"
fi

# ── 2) النطاق الهاتفي: صفر باستثناء provenance المثبت ───────────────────
# ملاحظة: clip مُسقط عمدًا من النمط — يتصادم كليًا مع Compose Modifier.clip؛
# CLIP الهاتفية لم تظهر أصلًا إلا في هجرات مجمّدة معفاة.
# SIP_REALM معفى نصًا: اسم متغير قديم لقيمة realm خادم TURN الذاتي — لا SIP فيه.
TELEPHONY_HITS=$(grep -rni -E "pstn|dinstar|telecom_gateways|gateway_(sim|port|route|config|health|operations)|msisdn|\bsip\b|\bussd\b|\bsmpp\b|\bgsm\b" \
  "$P/backend-server/src" "$P/red-app/src" "$P/admin_dashboard/src" \
  "$P/media-sfu" "$P/shared-proto" \
  "$P/docker-compose.yml" "$P/docker-compose.prod.yml" "$P/.env.example" 2>/dev/null \
  | grep -v "/_archive/" | grep -v "db/migration" | grep -v "docs/الأرشيف/" \
  | grep -v "android\.telecom" \
  | grep -v "SIP_REALM" \
  | grep -v "younes_icon_clean_pro.png" | grep -v "younes_icon_ultimate.png" \
  | grep -v "younes_icon_8k_new.png" | grep -v "mipmap-.*ic_launcher\.png" \
  | grep -v -F "purge stale PSTN keys" \
  | grep -v -F "اليومي PSTN" \
  | grep -v -F "PSTN اليومي" \
  | grep -v -F "كان DINSTAR (محذوف)" \
  | grep -v -F "DINSTAR/PSTN الميتة" \
  || true)
if [ -n "$TELEPHONY_HITS" ]; then
  fail "Telephony-scope tokens in shipped code:"; say "$TELEPHONY_HITS"
fi

# ── 3) أسماء ملفات النطاق الملغي خارج الأرشيف ────────────────────────────
# -z: فصل NUL يمنع تقتبيس git للمسارات العربية (core.quotePath يتجاهل الـTTY).
# db/migration و docs/archive و docs/الأرشيف تاريخ مجمّد معفى (الأول سلسلة Flyway، الثاني أرشيف دمج، الثالث أرشيف إزالة التيليكوم 2026-09-24).
NAME_HITS=$(cd "$ROOT" && git ls-files -z | tr '\0' '\n' \
  | grep -vi "^الأرشيف/" | grep -v "docs/archive/" | grep -v "docs/الأرشيف/" | grep -v "db/migration/" \
  | grep -i "dinstar\|pstn\|yemen\|asterisk" \
  | grep -v -i "check-sovereign-dod" || true)
if [ -n "$NAME_HITS" ]; then
  fail "Cancelled-scope filenames outside الأرشيف/:"; say "$NAME_HITS"
fi

if [ "$FAIL" -eq 0 ]; then say "✅ DoD sovereign guard: clean."; fi
exit "$FAIL"
