#!/bin/sh
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
IFS=','
for gw_ip in $DINSTAR_IPS; do
  [ -n "$gw_ip" ] || continue
  gw_name="dinstar-gw-$(echo "$gw_ip" | tr '.' '-')"
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

# منفذ الاتصال = المنفذ المحلي لمنفذ GSM وجهة المسار (المنفذ 2 → 5062):
# محرك المسارات في البوابة يقبل مكالمات IP→Tel فقط على مقبس المنفذ الوجهة
# (لا على مقبس الترنك 5060)، فكل مكالمة إلى 5060 تُرفض 503.
[${gw_name}]
type=aor
contact=sip:${gw_ip}:5062
qualify_frequency=30

[${gw_name}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
from_user=1000000
aors=${gw_name}

[${gw_name}]
type=identify
endpoint=${gw_name}
match=${gw_ip}
EOF
  gw_index=$((gw_index + 1))
done
unset IFS

# اسم توافقي للجهاز الأول: الخطط القديمة تستدعي dinstar-gateway
# مباشرةً عند غياب RED_GW.
first_ip="${DINSTAR_IPS%%,*}"
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[dinstar-gateway]
type=aor
contact=sip:${first_ip}:5062
qualify_frequency=30

[dinstar-gateway]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
from_user=1000000
aors=dinstar-gateway
EOF

# ═══════════════════════════════════════════════════════════════════════════
# قبول تسجيل منافذ البوابة (REGISTER) — المنافذ لا تسلّم المكالمات إلا إذا
# سُجّلت لدى "خادم SIP" (أستيريكس): كل منفذ يسجّل بمستخدمه الرقمي 0..7.
# بدون هذه النهايات يرد أستيريكس 403 (Endpoint 'anonymous' has no AORs)،
# تبقى المنافذ Unregistered فيُرفض كل مكالمة واردة بـ 503 Service Unavailable.
# هذه النهايات لأجل قبول REGISTER فقط — مكالمات البوابة الخارجة تذهب عبر
# ترنك dinstar-gateway مباشرةً، فيبقى context محايدًا (from-dinstar).
for pnum in $(seq 0 7); do
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[${pnum}]
type=aor
max_contacts=1
remove_existing=yes
qualify_frequency=30

[${pnum}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm
direct_media=no
force_rport=yes
rewrite_contact=no
aors=${pnum}
EOF
done

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

cat > "$CONFIG_DIR/http.conf" <<EOF
[general]
servername=Asterisk
enabled=yes
bindaddr=0.0.0.0
bindport=8088
sessionlimit=200
session_inactivity=300000
session_keep_alive=15000
EOF

# ═══════════════════════════════════════════════════════════════════════════
# WebRTC Client — تسجيل ديناميكي للتطبيق
# ═══════════════════════════════════════════════════════════════════════════
# كل مستخدم يحصل على حساب SIP فريد当他 يتصل بـ Asterisk عبر WSS.
# الباسورد يُولَّد من الـ JWT token عبر Backend.
WEBRTC_SECRET="${WEBRTC_SIP_SECRET:-red-webrtc-secret}"
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
tone_zone=YE
EOF

# ═══════════════════════════════════════════════════════════════════════════
# Dynamic WebRTC clients — لكل مستخدم حساب SIP فريد
# ═══════════════════════════════════════════════════════════════════════════
# القسم أعلاه [red-webrtc-client] يكفي للوضع الحالي.
# لاحقًا يمكن إضافة حسابات ديناميكية عبر AMI.

# ═════════════════════════════════════════════════════════════════════════════
# Dynamic dongle.conf generation from DINSTAR_USB_DEVICES
# Format: DINSTAR_USB_DEVICES="dongle0:/dev/ttyUSB1:/dev/ttyUSB2,dongle1:/dev/ttyUSB3:/dev/ttyUSB4,..."
# If not set, use static dongle.conf template
# ═════════════════════════════════════════════════════════════════════════════
DONGLE_CONF="/etc/asterisk/dongle.conf"
if [ -n "${DINSTAR_USB_DEVICES:-}" ]; then
  cat > "$DONGLE_CONF" <<EOF
; ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
; RED SOVEREIGN — chan_dongle Configuration (AUTO-GENERATED)
; Generated from DINSTAR_USB_DEVICES env var
; ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

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

  cat >> "$DONGLE_CONF" <<EOF

[sms]
smscenter=\${DONGLE_SMSCENTER:-+967712345678}
language=en
decode=yes
pdumode=0
incoming=full
max_sms_parts=10
delete=expired

[call]
dtmf=relax
callwaiting=yes
callerid=yes
busy=yes
progress=yes

[network]
operator=auto
band=all
roaming=yes
EOF
else
  # Use static template, just ensure it exists
  [ -f "$DONGLE_CONF" ] || cat > "$DONGLE_CONF" <<'EOF'
; chan_dongle static template — override with DINSTAR_USB_DEVICES
[general]
interval=15
EOF
fi

# ═════════════════════════════════════════════════════════════════════════════
# USB device permissions for dongle serial ports
# ═════════════════════════════════════════════════════════════════════════════
if [ -n "${DINSTAR_USB_DEVICES:-}" ]; then
  IFS=','; for spec in $DINSTAR_USB_DEVICES; do
    IFS=':'; set -- $spec; IFS=','
    audio_dev="$2"; data_dev="$3"
    for dev in "$audio_dev" "$data_dev"; do
      [ -c "$dev" ] && chown asterisk:asterisk "$dev" 2>/dev/null || true
    done
  done; unset IFS
fi

if [ "${RED_ASTERISK_CONFIG_ONLY:-0}" = "1" ]; then
  exit 0
fi
exec /usr/sbin/asterisk -f -U asterisk -G asterisk
