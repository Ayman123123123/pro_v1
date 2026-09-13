#!/usr/bin/env sh
# ════════════════════════════════════════════════════════════════════════════
# detect-lan-ips.sh — detect host LAN IPv4 for dual-interface (Ethernet+Wi-Fi).
# Prints:  WIFI_IP  ETHERNET_IP  CLIENT_LAN_IP  DINSTAR_NIC_IP
# With -j (JSON) prints a single JSON line for machine consumption.
#
# CLIENT_LAN_IP  — Wi-Fi IP Android/browser use for Nginx/TURN/SFU.
# DINSTAR_NIC_IP — Ethernet IP on 192.168.11.0/24 the UC2000 reaches Asterisk on.
# ════════════════════════════════════════════════════════════════════════════
set -eu

json=0
[ "${1:-}" = "-j" ] && json=1

wifi_ip=""
eth_ip=""
dinstar_nic_ip=""

# Prefer `ip` (Linux); fall back to `ifconfig` style via /proc on minimal systems.
if command -v ip >/dev/null 2>&1; then
    # Wi-Fi: adapter whose operstate is up and is a wireless device.
    for dev in $(ip -o link show 2>/dev/null | awk -F': ' '{print $2}' | sed 's/@.*//'); do
        oper=$(cat "/sys/class/net/$dev/operstate" 2>/dev/null || echo down)
        [ "$oper" = "up" ] || continue
        if [ -d "/sys/class/net/$dev/wireless" ] || [ -d "/sys/class/net/$dev/phy80211" ]; then
            addr=$(ip -o -4 addr show "$dev" 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -n1)
            case "$addr" in 127.*|169.254.*|"") ;; *) wifi_ip="$addr" ;; esac
        fi
    done
    # Ethernet: up, not wireless, not virtual/vpn.
    for dev in $(ip -o link show 2>/dev/null | awk -F': ' '{print $2}' | sed 's/@.*//'); do
        oper=$(cat "/sys/class/net/$dev/operstate" 2>/dev/null || echo down)
        [ "$oper" = "up" ] || continue
        [ -d "/sys/class/net/$dev/wireless" ] || [ -d "/sys/class/net/$dev/phy80211" ] && continue
        case "$dev" in docker*|veth*|br-*|tun*|tap*|virbr*) continue ;; esac
        addr=$(ip -o -4 addr show "$dev" 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | head -n1)
        case "$addr" in 127.*|169.254.*|"") ;; *) eth_ip="$addr" ;; esac
        [ -n "$eth_ip" ] && break
    done
    # Dinstar NIC: any 192.168.11.x address (up or down).
    dinstar_nic_ip=$(ip -o -4 addr show 2>/dev/null | awk '{print $4}' | cut -d/ -f1 | grep '^192\.168\.11\.' | head -n1 || true)
elif command -v hostname >/dev/null 2>&1; then
    # macOS / minimal: hostname -I is Linux-only; fall back to first global IPv4.
    :
fi

client_lan_ip="${wifi_ip:-$eth_ip}"

if [ "$json" = "1" ]; then
    printf '{"wifiIp":"%s","ethernetIp":"%s","clientLanIp":"%s","dinstarNicIp":"%s"}\n' \
        "$wifi_ip" "$eth_ip" "$client_lan_ip" "$dinstar_nic_ip"
else
    printf 'Wi-Fi IP        : %s\n' "$wifi_ip"
    printf 'Ethernet IP     : %s\n' "$eth_ip"
    printf 'Client LAN IP   : %s\n' "$client_lan_ip"
    printf 'Dinstar NIC IP  : %s\n' "$dinstar_nic_ip"
fi
