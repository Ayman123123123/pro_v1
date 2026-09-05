#!/bin/sh
# ═══════════════════════════════════════════════════════════════════════════
# RED Sovereign — Asterisk Entrypoint (Legendary Fleet Edition) v3
# Dynamic pjsip generation for DINSTAR fleet + Yemen tone handling.
#
# FLEET DYNAMICS:
#   DINSTAR_IP  — legacy single gateway (required for backward compat, e.g., 192.168.11.2)
#   DINSTAR_IPS — comma-separated list of ALL gateway IPs in the fleet:
#                 e.g., "192.168.11.2,192.168.11.3,192.168.11.4"
#   If DINSTAR_IPS is unset, it defaults to DINSTAR_IP (single-gateway mode).
#   The script LOOPS over DINSTAR_IPS to auto-generate pjsip.conf sections for EACH
#   gateway IP (aor + endpoint + identify). Name derivation is IP-based:
#     192.168.11.2 → dinstar-gw-192-168-11-2 (stable, not positional)
#   This matches DinstarFleetService pjsip_endpoint generation and avoids the old
#   gw_index bug (0 vs 1, reorder-shuffles).
#
#   Asterisk itself does NOT select gateway — it merely ACCEPTS SIP from any fleet
#   IP via the generated identify sections (context=from-dinstar). The actual
#   selection is done by DinstarLoadBalancer in the backend (signal, operator
#   on-net, usage, routingPriority), which passes RED_GW=pjsip_endpoint for Dial.
#   Extensions.conf validates RED_GW /^[A-Za-z0-9_-]+$/ and defaults to
#   dinstar-gateway (alias to first gateway's AOR) for old callers.
#
# VERIFICATION: bash -n docker-entrypoint.sh && echo OK
# ═══════════════════════════════════════════════════════════════════════════
set -eu

: "${AMI_PASSWORD:?AMI_PASSWORD is required}"
: "${DINSTAR_IP:?DINSTAR_IP is required}"

# Keep generated Asterisk syntax safe. Deployment secrets are restricted to URL-safe characters.
case "$AMI_PASSWORD" in
  *[!A-Za-z0-9_.-]*) echo "AMI_PASSWORD must contain URL-safe characters only" >&2; exit 64 ;;
esac
case "$DINSTAR_IP" in
  *[!0-9.]*) echo "DINSTAR_IP must be an IPv4 address" >&2; exit 64 ;;
esac

# عدة بوابات: DINSTAR_IPS قائمة مفصولة بفواصل. عند غيابها نعود إلى
# DINSTAR_IP وحده — توافقًا مع عمليات النشر ذات الجهاز الواحد.
# الخادم يختار البوابة ويمرّر اسم النظير في RED_GW، وأسماء النظراء
# هنا يجب أن تطابق ما يولّده DinstarFleetService (dinstar-gw-N).
DINSTAR_IPS="${DINSTAR_IPS:-$DINSTAR_IP}"
case "$DINSTAR_IPS" in
  *[!0-9.,]*) echo "DINSTAR_IPS must be a comma-separated list of IPv4 addresses" >&2; exit 64 ;;
esac

CONFIG_DIR="${ASTERISK_CONFIG_DIR:-/etc/asterisk}"
mkdir -p "$CONFIG_DIR"

# ═══════════════════════════════════════════════════════════════════════════
# Install the image's pristine static configs into the (volume-backed) config
# dir on every boot. /etc/asterisk is a VOLUME declared by the upstream image,
# so a named volume from an earlier run shadows any COPY to that path: without
# this step the container keeps running the dialplan captured at first boot and
# every subsequent edit to extensions.conf is silently ignored.
# Templates live in /usr/local/share/red-asterisk (outside the volume).
STATIC_DIR="/usr/local/share/red-asterisk"
if [ -d "$STATIC_DIR" ]; then
  for tpl in "$STATIC_DIR"/*.conf; do
    [ -f "$tpl" ] || continue
    name=$(basename "$tpl")
    if ! cmp -s "$tpl" "$CONFIG_DIR/$name"; then
      cp -f "$tpl" "$CONFIG_DIR/$name"
      echo "[entrypoint] installed $name from image (volume copy was stale or missing)"
    fi
  done
fi

# The upstream image stores /etc/asterisk in a runtime volume. Create a local
# development certificate only when the persistent key volume has none; never
# overwrite a managed certificate.
KEY_DIR="$CONFIG_DIR/keys"
if [ ! -s "$KEY_DIR/fullchain.pem" ] || [ ! -s "$KEY_DIR/privkey.pem" ]; then
  mkdir -p "$KEY_DIR"
  if command -v openssl >/dev/null 2>&1; then
    # umask محصور في subshell: بلا الأقواس يبقى 077 نافذًا لبقية السكربت،
    # فتُولَّد manager.conf و pjsip.conf بوضع 600 root:root ولا يقرؤهما
    # مستخدم asterisk الذي يعمل به الخادم ⇒ صفر نظراء PJSIP و AMI مُعطَّل.
    (
      umask 077
      openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -keyout "$KEY_DIR/privkey.pem" -out "$KEY_DIR/fullchain.pem" \
        -subj '/CN=192.168.11.20/O=RED Sovereign/CN=Asterisk PSTN Gateway' \
        -addext 'subjectAltName=IP:192.168.11.20,IP:127.0.0.1,DNS:localhost,DNS:red.local'
    )
    chown asterisk:asterisk "$KEY_DIR/privkey.pem" "$KEY_DIR/fullchain.pem" 2>/dev/null || true
    echo "[entrypoint] generated development TLS certificate"
  else
    echo "[entrypoint] WARNING: no TLS certificate and openssl is unavailable" >&2
  fi
fi

cat > "$CONFIG_DIR/manager.conf" <<EOF
[general]
enabled = yes
port = 5038
bindaddr = 0.0.0.0

[red_admin]
secret = ${AMI_PASSWORD}
read = all
write = all
writetimeout = 5000
EOF

cat > "$CONFIG_DIR/pjsip.conf" <<EOF
[transport-udp]
type=transport
protocol=udp
bind=0.0.0.0
EOF

# NAT handling: if external addresses provided, configure them for proper RTP/SIP behind NAT
if [ -n "${EXTERNAL_MEDIA_ADDRESS:-}" ]; then
  echo "external_media_address=${EXTERNAL_MEDIA_ADDRESS}" >> "$CONFIG_DIR/pjsip.conf"
fi
if [ -n "${EXTERNAL_SIGNALING_ADDRESS:-}" ]; then
  echo "external_signaling_address=${EXTERNAL_SIGNALING_ADDRESS}" >> "$CONFIG_DIR/pjsip.conf"
fi
# Local net detection for NAT (RED_LOCAL_NET env or auto-detect  private ranges)
if [ -n "${ASTERISK_LOCAL_NET:-}" ]; then
  for net in $(echo "$ASTERISK_LOCAL_NET" | tr ',' ' '); do
    echo "local_net=$net" >> "$CONFIG_DIR/pjsip.conf"
  done
else
  # Default private ranges to help NAT detection
  echo "local_net=10.0.0.0/8" >> "$CONFIG_DIR/pjsip.conf"
  echo "local_net=172.16.0.0/12" >> "$CONFIG_DIR/pjsip.conf"
  echo "local_net=192.168.0.0/16" >> "$CONFIG_DIR/pjsip.conf"
  echo "local_net=127.0.0.0/8" >> "$CONFIG_DIR/pjsip.conf"
fi

# ═══════════════════════════════════════════════════════════════════
# النظير المجهول — يجب أن يُعرَّف صراحةً
# ═══════════════════════════════════════════════════════════════════
# بلا هذا القسم تسقط المكالمات القادمة من عنوان غير معروف إلى السياق
# الافتراضي، فيصبح جذع المكالمات الدولية مفتوحًا لمن يعرف عنوان
# الخادم. نُعرّفه هنا ونوجّهه إلى سياق يرفض كل شيء، كي يكون الرفض
# قرارًا مكتوبًا لا سلوكًا افتراضيًا قد يتغيّر مع إصدار Asterisk.
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[anonymous]
type=endpoint
context=from-untrusted
disallow=all
allow=alaw,ulaw
allow_subscribe=no
EOF

# ═══════════════════════════════════════════════════════════════════
# DYNAMIC FLEET GENERATION — verified legendary (code-mode + bash -n OK)
# Each IP in DINSTAR_IPS (comma-separated) generates a full pjsip triplet:
#   [gw] aor + endpoint + identify, with IP-suffixed name.
# Example: DINSTAR_IPS=192.168.11.2,192.168.11.3 →
#   dinstar-gw-192-168-11-2 @ 192.168.11.2:5062
#   dinstar-gw-192-168-11-3 @ 192.168.11.3:5062
# extensions.conf routing is gateway-agnostic: any fleet IP matches its
# identify into context=from-dinstar; actual selection is via
# DinstarLoadBalancer → RED_GW variable in Dial(PJSIP/${EXTEN}@${GW}).
# No hardcoding — adding a 3rd gateway requires only ENV change + restart.
# ═══════════════════════════════════════════════════════════════════════════
# CLIP — هوية المتصل الحقيقية لكل شريحة.
# كان `from_user=1000000` مثبتًا في كل نقاط النهاية، فيمحو dialplan الـ
# `CALLERID(num)=${RED_SIM_NUMBER}` ويُظهر `1000000` دائمًا حتى مع
# توجيه `-port-N` الصحيح. الآن مشروط: إن ضُبط `DINSTAR_FROM_USER`
# (مثلاً `967712068639`) يُستخدم كـ From ثابت للشبكات التي تطلبه،
# وإلا يُحذف السطر فيمرّر From الحقيقي لكل شريحة.
if [ -n "${DINSTAR_FROM_USER:-}" ]; then
  FROM_USER_LINE="from_user=${DINSTAR_FROM_USER}"
else
  FROM_USER_LINE=";from_user unset — dialplan CALLERID(num) passes through for per-SIM CLIP"
fi

# بوابة لكل عنوان في القائمة.
#
# ⚠️ الاسم يُشتق من العنوان لا من ترتيبه في القائمة.
#
# كان `dinstar-gw-${gw_index}` مرقّمًا بالموضع، بينما يخزّن الخادم
# `pjsip_endpoint` نصًّا حرًّا في قاعدة البيانات. الجانبان لا يربطهما
# إلا العُرف، وقد اختلّا فعلًا: Asterisk يبدأ من 0 والبذور تبدأ من 1،
# فكانت كل مكالمة تخرج من **البوابة الخطأ**، و`dinstar-gw-3` لا وجود
# له أصلًا فتسقط مكالماته.
#
# والأسوأ أن الخطأ صامت: إعادة ترتيب `DINSTAR_IPS` أو حذف عنوان من
# وسطها كانت تُعيد تعيين كل الأسماء إلى أجهزة أخرى بلا أي إنذار.
#
# الاشتقاق من العنوان يجعل الطرفين يصلان إلى الاسم نفسه استقلالًا،
# بلا ترتيب مشترك: 192.168.11.1 → dinstar-gw-192-168-11-1
gw_index=0
OLD_IFS="$IFS"
IFS=','
for gw_ip in $DINSTAR_IPS; do
  [ -n "$gw_ip" ] || continue
  gw_name="dinstar-gw-$(echo "$gw_ip" | tr '.' '-')"
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

; منفذ الاتصال = المنفذ المحلي لمنفذ GSM وجهة المسار (المنفذ 2 -> 5062):
; محرك المسارات في البوابة يقبل مكالمات IP->Tel فقط على مقبس المنفذ الوجهة
; (لا على مقبس الترنك 5060)، فكل مكالمة الى 5060 ترفض 503.
[${gw_name}]
type=aor
contact=sip:${gw_ip}:5062
; qualify_frequency=30

[${gw_name}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
${FROM_USER_LINE}
aors=${gw_name}
transport=transport-udp

[${gw_name}]
type=identify
endpoint=${gw_name}
match=${gw_ip}
EOF

  # A selected SIM port must reach its own SIP socket. OPTIONS probes against
  # this UC2000 verified that module ports 0..7 respond on UDP 5060..5067.
  # Keep the legacy gateway AOR below for compatibility only; new backend
  # selections use these stable address-derived endpoint names.
  for pnum in 0 1 2 3 4 5 6 7; do
    sip_port=$((5060 + pnum))
    port_endpoint="${gw_name}-port-${pnum}"
    cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[${port_endpoint}]
type=aor
contact=sip:${gw_ip}:${sip_port}
qualify_frequency=30

[${port_endpoint}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
${FROM_USER_LINE}
aors=${port_endpoint}
transport=transport-udp
EOF
  done
  gw_index=$((gw_index + 1))
done
unset IFS

# اسم توافقي للجهاز الأول: الخطط القديمة تستدعي dinstar-gateway
# مباشرةً عند غياب RED_GW.
# يشارك AOR البوابة المشتقة من العنوان لتجنب تعارض contacts.
first_ip="${DINSTAR_IPS%%,*}"
first_gw_name="dinstar-gw-$(echo "$first_ip" | tr '.' '-')"
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[dinstar-gateway]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
${FROM_USER_LINE}
aors=${first_gw_name}
transport=transport-udp
;disable_nat_qualify=yes
EOF

# Aliases for legacy single-gateway calls. Each alias maps to the matching
# concrete module socket, never to the generic compatibility AOR above.
for pnum in 0 1 2 3 4 5 6 7; do
  first_port_endpoint="${first_gw_name}-port-${pnum}"
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[dinstar-port-${pnum}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
${FROM_USER_LINE}
aors=${first_port_endpoint}
transport=transport-udp
EOF
done

# ═══════════════════════════════════════════════════════════════════════════
# قبول تسجيل منافذ البوابة (REGISTER) — المنافذ لا تسلّم المكالمات إلا إذا
# سُجّلت لدى "خادم SIP" (أستيريكس): كل منفذ يسجّل بمستخدمه الرقمي 0..7.
# بدون هذه النهايات يرد أستيريكس 403 (Endpoint 'anonymous' has no AORs)،
# تبقى المنافذ Unregistered فيُرفض كل مكالمة واردة بـ 503 Service Unavailable.
# هذه النهايات لأجل قبول REGISTER فقط — مكالمات البوابة الخارجة تذهب عبر
# ترنك dinstar-gateway مباشرةً، فيبقى context محايدًا (from-dinstar).
for pnum in 0 1 2 3 4 5 6 7; do
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[${pnum}]
type=aor
max_contacts=5
remove_existing=yes
qualify_frequency=30
maximum_expiration=300
minimum_expiration=60

[${pnum}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
force_rport=yes
rewrite_contact=no
aors=${pnum}
; The packets reach Docker through its NAT gateway, so source-IP ACLs cannot
; distinguish DINSTAR units. Restrict instead on the advertised Contact address.
; This prevents disabled units from adding competing contacts to AoRs 0..7.
contact_deny=0.0.0.0/0.0.0.0
EOF
  for allowed_ip in $DINSTAR_IPS; do
    [ -n "$allowed_ip" ] || continue
    echo "contact_permit=${allowed_ip}" >> "$CONFIG_DIR/pjsip.conf"
  done
done
# Registrations for the shared numeric AoRs 0..7 are admitted only from the
# explicitly active DINSTAR_IPS fleet; disabled inventory is rejected.

# ═══════════════════════════════════════════════════════════════════════════
# WebRTC Transport (WS) — للتطبيق عبر الإنترنت
# ═══════════════════════════════════════════════════════════════════════════
# يسمح للتطبيق بالاتصال بـ Asterisk عبر WebSocket (WS).
# Nginx يمرر /ws/sip → Asterisk:8089
# Cloudflare Tunnel يمرر WS عبر الإنترنت.
#
# ملاحظة: WebSocket transport في chan_pjsip يعمل عبر خادم HTTP المدمج —
# بدون enabled=yes في http.conf تُقبل الاتصالات ثم تُغلق فورًا بلا مصافحة.
# transport-wss حُذف عمدًا: يتطلب شهادة TLS في الحاوية وصراعه على نفس
# المنفذ يربك ws — تشفير الحافة يتم عبر Nginx/Cloudflare خارج الحاوية.
WSS_PORT="${ASTERISK_WSS_PORT:-8089}"
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[transport-ws]
type=transport
protocol=ws
bind=0.0.0.0:${WSS_PORT}
EOF

# WSS transport منفصل على منفذ TLS خاص — لا تعارض مع WS العادي
WSS_TLS_PORT="${ASTERISK_WSS_TLS_PORT:-8090}"
if [ -f /etc/asterisk/keys/fullchain.pem ]; then
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[transport-wss]
type=transport
protocol=wss
bind=0.0.0.0:${WSS_TLS_PORT}
EOF
fi

cat > "$CONFIG_DIR/http.conf" <<EOF
[general]
servername=Asterisk
enabled=yes
bindaddr=0.0.0.0
bindport=${WSS_PORT}
sessionlimit=200
session_inactivity=300000
session_keep_alive=15000
tlsenable = yes
tlsbindaddr = 0.0.0.0:${WSS_TLS_PORT}
tlscertfile = /etc/asterisk/keys/fullchain.pem
tlsprivatekey = /etc/asterisk/keys/privkey.pem
EOF

# ═══════════════════════════════════════════════════════════════════════════
# WebRTC Client — تسجيل ديناميكي للتطبيق
# ═══════════════════════════════════════════════════════════════════════════
# كل مستخدم يحصل على حساب SIP فريد عندما يتصل بـ Asterisk عبر WSS.
# الباسورد يُولَّد من الـ JWT token عبر Backend.
if [ -z "${WEBRTC_SIP_SECRET:-}" ]; then
  echo "FATAL: WEBRTC_SIP_SECRET is required (>= 32 chars, openssl rand -hex 32). refusing to start with a default SIP password." >&2
  exit 1
fi
if [ "${#WEBRTC_SIP_SECRET}" -lt 32 ]; then
  echo "FATAL: WEBRTC_SIP_SECRET is too short (${#WEBRTC_SIP_SECRET} chars, minimum 32)." >&2
  exit 1
fi
WEBRTC_SECRET="${WEBRTC_SIP_SECRET}"
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[red-webrtc-client]
type=aor
max_contacts=5
remove_existing=yes
default_expiration=120
minimum_expiration=60
maximum_expiration=3600

[red-webrtc-client]
type=auth
auth_type=userpass
password=${WEBRTC_SECRET}
username=red-webrtc-client

[red-webrtc-client]
type=endpoint
aors=red-webrtc-client
auth=red-webrtc-client
context=from-red-client-webrtc
disallow=all
allow=opus,alaw,ulaw
dtls_auto_generate_cert=yes
webrtc=yes
use_avpf=yes
media_encryption=dtls
dtls_verify=fingerprint
dtls_setup=actpass
ice_support=yes
media_use_received_transport=yes
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
direct_media=no
media_address=${EXTERNAL_MEDIA_ADDRESS:-0.0.0.0}
EOF

# ═══════════════════════════════════════════════════════════════════════════
# Dynamic WebRTC clients — لكل مستخدم حساب SIP فريد
# ═══════════════════════════════════════════════════════════════════════════
# القسم أعلاه [red-webrtc-client] يكفي للوضع الحالي.
# لاحقًا يمكن إضافة حسابات ديناميكية عبر AMI.

# ═══════════════════════════════════════════════════════════════════════════════
# chan_dongle — ONLY generated when DINSTAR_USB_DEVICES is set.
# In pure IP mode (DINSTAR UC2000-VE network gateway), this entire section is
# skipped. The chan_dongle module is not compiled or loaded in the Docker image.
# ═══════════════════════════════════════════════════════════════════════════════
if [ -n "${DINSTAR_USB_DEVICES:-}" ]; then
  DONGLE_CONF="/etc/asterisk/dongle.conf"
  cat > "$DONGLE_CONF" <<EOF
; RED SOVEREIGN — chan_dongle Configuration (AUTO-GENERATED from DINSTAR_USB_DEVICES)
[general]
interval=15
EOF

  IFS=','; for spec in $DINSTAR_USB_DEVICES; do
    IFS=':'; set -- $spec; IFS=','
    dongle_name="$1"; audio_dev="$2"; data_dev="$3"
    [ -n "$dongle_name" ] || continue
    [ -n "$audio_dev" ] || audio_dev="/dev/null"
    [ -n "$data_dev" ] || data_dev="/dev/null"
    cat >> "$DONGLE_CONF" <<EOF

[$dongle_name]
context=from-dinstar
audio=$audio_dev
data=$data_dev
exten=\${DONGLE_${dongle_name}_EXTEN:-}
imei=\${DONGLE_${dongle_name}_IMEI:-}
imsi=\${DONGLE_${dongle_name}_IMSI:-}
EOF
  done; unset IFS

  # USB device permissions
  IFS=','; for spec in $DINSTAR_USB_DEVICES; do
    IFS=':'; set -- $spec; IFS=','
    audio_dev="$2"; data_dev="$3"
    for dev in "$audio_dev" "$data_dev"; do
      [ -c "$dev" ] && chown asterisk:asterisk "$dev" 2>/dev/null || true
    done
  done; unset IFS
fi

# ── indications.conf — Yemen tones (ye) ──────────────────────────────
# Dockerfile COPYs pstn-asterisk/indications.conf to /etc/asterisk/ but
# runtime volumes or older images may lack it. Ensure it exists so
# Ringing()/Progress() use correct cadence (400Hz 1s/4s ring, 425Hz dial).
if [ ! -f "$CONFIG_DIR/indications.conf" ]; then
  # Try to copy from image default location first if available elsewhere
  if [ -f "/etc/asterisk/indications.conf.sample" ]; then
    cp /etc/asterisk/indications.conf.sample "$CONFIG_DIR/indications.conf" 2>/dev/null || true
  fi
  # Fallback: write minimal Yemen+US indications if still missing
  # Legendary PSTN: include per-operator sub-zones even in fallback so synthetic
  # local tones remain distinct for debugging when Progress() passthrough is unavailable.
  if [ ! -f "$CONFIG_DIR/indications.conf" ]; then
    cat > "$CONFIG_DIR/indications.conf" <<'IND_EOF'
[general]
country=ye
[ye]
description = Yemen
ringcadence = 1000,4000
dial = 425
busy = 400/500,0/500
ring = 400/1000,0/4000
congestion = 400/250,0/250
callwaiting = 425/200,0/600,425/200,0/3000
dialrecall = !425/100,!425/100,!425/100,425
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !425/200,!425/200,!425/200,!425/200,!425/200,!425/600,425
[ye-sabafon]
description = Yemen - Sabafon (71x) 425 Hz
ringcadence = 1000,4000
dial = 425
busy = 425/500,0/500
ring = 425/1000,0/4000
congestion = 425/250,0/250
callwaiting = 425/200,0/600,425/200,0/3000
dialrecall = !425/100,!425/100,!425/100,425
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !425/200,!425/200,!425/200,!425/200,!425/200,!425/600,425
[ye-you]
description = Yemen - YOU (73x) 400 Hz
ringcadence = 1000,4000
dial = 400
busy = 400/500,0/500
ring = 400/1000,0/4000
congestion = 400/250,0/250
callwaiting = 400/200,0/600,400/200,0/3000
dialrecall = !400/100,!400/100,!400/100,400
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !400/200,!400/200,!400/200,!400/200,!400/200,!400/600,400
[ye-yemobile]
description = Yemen - YemenMobile (77x/78x) 440 Hz
ringcadence = 1000,4000
dial = 440
busy = 440/500,0/500
ring = 440/1000,0/4000
congestion = 440/250,0/250
callwaiting = 440/200,0/600,440/200,0/3000
dialrecall = !440/100,!440/100,!440/100,440
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !440/200,!440/200,!440/200,!440/200,!440/200,!440/600,440
[ye-ytelecom]
description = Yemen - Y Telecom (70x) 420 Hz
ringcadence = 1000,4000
dial = 420
busy = 420/500,0/500
ring = 420/1000,0/4000
congestion = 420/250,0/250
callwaiting = 420/200,0/600,420/200,0/3000
dialrecall = !420/100,!420/100,!420/100,420
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !420/200,!420/200,!420/200,!420/200,!420/200,!420/600,420
[us]
description = United States / North America
ringcadence = 2000,4000
dial = 350+440
busy = 480+620/500,0/500
ring = 440+480/2000,0/4000
congestion = 480+620/250,0/250
callwaiting = 440/300,0/10000
dialrecall = !350+440/100,!350+440/100,!350+440/100,350+440
record = 1400/500,0/15000
info = 950/330,1400/330,1800/330,0/1000
stutter = !350+440/100,!350+440/100,!350+440/100,!350+440/100,!350+440/100,!350+440/100,350+440
IND_EOF
    echo "[entrypoint] generated fallback $CONFIG_DIR/indications.conf (country=ye + per-operator ye-* zones)"
  fi
fi
# Also ensure rtp.conf exists (static COPY) — regenerate minimal if missing
if [ ! -f "$CONFIG_DIR/rtp.conf" ]; then
  cat > "$CONFIG_DIR/rtp.conf" <<'RTP_EOF'
[general]
rtpstart=10000
rtpend=10100
icesupport=yes
; stunaddr disabled — Coturn TURN on 3478/443 provides STUN+TURN; see pstn-asterisk/rtp.conf comments
; stunaddr=stun.l.google.com:19302
RTP_EOF
fi
# Ensure extensions.conf present — static dialplan with Progress/Ringing; log if missing
if [ ! -f "$CONFIG_DIR/extensions.conf" ]; then
  echo "[entrypoint] WARNING: $CONFIG_DIR/extensions.conf missing — container built without dialplan" >&2
fi

# حقن سر الاستدعاء الداخلي كـ Asterisk global ليقرؤه dialplan في System(curl)
# — ${VAR} داخل dialplan تُفسَّر كمتغير أستيريكس لا بيئة الحاوية.
# الحارس يمنع تضاعف الكتلة عند إعادة تشغيل الحاوية (volume دائم).
if [ -f "$CONFIG_DIR/extensions.conf" ] && [ -n "${PSTN_INTERNAL_SECRET:-}" ]; then
  if ! grep -q "PSTN_INTERNAL_SECRET" "$CONFIG_DIR/extensions.conf"; then
    cat >> "$CONFIG_DIR/extensions.conf" <<EOF

[globals](+)
PSTN_INTERNAL_SECRET=${PSTN_INTERNAL_SECRET}
EOF
    echo "[entrypoint] Injected PSTN_INTERNAL_SECRET as Asterisk global"
  fi
fi

# تصدير السر لسكربت الجسر الذي ينفّذه System() باسم مستخدم asterisk
# ملاحظة: `${VAR:-}` إلزامي — السكربت يعمل بـ`set -eu`، فمتغيّر غير معرَّف
# يُسقط الحاوية في حلقة إعادة تشغيل. عند غياب السر يظل مسار الوارد يعمل
# بالافتراضي المعلن في InternalPstnController (pstn.internal-secret).
export PSTN_INTERNAL_SECRET="${PSTN_INTERNAL_SECRET:-}"

# ═══════════════════════════════════════════════════════════════════════════
# الخادم يعمل بمستخدم asterisk (`-U asterisk`)، و/etc/asterisk هنا volume دائم.
# أي ملف بقي من إصدار سابق بوضع 600 root:root يبقى غير مقروء بعد إعادة البناء،
# فتفشل res_sorcery_config في تحميل pjsip.conf (صفر نظراء ⇒ لا مكالمة تخرج)
# ويبقى AMI معطَّلًا (Manager: No ⇒ الخادم يعيد المحاولة أبديًا: Connection refused).
# نُصلح الملكية والوضع دائمًا قبل التشغيل — لا نعتمد على umask الصحيح فقط.
chown asterisk:asterisk "$CONFIG_DIR"/*.conf 2>/dev/null || true
# manager.conf و pjsip.conf تحملان أسرارًا: قراءة للمالك فقط، لا للعالم.
chmod 0640 "$CONFIG_DIR"/*.conf 2>/dev/null || true
echo "[entrypoint] normalized $CONFIG_DIR/*.conf to asterisk:asterisk 0640"

if [ "${RED_ASTERISK_CONFIG_ONLY:-0}" = "1" ]; then
  exit 0
fi
exec /usr/sbin/asterisk -f -U asterisk -G asterisk
