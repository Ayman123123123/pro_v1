FROM andrius/asterisk

# Install missing libraries for format_ogg_vorbis and other modules
RUN apt-get update && apt-get install -y --no-install-recommends \
    libvorbisenc2 \
    libvorbisfile3 \
    && rm -rf /var/lib/apt/lists/*

COPY extensions.conf /etc/asterisk/extensions.conf
COPY modules.conf /etc/asterisk/modules.conf
COPY docker-entrypoint.sh /usr/local/bin/red-asterisk-entrypoint
# Windows checkouts may present CRLF even for shell files; normalize inside the image before exec.
RUN sed -i 's/\r$//' /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/extensions.conf \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint \
    && rm -f /etc/asterisk/manager.conf /etc/asterisk/pjsip.conf \
    && rm -f /etc/asterisk/extensions.ael /etc/asterisk/extensions.lua /etc/asterisk/extensions_minivm.conf

EXPOSE 5060/udp 5038 8088 10000-10100/udp
ENTRYPOINT ["/usr/local/bin/red-asterisk-entrypoint"]
