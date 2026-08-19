#!/bin/bash
set -euo pipefail

echo "=== Compiling chan_dongle for Asterisk 22 ==="

apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    wget \
    autoconf \
    automake \
    libtool \
    pkg-config \
    libusb-1.0-0-dev \
    && rm -rf /var/lib/apt/lists/*

cd /tmp

echo "Downloading Asterisk 22.3.0 source..."
wget -q "https://downloads.asterisk.org/pub/telephony/asterisk/asterisk-22.3.0.tar.gz"
tar -xzf asterisk-22.3.0.tar.gz

echo "Downloading chan_dongle source..."
# Try multiple sources
wget -q "https://github.com/wdoekes/chan_dongle/archive/refs/heads/master.tar.gz" -O chan_dongle.tar.gz \
    || wget -q "https://codeload.github.com/wdoekes/chan_dongle/tar.gz/master" -O chan_dongle.tar.gz \
    || wget -q "https://github.com/jstasiak/chan_dongle/archive/refs/heads/master.tar.gz" -O chan_dongle.tar.gz \
    || wget -q "https://github.com/asterisk-chan-dongle/chan_dongle/archive/refs/heads/master.tar.gz" -O chan_dongle.tar.gz

tar -xzf chan_dongle.tar.gz
mv chan_dongle-* chan_dongle

cd chan_dongle
aclocal && autoconf && automake -a
./configure --with-asterisk=/tmp/asterisk-22.3.0
make -j$(nproc)

cp chan_dongle.so /out/
echo "=== SUCCESS: chan_dongle.so compiled and copied to /out ==="
ls -la /out/chan_dongle.so