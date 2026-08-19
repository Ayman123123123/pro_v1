# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# RED SOVEREIGN — PSTN Gateway (Asterisk 22 + chan_dongle)
# Builds on andrius/asterisk base, compiles chan_dongle module
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# ═════════════════════════════════════════════════════════════════════════════
# STAGE 1: Compile chan_dongle against Asterisk headers
# ═════════════════════════════════════════════════════════════════════════════
FROM andrius/asterisk AS dongle-builder

ENV DEBIAN_FRONTEND=noninteractive
ENV CHAN_DONGLE_VERSION=2.0.0

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    wget \
    autoconf \
    automake \
    libtool \
    pkg-config \
    libusb-1.0-0-dev \
    && rm -rf /var/lib/apt/lists/*

# Get Asterisk source headers (matching the version in base image)
WORKDIR /usr/src
RUN ASTERISK_VERSION=$(asterisk -V | sed 's/Asterisk \([0-9.]*\).*/\1/') \
    && wget -q "https://downloads.asterisk.org/pub/telephony/asterisk/asterisk-${ASTERISK_VERSION}.tar.gz" \
    && tar -xzf "asterisk-${ASTERISK_VERSION}.tar.gz" \
    && rm "asterisk-${ASTERISK_VERSION}.tar.gz"

# Download chan_dongle source (use tarball to avoid git auth issues)
WORKDIR /usr/src
RUN wget -q "https://github.com/wdoekes/chan_dongle/archive/refs/tags/${CHAN_DONGLE_VERSION}.tar.gz" -O chan_dongle.tar.gz \
    || wget -q "https://github.com/wdoekes/chan_dongle/archive/refs/heads/master.tar.gz" -O chan_dongle.tar.gz \
    && tar -xzf chan_dongle.tar.gz \
    && mv chan_dongle-* chan_dongle \
    && rm chan_dongle.tar.gz

WORKDIR /usr/src/chan_dongle
RUN aclocal && autoconf && automake -a \
    && ./configure --with-asterisk=/usr/src/asterisk-${ASTERISK_VERSION} \
    && make -j$(nproc)

# ══════════════════════════════════════════════════════════════════════════════
# STAGE 2: Runtime image (andrius/asterisk + chan_dongle.so)
# ══════════════════════════════════════════════════════════════════════════════
FROM andrius/asterisk

# Install USB/serial utilities
RUN apt-get update && apt-get install -y --no-install-recommends \
    usbutils \
    usb-modeswitch \
    kmod \
    && rm -rf /var/lib/apt/lists/*

# Copy compiled chan_dongle module
COPY --from=dongle-builder /usr/src/chan_dongle/chan_dongle.so /usr/lib/asterisk/modules/chan_dongle.so

# Verify module loads
RUN asterisk -c "module load chan_dongle.so" 2>&1 | head -5 || true

# ══════════════════════════════════════════════════════════════════════════════
# Application configuration
# ═════════════════════════════════════════════════════════════════════════════
COPY extensions.conf /etc/asterisk/extensions.conf
COPY modules.conf /etc/asterisk/modules.conf
COPY http.conf /etc/asterisk/http.conf
COPY rtp.conf /etc/asterisk/rtp.conf
COPY dongle.conf /etc/asterisk/dongle.conf
COPY docker-entrypoint.sh /usr/local/bin/red-asterisk-entrypoint

RUN sed -i 's/\r$//' /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/*.conf \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint \
    && rm -f /etc/asterisk/manager.conf /etc/asterisk/pjsip.conf \
    && rm -f /etc/asterisk/extensions.ael /etc/asterisk/extensions.lua /etc/asterisk/extensions_minivm.conf

EXPOSE 5060/udp 5038 8088 8089 10000-10100/udp

ENTRYPOINT ["/usr/local/bin/red-asterisk-entrypoint"]