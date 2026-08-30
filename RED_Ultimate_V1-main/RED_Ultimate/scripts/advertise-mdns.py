#!/usr/bin/env python3
# ════════════════════════════════════════════════════════════════════════════
# advertise-mdns.py — advertise the RED/YOUNES backend as an mDNS service
# (_younes._tcp.local) on the host's client-facing LAN interface, so Android
# phones discover it automatically via LocalServerDiscovery.discoverMdnsNsd().
#
# This runs on the HOST (not a Docker container) because Docker Desktop on
# Windows/macOS cannot use host networking for multicast mDNS reliably.
#
# Best-effort: uses `zeroconf` (pip) when available, else falls back to the
# system `avahi-publish` binary. Exits gracefully if neither is present.
#
#   python3 scripts/advertise-mdns.py --host 192.168.0.244 --port 8088
# ════════════════════════════════════════════════════════════════════════════
import argparse
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser(description="Advertise RED backend over mDNS (best-effort).")
    parser.add_argument("--host", required=True, help="Client-facing LAN IP (e.g. Wi-Fi IP)")
    parser.add_argument("--port", type=int, default=8088, help="HTTP port (default 8088)")
    parser.add_argument("--name", default="RED Sovereign", help="Service instance name")
    parser.add_argument("--type", default="_younes._tcp", help="mDNS service type")
    args = parser.parse_args()

    # Preference 1: zeroconf (pure Python, cross-platform).
    try:
        from zeroconf import Zeroconf, ServiceInfo
        import socket

        info = ServiceInfo(
            f"{args.type}.local.",
            f"{args.name}.{args.type}.local.",
            addresses=[socket.inet_aton(args.host)],
            port=args.port,
            properties={"path": "/", "brand": "YOUNES"},
            server=f"{args.name}.local.",
        )
        zc = Zeroconf()
        zc.register_service(info)
        print(f"[mdns] advertising {args.name} at {args.host}:{args.port} via zeroconf", flush=True)
        try:
            while True:
                time.sleep(3600)
        except KeyboardInterrupt:
            zc.unregister_service(info)
            zc.close()
        return 0
    except ImportError:
        print("[mdns] zeroconf not installed (pip install zeroconf); trying avahi-publish.", flush=True)
    except Exception as exc:  # noqa: BLE001
        print(f"[mdns] zeroconf path failed: {exc}; trying avahi-publish.", flush=True)

    # Preference 2: system avahi-publish.
    try:
        subprocess.run([
            "avahi-publish", "-s", args.name, args.type, str(args.port),
            f"brand=YOUNES path=/",
        ], check=True)
        return 0
    except FileNotFoundError:
        print("[mdns] avahi-publish not found. Skipping mDNS advertisement (auto-discovery still works via embedded candidates + LAN sweep).", flush=True)
        return 0
    except subprocess.CalledProcessError as exc:
        print(f"[mdns] avahi-publish exited {exc.returncode}. Skipping.", flush=True)
        return 0


if __name__ == "__main__":
    sys.exit(main())
