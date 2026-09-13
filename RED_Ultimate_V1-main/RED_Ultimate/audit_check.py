#!/usr/bin/env python3
"""Small repository sanity audit for the canonical RED layout."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
checks = {
    "canonical Android module": ROOT / "red-app",
    "backend": ROOT / "backend-server",
    "Compose runtime": ROOT / "docker-compose.yml",
    "admin dashboard": ROOT / "admin_dashboard",
    "media SFU": ROOT / "media-sfu",
}

for label, path in checks.items():
    status = "PASS" if path.exists() else "FAIL"
    print(f"[{status}] {label}: {path}")

print(f"Files: {sum(1 for p in ROOT.rglob('*') if p.is_file())}")
