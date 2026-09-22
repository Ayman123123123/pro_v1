package com.red.sovereign.voicebridge.rtp;

import com.red.sovereign.voicebridge.config.RtpProperties;
import com.red.sovereign.voicebridge.session.CallSession;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
public class RtpSymmetricEndpoint {

    private static final Logger log = LoggerFactory.getLogger(RtpSymmetricEndpoint.class);

    private final RtpProperties rtpProperties;
    private final RtpPacketizer packetizer;
    private final MeterRegistry meterRegistry;
    private final ExecutorService receiveExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    private final Timer receiveTimer;
    private final Timer sendTimer;
    private final Timer packetLossTimer;

    private final AtomicReference<InetSocketAddress> remotePeer = new AtomicReference<>();
    private final AtomicBoolean learnMode = new AtomicBoolean(true);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private DatagramSocket rtcpSocket;
    private CallSession session;
    private Consumer<byte[]> audioConsumer;
    private Thread receiveThread;

    public RtpSymmetricEndpoint(RtpProperties rtpProperties,
                                RtpPacketizer packetizer,
                                MeterRegistry meterRegistry) {
        this.rtpProperties = rtpProperties;
        this.packetizer = packetizer;
        this.meterRegistry = meterRegistry;

        this.receiveTimer = Timer.builder("voicebridge.rtp.receive")
            .tag("direction", "inbound")
            .register(meterRegistry);
        this.sendTimer = Timer.builder("voicebridge.rtp.send")
            .tag("direction", "outbound")
            .register(meterRegistry);
        this.packetLossTimer = Timer.builder("voicebridge.rtp.loss")
            .register(meterRegistry);
    }

    public void start(CallSession session, String localAddress, int localPort,
                      Consumer<byte[]> audioConsumer) {
        this.session = session;
        this.audioConsumer = audioConsumer;

        try {
            this.socket = new DatagramSocket(new java.net.InetSocketAddress(localAddress, session.getLocalRtpPort()));
            this.socket.setSoTimeout(100);
            this.socket.setReuseAddress(true);

            int rtcpPort = session.getLocalRtpPort() + 1;
            this.rtcpSocket = new DatagramSocket(new java.net.InetSocketAddress(localAddress, rtcpPort));
            this.rtcpSocket.setSoTimeout(100);

            log.info("RTP endpoint started: {}:{} (RTCP: {})",
                localAddress, session.getLocalRtpPort(), session.getLocalRtpPort() + 1);

            this.running.set(true);
            this.learnMode.set(rtpProperties.getLearnPeer());

            this.receiveThread = new Thread(this::receiveLoop, "rtp-receive-" + session.getSessionId());
            this.receiveThread.start();

            log.info("RTP symmetric endpoint started for session {} on {}:{}",
                session.getSessionId(), localAddress, session.getLocalRtpPort());

        } catch (Exception e) {
            log.error("Failed to start RTP endpoint for session {}", session.getSessionId(), e);
            throw new RuntimeException("Failed to start RTP endpoint", e);
        }
    }

    public void setRemotePeer(String address, int port) {
        InetSocketAddress peer = new InetSocketAddress(address, port);
        InetSocketAddress oldPeer = remotePeer.getAndSet(peer);
        learnMode.set(false);
        log.info("RTP remote peer set for session {}: {} -> {}", session.getSessionId(), oldPeer, peer);
    }

    public void sendAudio(byte[] pcm16Audio, long timestamp, int sequence, int ssrc) {
        if (!running.get() || session == null) return;

        InetSocketAddress peer = remotePeer.get();
        if (peer == null) {
            log.warn("Cannot send audio: no remote peer for session {}", session.getSessionId());
            return;
        }

        sendExecutor.submit(() -> {
            try {
                int payloadType = packetizer.getPayloadTypeForCodec(session.getCodec());
                ByteBuffer packet = packetizer.packetize(pcm16Audio, sequence, timestamp, session.getSsrc(), payloadType);

                DatagramPacket packet = new DatagramPacket(
                    packet.array(), packet.remaining(), remotePeer.get());

                sendTimer.record(() -> {
                    socket.send(packet);
                });

            } catch (Exception e) {
                log.warn("Failed to send RTP packet for session {}", session.getSessionId(), e);
                meterRegistry.counter("voicebridge.rtp.send.errors").increment();
            }
        });
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                socket.receive(packet);
                InetSocketAddress sender = (InetSocketAddress) packet.getSocketAddress();

                // Learn remote peer if in learn mode
                if (learnMode.get()) {
                    remotePeer.set(sender);
                    learnMode.set(false);
                    log.info("RTP learned remote peer for session {}: {}", session.getSessionId(), sender);
                }

                // Verify sender matches expected peer (unless in learn mode)
                InetSocketAddress expectedPeer = remotePeer.get();
                if (expectedPeer != null && !sender.equals(expectedPeer)) {
                    log.warn("RTP packet from unexpected source: {} (expected {})", sender, expectedPeer);
                    packetLossTimer.record(() -> {}); // Count as loss
                    continue;
                }

                // Parse RTP packet
                ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), packet.getOffset(), packet.getLength());
                RtpPacketizer.ParsedRtpPacket rtpPacket;
                try {
                    rtpPacket = packetizer.parse(buffer);
                } catch (Exception e) {
                    log.warn("Failed to parse RTP packet from {}: {}", sender, e.getMessage());
                    continue;
                }

                // Extract audio payload (PCM16 expected after decoding)
                byte[] payload = rtpPacket.payload();

                // Record metrics
                receiveTimer.record(() -> {});

                // Pass to audio consumer (for STT/bot)
                if (audioConsumer != null) {
                    audioConsumer.accept(payload);
                }

            } catch (java.net.SocketTimeoutException e) {
                // Normal timeout, continue
            } catch (Exception e) {
                if (running.get()) {
                    log.warn("RTP receive error for session {}", session.getSessionId(), e);
                }
            }
        }
    }

    public void stop() {
        running.set(false);
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (rtcpSocket != null && !rtcpSocket.isClosed()) {
            rtcpSocket.close();
        }
        receiveExecutor.shutdownNow();
        sendExecutor.shutdownNow();
        log.info("RTP symmetric endpoint stopped for session {}", session != null ? session.getSessionId() : "unknown");
    }

    public InetSocketAddress getRemotePeer() {
        return remotePeer.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}