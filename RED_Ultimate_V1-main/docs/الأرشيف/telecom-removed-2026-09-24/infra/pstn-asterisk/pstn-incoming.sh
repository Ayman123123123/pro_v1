#!/bin/sh
# ============================================================
# RED Sovereign - dialplan-to-backend bridge for DINSTAR incoming calls.
# Invoked by extensions.conf System() with positional args so we avoid
# nested JSON quoting entirely. Env: PSTN_INTERNAL_SECRET (exported by
# docker-entrypoint before asterisk starts).
# Usage: pstn-incoming.sh "<caller>" "<called>" "<channel>" [port] [gatewayHost]
#
# port / gatewayHost are resolved by [dinstar-resolve-port] from the SIP Via
# header. They are optional for backward compatibility: an empty value keeps
# the previous behaviour (the backend guesses from the channel name), but
# passing them is what makes inbound owner resolution and port release exact.
# ============================================================

CALLER="$1"
CALLED="$2"
CHANNEL="$3"
PORT="${4:-}"
GATEWAY_HOST="${5:-}"

if [ -z "$CALLER" ] || [ -z "$CHANNEL" ]; then
  logger -t pstn-incoming "missing args caller or channel"
  exit 1
fi

# حارس صياغة: الحقول تُدرج في JSON بلا ترميز، فأي محرف اقتباس أو شرطة
# مائلة عكسية يفسد الجسم ويعيد 400. نُقصي كل ما ليس رقمًا في المنفذ،
# وكل ما ليس رقمًا أو نقطة في العنوان.
PORT=$(printf '%s' "$PORT" | tr -cd '0-9')
GATEWAY_HOST=$(printf '%s' "$GATEWAY_HOST" | tr -cd '0-9.')

# منفذ صالح فقط 0..31 (حدّ عائلة UC2000). ما عدا ذلك يُرسَل -1 ويعني
# «غير معروف» للخادم بدل رقم مضلِّل.
if [ -n "$PORT" ]; then
  if [ "$PORT" -gt 31 ] 2>/dev/null; then
    PORT=""
  fi
fi
[ -n "$PORT" ] || PORT="-1"

PAYLOAD=$(printf '{"caller":"%s","called":"%s","channel":"%s","gatewayHost":"%s","port":%s}' \
  "$CALLER" "$CALLED" "$CHANNEL" "$GATEWAY_HOST" "$PORT")

post_incoming() {
  curl -sS -m 5 -w "\n%{http_code}" -X POST "http://$1:8080/api/internal/pstn/incoming" \
    -H 'Content-Type: application/json' \
    -H "X-Internal-Secret: ${PSTN_INTERNAL_SECRET}" \
    --data-binary "$PAYLOAD" 2>&1
}

# جرّب backend أولاً (الاسم الصحيح في docker-compose)، ثم fallback لـ red-backend
RESPONSE=$(post_incoming backend)
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if echo "$HTTP_CODE" | grep -q "^2"; then
  exit 0
fi

# Fallback: جرّب red-backend كاسم ثانوي
RESPONSE2=$(post_incoming red-backend)
HTTP_CODE2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | sed '$d')

if echo "$HTTP_CODE2" | grep -q "^2"; then
  exit 0
fi

logger -t pstn-incoming "POST failed: backend=$HTTP_CODE ($BODY) red-backend=$HTTP_CODE2 ($BODY2) caller=$CALLER channel=$CHANNEL port=$PORT gw=$GATEWAY_HOST"
exit 1
