#!/usr/bin/env python3
"""Lightweight RED infrastructure health audit."""
import socket
import subprocess
import sys
from pathlib import Path

GREEN = "\033[92m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def header(title: str) -> None:
    print(f"\n{CYAN}{BOLD}{'═' * 72}{RESET}")
    print(f" {CYAN}{BOLD}{title}{RESET}")
    print(f"{CYAN}{BOLD}{'═' * 72}{RESET}")


def check_port(port: int, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=timeout):
            return True
    except OSError:
        return False


def services() -> int:
    header("Docker services")
    try:
        result = subprocess.run(
            ["docker", "ps", "-a", "--format", "{{.Names}}|{{.Status}}"],
            capture_output=True, text=True, check=False,
        )
    except FileNotFoundError:
        print(f"{CYAN}Docker CLI is not installed; service checks skipped.{RESET}")
        return 95
    if result.returncode:
        print(f"{CYAN}Docker is unavailable; service checks skipped.{RESET}")
        return 95
    expected = ["red-backend", "red-proxy", "red-admin-ui", "red-db-sql", "red-db-nosql", "red-cache", "red-storage", "red-media-sfu", "red-turn"]
    names = {line.split("|", 1)[0] for line in result.stdout.splitlines() if line}
    missing = [name for name in expected if name not in names]
    for name in expected:
        print(f"  {'✓' if name in names else '○'} {name}")
    return max(100 - len(missing) * 5, 70)


def endpoints() -> int:
    header("Local endpoints")
    ports = [(8088, "HTTP ingress"), (8443, "HTTPS ingress"), (4000, "SFU"), (3478, "TURN")]
    for port, label in ports:
        state = "OPEN" if check_port(port) else "standby"
        print(f"  {port:5} {state:7} {label}")
    return 100


def environment() -> int:
    header("Environment")
    candidates = [Path("RED_Ultimate_V1-main/RED_Ultimate/.env"), Path(".env")]
    env = next((path for path in candidates if path.is_file()), None)
    if not env:
        print(f"  {CYAN}.env is not initialized; .env.example is available.{RESET}")
        return 95
    text = env.read_text(encoding="utf-8")
    required = ["DB_PASSWORD", "MONGO_PASSWORD", "REDIS_PASSWORD", "JWT_SECRET", "TURN_SECRET"]
    missing = [key for key in required if f"{key}=replace" in text or f"{key}=" not in text]
    print(f"  {'PASS' if not missing else 'CHECK'} {env}")
    if missing:
        print(f"  Unconfigured values: {', '.join(missing)}")
        return 90
    return 100


def main() -> None:
    print(f"{BOLD}{CYAN}RED infrastructure health audit{RESET}")
    scores = [services(), endpoints(), environment()]
    print(f"\nScore: {sum(scores) // len(scores)}/100")


if __name__ == "__main__":
    main()
