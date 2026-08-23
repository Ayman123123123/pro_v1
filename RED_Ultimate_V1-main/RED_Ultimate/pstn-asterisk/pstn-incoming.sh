#!/bin/sh
# ============================================================
# RED Sovereign - dialplan-to-backend bridge for DINSTAR incoming calls.
# Invoked by extensions.conf System() with positional args so we avoid
# nested JSON quoting entirely. Env: PSTN_INTERNAL_SECRET (exported by
# docker-entrypoint before asterisk starts).
# Usage: pstn-incoming.sh "<caller>" "<called>" "<channel>"
# ============================================================

CALLER="$1"
CALLED="$2"
CHANNEL="$3"

if [ -z "$CALLER" ] || [ -z "$CHANNEL" ]; then
  logger -t pstn-incoming "missing args caller or channel"
  exit 1
fi

# IP البوابة يُستخرج في الخادم من اسم القناة (DinstarEventListener.extractGatewayHost)
# نرسله فارغاً هنا والخادم يحلله تلقائياً
GATEWAY_HOST=""

PAYLOAD=$(printf '{"caller":"%s","called":"%s","channel":"%s","gatewayHost":"%s"}' "$CALLER" "$CALLED" "$CHANNEL" "$GATEWAY_HOST")

# جرّب backend أولاً (الاسم الصحيح في docker-compose)، ثم fallback لـ red-backend
RESPONSE=$(curl -sS -m 5 -w "\n%{http_code}" -X POST http://backend:8080/api/internal/pstn/incoming \
  -H 'Content-Type: application/json' \
  -H "X-Internal-Secret: ${PSTN_INTERNAL_SECRET}" \
  --data-binary "$PAYLOAD" 2>&1)
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if echo "$HTTP_CODE" | grep -q "^2"; then
  exit 0
fi

# Fallback: جرّب red-backend كاسم ثانوي
RESPONSE2=$(curl -sS -m 5 -w "\n%{http_code}" -X POST http://red-backend:8080/api/internal/pstn/incoming \
  -H 'Content-Type: application/json' \
  -H "X-Internal-Secret: ${PSTN_INTERNAL_SECRET}" \
  --data-binary "$PAYLOAD" 2>&1)
HTTP_CODE2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | sed '$d')

if echo "$HTTP_CODE2" | grep -q "^2"; then
  exit 0
fi

logger -t pstn-incoming "POST failed: backend=$HTTP_CODE ($BODY) red-backend=$HTTP_CODE2 ($BODY2) caller=$CALLER channel=$CHANNEL"
exit 1
