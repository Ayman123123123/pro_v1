FROM andrius/asterisk

COPY extensions.conf /etc/asterisk/extensions.conf
COPY docker-entrypoint.sh /usr/local/bin/red-asterisk-entrypoint
# Windows checkouts may present CRLF even for shell files; normalize inside the image before exec.
RUN sed -i 's/\r$//' /usr/local/bin/red-asterisk-entrypoint /etc/asterisk/extensions.conf \
    && chmod 0755 /usr/local/bin/red-asterisk-entrypoint \
    && rm -f /etc/asterisk/manager.conf /etc/asterisk/pjsip.conf

EXPOSE 5060/udp 5038 8088 10000-10100/udp
ENTRYPOINT ["/usr/local/bin/red-asterisk-entrypoint"]
