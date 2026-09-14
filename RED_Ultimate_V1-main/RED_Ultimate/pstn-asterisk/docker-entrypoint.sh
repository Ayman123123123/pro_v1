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
# عنوان الوسائط المُعلَن لبوابة DINSTAR — إصلاح "صفر صوت / 100% RTP loss"
# ═══════════════════════════════════════════════════════════════════════════
# الشاهد الحيّ (cdr.htm، مكالمتان IP->Gsm على المنفذ 0):
#   NO CARRIER | G.711A | sent=976 | recv=0 | loss=100%
# أي أن أستيريكس أرسل RTP والبوابة لم تُرسل شيئًا يصل — وهذا توقيع خطأ
# عنوان لا خطأ ترميز أو تفاوض.
#
# السبب: `external_media_address` لا يُطبَّق إلا على نظير يقع **خارج**
# `local_net`. وقائمة local_net أدناه تضم 192.168.0.0/16، وهي تحتوي شبكة
# البوابة 192.168.11.0/24 نفسها. فتَعُدّ PJSIP البوابة "محلية" ولا تُعيد
# كتابة SDP، فيُعلن أستيريكس عنوان الحاوية 172.x — وهو عنوان داخلي في
# شبكة Docker لا تستطيع البوابة توجيه RTP إليه إطلاقًا.
#
# الحل الجراحي: `media_address` على نظراء البوابة تحديدًا. هذا الخيار
# يفرض عنوان SDP لهذا النظير مهما كان تصنيف local_net، فلا نحتاج إخراج
# 192.168.0.0/16 من local_net (وهو ما كان سيُفسد نظراء LAN الآخرين).
#
# القيمة = العنوان الثابت للمضيف على شبكة البوابة (canonical: 192.168.11.10).
DINSTAR_MEDIA_ADDRESS="${EXTERNAL_MEDIA_ADDRESS:-}"
if [ -n "$DINSTAR_MEDIA_ADDRESS" ]; then
  case "$DINSTAR_MEDIA_ADDRESS" in
    *[!0-9.]*)
      echo "[entrypoint] WARNING: EXTERNAL_MEDIA_ADDRESS='$DINSTAR_MEDIA_ADDRESS' is not IPv4 — skipping media_address" >&2
      DINSTAR_MEDIA_ADDRESS=""
      ;;
  esac
fi
if [ -n "$DINSTAR_MEDIA_ADDRESS" ]; then
  DINSTAR_MEDIA_LINE="media_address=${DINSTAR_MEDIA_ADDRESS}"
  echo "[entrypoint] DINSTAR peers will advertise media_address=${DINSTAR_MEDIA_ADDRESS} in SDP"
else
  DINSTAR_MEDIA_LINE="; media_address unset — EXTERNAL_MEDIA_ADDRESS was empty or invalid"
  echo "[entrypoint] WARNING: no EXTERNAL_MEDIA_ADDRESS — DINSTAR will receive the container IP in SDP and audio will be one-way" >&2
fi

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
      # 2026-09-04: SAN من البيئة لا عنوان ميت (.20 سابقًا). TLS_SAN_IP هو
      # عنوان المضيف على LAN (canonical: 192.168.11.10).
      TLS_SAN_IP="${TLS_SAN_IP:-192.168.11.10}"
      openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -keyout "$KEY_DIR/privkey.pem" -out "$KEY_DIR/fullchain.pem" \
        -subj "/CN=${TLS_SAN_IP}/O=RED Sovereign/CN=Asterisk PSTN Gateway" \
        -addext "subjectAltName=IP:${TLS_SAN_IP},IP:127.0.0.1,DNS:localhost,DNS:red.local"
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
[global]
type=global
; ⚠️ الترتيب حاسم: username قبل ip.
;
; Docker يُترجم عنوان المصدر (SNAT) فتصل كل رزم البوابة من عنوان بوابة
; الشبكة الداخلية (172.19.0.1) لا من 192.168.11.1. ومع الترتيب الافتراضي
; (ip أولًا) تُنسب رسالة REGISTER إلى نظير الترنك عبر match=172.16.0.0/12،
; فيبحث المسجّل عن AoR باسم مستخدم الترويسة To ولا يجده على ذلك النظير:
;   WARNING res_pjsip_registrar.c: AOR '' not found for endpoint
;           'dinstar-gw-<ip>' (172.19.0.1:59260)
; فتبقى كل منافذ البوابة Unregistered أبديًا (مُثبت حيًّا 2026-09-05).
;
; بتقديم username تُطابق رسالة REGISTER نظيرها الصحيح عبر اسم المصادقة،
; وتبقى INVITE الواردة من البوابة تُطابق عبر ip لأنها بلا مصادقة —
; فلا يُفقد أي مسار.
endpoint_identifier_order=username,ip,anonymous

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
# ═══════════════════════════════════════════════════════════════════════════
# local_net — يحدّد مَن يُعَدّ «محليًا» فلا تُعاد كتابة عنوانه في SIP
# ═══════════════════════════════════════════════════════════════════════════
# ⚠️ الافتراض القديم كان يضم 192.168.0.0/16 و10.0.0.0/8. وشبكة البوابة
# 192.168.11.0/24 داخل الأولى، فتَعُدّ PJSIP البوابة «محلية» ولا تُطبّق
# external_signaling_address ولا external_media_address عليها.
#
# النتيجة الفعلية (وهي سبب «البورت يجلس معلّق على مكالمة»):
#   أستيريكس يعلن  Contact: sip:...@172.19.0.11:5060  ← عنوان داخلي في
#   شبكة Docker لا تستطيع البوابة الوصول إليه إطلاقًا. فكل طلب داخل الحوار
#   الذي ترسله البوابة إلى ذلك العنوان يضيع:
#     • ACK على 200 OK لا يصل  ⇒ أستيريكس يُعيد إرسال 200 حتى Timer H،
#       والبوابة تظن المكالمة قائمة ⇒ المنفذ الخلوي مشغول بلا مكالمة.
#     • BYE من البوابة لا يصل  ⇒ أستيريكس لا يعرف أن المكالمة انتهت،
#       فلا يصدر HangupEvent، فلا يُحرَّر المنفذ في الخادم إلا بالمهلة.
#   وعلى مستوى الوسائط: SDP يحمل 172.x ⇒ RTP باتجاه واحد (sent=N recv=0).
#
# الصحيح: كل ما هو خارج شبكة Docker يصل إلينا عبر NAT الخاص بـDocker،
# فيجب أن يُعَدّ «خارجيًا» ليُستبدَل العنوان المُعلن بعنوان المضيف على
# شبكة البوابة. المحلي هو شبكة الحاويات وحدها.
#
# ملاحظة: عميل WebRTC يصل عبر nginx داخل الشبكة نفسها (172.19.x) فيبقى
# محليًا، وعنوان وسائطه مضبوط صراحةً بـ media_address على نظيره.
if [ -n "${ASTERISK_LOCAL_NET:-}" ]; then
  for net in $(echo "$ASTERISK_LOCAL_NET" | tr ',' ' '); do
    echo "local_net=$net" >> "$CONFIG_DIR/pjsip.conf"
  done
else
  echo "local_net=172.16.0.0/12" >> "$CONFIG_DIR/pjsip.conf"
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
# ═══════════════════════════════════════════════════════════════════════════
# خريطة مقابس المنافذ — الجهاز هو المصدر، لا الحساب الرياضي
# ═══════════════════════════════════════════════════════════════════════════
# البوابة تختار المنفذ الخلوي بحسب **مقبس الوجهة** الذي يصل إليه INVITE،
# والافتراض الشائع أن المقبس = 5060+رقم المنفذ. هذا صحيح غالبًا لكنه ليس
# قانونًا: صفحة Port List تسمح بأي مقبس لكل منفذ، والجهاز الحيّ يرفض إسناد
# 5060 للمنفذ 0 لأن 5060 محجوز لمقبس الجذع المشترك — فيبقى المنفذ 0 على
# 5068 (مُثبت حيًّا 2026-09-05: محاولة الكتابة نجحت HTTP 302 والقيمة لم تتغيّر).
#
# مع الحساب الأعمى 5060+N كان أستيريكس يوجّه مكالمات المنفذ 0 إلى 5060،
# أي إلى مقبس الجذع، فتُطبَّق عليه قاعدة التوجيه الشاملة بدل قاعدة المنفذ 0.
#
# DINSTAR_PORT_SIP_PORTS: قائمة مفصولة بفواصل بترتيب المنافذ 0..7.
# الفراغ أو القيمة غير الرقمية ⇒ رجوع إلى 5060+N لذلك المنفذ وحده.
DINSTAR_PORT_SIP_PORTS="${DINSTAR_PORT_SIP_PORTS:-}"
sip_port_for() {
  _idx="$1"
  _fallback=$((5060 + _idx))
  [ -n "$DINSTAR_PORT_SIP_PORTS" ] || { echo "$_fallback"; return; }
  _i=0
  OLD_IFS_SP="$IFS"
  IFS=','
  for _p in $DINSTAR_PORT_SIP_PORTS; do
    if [ "$_i" = "$_idx" ]; then
      case "$_p" in
        ''|*[!0-9]*) : ;;
        *) [ "$_p" -ge 1 ] 2>/dev/null && [ "$_p" -le 65535 ] 2>/dev/null && _fallback="$_p" ;;
      esac
      break
    fi
    _i=$((_i + 1))
  done
  IFS="$OLD_IFS_SP"
  echo "$_fallback"
}

gw_index=0
OLD_IFS="$IFS"
IFS=','
for gw_ip in $DINSTAR_IPS; do
  [ -n "$gw_ip" ] || continue
  gw_name="dinstar-gw-$(echo "$gw_ip" | tr '.' '-')"
  cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

; نظير البوابة العام (بلا منفذ محدد) = مقبس الترنك 5060.
;
; ⚠️ كان هذا العنوان مثبَّتًا على 5062، أي مقبس المنفذ 2. فكل مكالمة لم
; يمرّر لها الخادم RED_PORT_INDEX كانت تخرج من المنفذ 2 حتمًا بصرف النظر
; عن اختيار موزّع الأحمال، وإن كان المنفذ 2 بلا شريحة سقطت المكالمة —
; وهو خطأ صامت تمامًا لأن الاسم يقول "gateway" لا "port-2".
;
; اختيار المنفذ الحقيقي يتم عبر النظراء ‎${gw_name}-port-N‎ أدناه، ويثبّته
; extensions.conf بإلحاق ‎-port-\${RED_PORT_INDEX}‎. أما 5060 فهو المقبس
; الذي تُطبَّق عليه قاعدة التوجيه الشاملة IP→Tel (الفهرس 63) في البوابة.
[${gw_name}]
type=aor
contact=sip:${gw_ip}:5060
qualify_frequency=30
qualify_timeout=5.0

[${gw_name}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm,g722
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
from_user=1000000
aors=${gw_name}
transport=transport-udp
rtp_timeout=60
rtp_timeout_hold=180
rtp_keepalive=30
timers=yes
timers_min_se=90
; 100rel (RFC 3262): reliable provisional responses — guarantees 183 Session
; Progress with SDP reaches the caller even on lossy paths. Critical for GSM
; gateways where early media carries the real carrier ringback. The UC2000
; supports it (SIP Config → Enable 100rel → Yes must match on the device).
100rel=yes
trust_id_inbound=yes
send_connected_line=no
${DINSTAR_MEDIA_LINE}

[${gw_name}]
type=identify
endpoint=${gw_name}
match=${gw_ip}
match=172.16.0.0/12
match=10.0.0.0/8
EOF

  # A selected SIM port must reach its own SIP socket. VERIFIED LIVE 2026-09-04
  # (UDP OPTIONS from host to 192.168.11.1): every socket 5060..5067 answers
  # 200 OK with its own user — :5060→user 0, :5061→user 1, ... :5067→user 7.
  # An INVITE to :5060 is routed by the box's default rule (NOT to the
  # selected port) and fails; the port is selected by DESTINATION SOCKET.
  # So per-port AORs MUST use 5060+pnum. (A conflicting comment claiming
  # "all ports share 5060" was disproven by the live probe above.)
  for pnum in 0 1 2 3 4 5 6 7; do
    sip_port="$(sip_port_for "$pnum")"
    port_endpoint="${gw_name}-port-${pnum}"
    cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[${port_endpoint}]
type=aor
contact=sip:${gw_ip}:${sip_port}
qualify_frequency=30
qualify_timeout=5.0

[${port_endpoint}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm,g722
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
from_user=1000000
aors=${port_endpoint}
transport=transport-udp
rtp_timeout=60
rtp_timeout_hold=180
rtp_keepalive=30
timers=yes
timers_min_se=90
100rel=yes
${DINSTAR_MEDIA_LINE}
EOF
  done
  gw_index=$((gw_index + 1))
done
IFS="$OLD_IFS"

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
from_user=1000000
aors=${first_gw_name}
transport=transport-udp
100rel=yes
${DINSTAR_MEDIA_LINE}
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
from_user=1000000
aors=${first_port_endpoint}
transport=transport-udp
100rel=yes
${DINSTAR_MEDIA_LINE}
EOF
done

# ═══════════════════════════════════════════════════════════════════════════
# قبول تسجيل منافذ البوابة (REGISTER)
# ═══════════════════════════════════════════════════════════════════════════
# ⚠️ اسم النظير يجب أن يساوي **حساب SIP الفعلي** على البوابة، لا رقم المنفذ.
#
# البوابة تُسجّل بحساب `SIP User ID` المضبوط في صفحة Port List — وهو
# `ymn01..ymn08` على الجهاز الحيّ (مُثبت من enPortList.htm 2026-09-05)،
# وليس `0..7`. ومع نظراء مرقّمة فقط لا يجد المسجّل AoR مطابقًا فيرد:
#   WARNING res_pjsip_registrar.c: AOR '' not found for endpoint …
# وتبقى المنافذ الثمانية Unregistered أبديًا.
#
# ولأن REGISTER مصادَق، لكل حساب قسم auth بكلمته. القائمة تأتي من البيئة
# بصيغة `user:password` مفصولة بفواصل — لا أسرار مضمّنة في الصورة.
# الترتيب = ترتيب المنافذ 0..7، فأول عنصر هو حساب المنفذ 0.
DINSTAR_PORT_ACCOUNTS="${DINSTAR_PORT_ACCOUNTS:-}"
if [ -n "$DINSTAR_PORT_ACCOUNTS" ]; then
  acct_index=0
  OLD_IFS="$IFS"
  IFS=','
  for entry in $DINSTAR_PORT_ACCOUNTS; do
    [ -n "$entry" ] || continue
    acct_user="${entry%%:*}"
    acct_pass="${entry#*:}"
    # حارس: أسماء ونصوص آمنة لصياغة ملف الإعداد فقط.
    case "$acct_user" in
      ''|*[!A-Za-z0-9_.-]*)
        echo "[entrypoint] WARNING: skipping malformed DINSTAR_PORT_ACCOUNTS entry '$entry'" >&2
        acct_index=$((acct_index + 1))
        continue
        ;;
    esac
    if [ -z "$acct_pass" ] || [ "$acct_pass" = "$acct_user" ]; then
      echo "[entrypoint] WARNING: account '$acct_user' has no password — REGISTER will be rejected" >&2
    fi
    cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[${acct_user}]
type=aor
max_contacts=2
remove_existing=yes
qualify_frequency=30
maximum_expiration=3600
minimum_expiration=60

[${acct_user}]
type=auth
auth_type=userpass
username=${acct_user}
password=${acct_pass}

[${acct_user}]
type=endpoint
context=from-dinstar
disallow=all
allow=alaw,ulaw,gsm,g722
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
aors=${acct_user}
auth=${acct_user}
transport=transport-udp
device_state_busy_at=1
set_var=RED_PORT_INDEX=${acct_index}
100rel=yes
${DINSTAR_MEDIA_LINE}
EOF
    acct_index=$((acct_index + 1))
  done
  IFS="$OLD_IFS"
  echo "[entrypoint] generated ${acct_index} DINSTAR port SIP accounts for REGISTER"
else
  echo "[entrypoint] WARNING: DINSTAR_PORT_ACCOUNTS empty — gateway ports cannot REGISTER" >&2
fi

# نظراء رقمية 0..7 — توافقية فقط لبوابة مضبوطة بحسابات رقمية.
# تُقبل REGISTER منها بلا مصادقة ومحصورة بعناوين الأسطول عبر contact_permit.
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
set_var=RED_PORT_INDEX=${pnum}
; The packets reach Docker through its NAT gateway, so source-IP ACLs cannot
; distinguish DINSTAR units. Restrict instead on the advertised Contact address.
; This prevents disabled units from adding competing contacts to AoRs 0..7.
contact_deny=0.0.0.0/0.0.0.0
EOF
  # DINSTAR_IPS comma-separated → space-separated for POSIX for-loop
  for allowed_ip in $(echo "$DINSTAR_IPS" | tr ',' ' '); do
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
# كل مستخدم يحصل على حساب SIP فريد当他 يتصل بـ Asterisk عبر WSS.
# الباسورد يُولَّد من الـ JWT token عبر Backend.
WEBRTC_SECRET="${WEBRTC_SIP_SECRET:-red-secret-token}"
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
media_address=${TURN_PUBLIC_HOST:-0.0.0.0}
rtp_timeout=60
rtp_timeout_hold=180
rtp_keepalive=30
timers=yes
timers_min_se=90
; 100rel for WebRTC: reliable 183 delivery to the app (early media ringback).
; All modern WebRTC stacks (including Android WebRTC) support RFC 3262 PRACK.
100rel=yes
EOF

# ═══════════════════════════════════════════════════════════════════════════
# Dynamic WebRTC clients — لكل مستخدم حساب SIP فريد
# ═══════════════════════════════════════════════════════════════════════════
# القسم أعلاه [red-webrtc-client] يكفي للوضع الحالي.
# لاحقًا يمكن إضافة حسابات ديناميكية عبر AMI.

# ═══════════════════════════════════════════════════════════════════════════
# Backend endpoint — تسجيل الخادم (P0-8: كان 'anonymous has no AORs' يسقط REGISTER)
# ═══════════════════════════════════════════════════════════════════════════
RED_SIP_SECRET="${RED_SIP_PASSWORD:-red-secret-token}"
cat >> "$CONFIG_DIR/pjsip.conf" <<EOF

[red-backend]
type=aor
contact=sip:red-backend:5060
qualify_frequency=30
qualify_timeout=5.0
max_contacts=5

[red-backend]
type=auth
auth_type=userpass
username=red-backend
password=${RED_SIP_SECRET}
realm=red.sovereign

[red-backend]
type=endpoint
context=from-red-backend
disallow=all
allow=alaw,ulaw
direct_media=no
rtp_symmetric=yes
force_rport=yes
rewrite_contact=yes
aors=red-backend
auth=red-backend
rtp_timeout=120
rtp_timeout_hold=180
timers=yes
timers_min_se=90
EOF

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
rtpend=11000
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
#
# ومعه خريطة «مقبس SIP → فهرس المنفذ» التي يستخدمها السياق
# [dinstar-resolve-port] لتحديد المنفذ الخلوي للمكالمة الواردة من ترويسة
# Via. الخريطة تُولَّد من DINSTAR_PORT_SIP_PORTS نفسها التي تُبنى بها
# نظراء المنافذ، فلا يمكن أن يتباعد الطرفان.
#
# الإدراج آمن التكرار: كتلة القوالب أعلاه تُعيد نسخ extensions.conf
# الأصلي من الصورة في كل إقلاع (لأن الملف المُعدَّل يختلف عن القالب)،
# فتُمحى الكتلة المُلحقة ثم تُعاد كتابتها هنا. والحارس يمنع التضاعف
# داخل الإقلاع نفسه.
if [ -f "$CONFIG_DIR/extensions.conf" ]; then
  if ! grep -q "^; RED_GENERATED_GLOBALS" "$CONFIG_DIR/extensions.conf"; then
    {
      echo ""
      echo "; RED_GENERATED_GLOBALS — auto-written by docker-entrypoint, do not edit"
      echo "[globals](+)"
      if [ -n "${PSTN_INTERNAL_SECRET:-}" ]; then
        echo "PSTN_INTERNAL_SECRET=${PSTN_INTERNAL_SECRET}"
      fi
      # مقبس→منفذ. تكرار المقبس نفسه لمنفذين مستحيل على الجهاز، وإن حدث
      # يفوز الأول لأن Asterisk يتجاهل إعادة تعريف نفس المتغيّر العام.
      for pnum in 0 1 2 3 4 5 6 7; do
        echo "DINSTAR_PORT_FOR_$(sip_port_for "$pnum")=${pnum}"
      done
    } >> "$CONFIG_DIR/extensions.conf"
    echo "[entrypoint] Injected Asterisk globals: internal secret + DINSTAR socket→port map"
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
