package com.red.sovereign.voicebridge.rtp;

import com.red.sovereign.voicebridge.config.RtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RtpPortAllocator {

    private static final Logger log = LoggerFactory.getLogger(RtpPortAllocator.class);

    private final RtpProperties rtpProperties;
    private final AtomicInteger nextPort;
    private final ConcurrentHashMap<Integer, DatagramSocket> allocatedPorts = new ConcurrentHashMap<>();

    public RtpPortAllocator(RtpProperties rtpProperties) {
        this.rtpProperties = rtpProperties;
        this.nextPort = new AtomicInteger(rtpProperties.getPortRangeStart());
    }

    @PostConstruct
    public void init() {
        log.info("RTP Port Allocator initialized: range={}-{}, frameSize={}ms",
            rtpProperties.getPortRangeStart(), rtpProperties.getPortRangeEnd(),
            rtpProperties.getFrameSizeMs());
    }

    @PreDestroy
    public void shutdown() {
        allocatedPorts.values().forEach(DatagramSocket::close);
        allocatedPorts.clear();
        log.info("RTP Port Allocator shutdown, released {} ports", allocatedPorts.size());
    }

    /**
     * Allocate a pair of RTP/RTCP ports (even RTP, odd RTCP).
     * Returns the RTP port (even number).
     */
    public synchronized AllocatedPortPair allocatePair() {
        int maxAttempts = rtpProperties.getPortRangeEnd() - rtpProperties.getPortRangeStart();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int rtpPort = nextPort.getAndIncrement();
            if (rtpPort > rtpProperties.getPortRangeEnd()) {
                nextPort.set(rtpProperties.getPortRangeStart());
                rtpPort = nextPort.getAndIncrement();
            }

            // Ensure RTP port is even
            if (rtpPort % 2 != 0) {
                rtpPort++;
                if (rtpPort > rtpProperties.getPortRangeEnd()) {
                    rtpPort = rtpProperties.getPortRangeStart() + (rtpProperties.getPortRangeStart() % 2);
                }
            }

            int rtcpPort = rtpPort + 1;

            if (rtpPort > rtpProperties.getPortRangeEnd() || rtcpPort > rtpProperties.getPortRangeEnd()) {
                continue;
            }

            try {
                DatagramSocket rtpSocket = new DatagramSocket(rtpPort);
                DatagramSocket rtcpSocket = new DatagramSocket(rtcpPort);

                rtpSocket.setSoTimeout(0);
                rtcpSocket.setSoTimeout(0);
                rtpSocket.setReuseAddress(true);
                rtcpSocket.setReuseAddress(true);

                // Set buffer sizes for low latency
                int bufferSize = 65536;
                rtpSocket.setReceiveBufferSize(bufferSize);
                rtpSocket.setSendBufferSize(bufferSize);
                rtcpSocket.setReceiveBufferSize(bufferSize);
                rtcpSocket.setSendBufferSize(bufferSize);

                if (allocatedPorts.putIfAbsent(rtpPort, rtpSocket) != null ||
                    allocatedPorts.putIfAbsent(rtcpPort, rtcpSocket) != null) {
                    // Race condition - close and retry
                    rtpSocket.close();
                    rtcpSocket.close();
                    continue;
                }

                log.debug("Allocated RTP/RTCP port pair: {}/{}", rtpPort, rtcpPort);
                return new AllocatedPortPair(rtpPort, rtcpPort, rtpSocket, rtcpSocket);

            } catch (SocketException e) {
                // Port busy, try next
                log.trace("Port {}/{} busy, trying next", rtpPort, rtcpPort);
            }
        }

        throw new IllegalStateException("No available RTP port pairs in range " +
            rtpProperties.getPortRangeStart() + "-" + rtpProperties.getPortRangeEnd());
    }

    public void releasePair(AllocatedPortPair pair) {
        if (pair == null) return;

        DatagramSocket rtpSocket = allocatedPorts.remove(pair.rtpPort());
        DatagramSocket rtcpSocket = allocatedPorts.remove(pair.rtcpPort());

        if (rtpSocket != null && !rtpSocket.isClosed()) {
            rtpSocket.close();
        }
        if (rtcpSocket != null && !rtcpSocket.isClosed()) {
            rtcpSocket.close();
        }

        log.debug("Released RTP/RTCP port pair: {}/{}", pair.rtpPort(), pair.rtcpPort());
    }

    public int getAllocatedCount() {
        return allocatedPorts.size() / 2;
    }

    public record AllocatedPortPair(int rtpPort, int rtcpPort,
                                    DatagramSocket rtpSocket, DatagramSocket rtcpSocket) {
        public void close() {
            if (rtpSocket != null && !rtpSocket.isClosed()) rtpSocket.close();
            if (rtcpSocket != null && !rtcpSocket.isClosed()) rtcpSocket.close();
        }
    }
}