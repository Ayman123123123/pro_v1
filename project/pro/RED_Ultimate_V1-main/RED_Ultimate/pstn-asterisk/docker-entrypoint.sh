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

CONFIG_DIR="${ASTERISK_CONFIG_DIR:-/etc/asterisk}"
mkdir -p "$CONFIG_DIR"

cat > "$CONFIG_DIR/manager.conf" <<EOF
[general]
enabled = yes
port = 5038
bindaddr = 0.0.0.0

[red_admin]
secret = ${AMI_PASSWORD}
read = call,reporting,system
write = call,originate
writetimeout = 5000
EOF

cat > "$CONFIG_DIR/pjsip.conf" <<EOF
[transport-udp]
type=transport
protocol=udp
bind=0.0.0.0

[dinstar-gateway]
type=aor
contact=sip:${DINSTAR_IP}:5060
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
aors=dinstar-gateway

[dinstar-gateway]
type=identify
endpoint=dinstar-gateway
match=${DINSTAR_IP}
EOF

# RED-to-RED WebRTC never enters Asterisk. Asterisk is reserved for authorized DINSTAR voice only.
if [ "${RED_ASTERISK_CONFIG_ONLY:-0}" = "1" ]; then
  exit 0
fi
exec /usr/sbin/asterisk -f -U asterisk -G asterisk
