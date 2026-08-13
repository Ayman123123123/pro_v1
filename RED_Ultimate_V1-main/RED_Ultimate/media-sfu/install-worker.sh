#!/bin/sh
# Official mediasoup install for restricted networks + Docker.
# 1) Try npm's GitHub prebuilt worker (fast).
# 2) If TLS/firewall blocks the download, compile locally WITHOUT io_uring
#    (io_uring is broken under default Docker seccomp — versatica/mediasoup#1435).
set -eu

PYTHON="${PYTHON:-python3}"
export PYTHON

worker_present() {
  find node_modules/mediasoup -type f -name 'mediasoup-worker' 2>/dev/null | grep -q .
}

if [ "${MEDIASOUP_FORCE_LOCAL_BUILD:-}" = "true" ]; then
  echo "[sfu] MEDIASOUP_FORCE_LOCAL_BUILD=true — compiling worker without io_uring"
  export MEDIASOUP_SKIP_WORKER_PREBUILT_DOWNLOAD=true
  export MESON_ARGS="${MESON_ARGS:--Dms_disable_liburing=true}"
  npm ci --omit=dev --no-audit --no-fund
  worker_present || { echo "[sfu] local worker build produced no binary" >&2; exit 1; }
  exit 0
fi

if npm ci --omit=dev --no-audit --no-fund && worker_present; then
  echo "[sfu] mediasoup-worker ready (prebuilt)"
  exit 0
fi

echo "[sfu] prebuilt worker missing — compiling locally without io_uring"
rm -rf node_modules
export MEDIASOUP_SKIP_WORKER_PREBUILT_DOWNLOAD=true
export MESON_ARGS="${MESON_ARGS:--Dms_disable_liburing=true}"
npm ci --omit=dev --no-audit --no-fund
worker_present || { echo "[sfu] local worker build produced no binary" >&2; exit 1; }
echo "[sfu] mediasoup-worker compiled"
