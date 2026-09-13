package com.red.sovereign.voicebridge.session;

import com.red.sovereign.voicebridge.ari.ExternalMediaManager;
import com.red.sovereign.voicebridge.bot.BotSession;
import com.red.sovereign.voicebridge.rtp.RtpSymmetricEndpoint;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class CallSession {

    private final String sessionId;
    private final String appName;
    private final String inboundChannelId;
    private final String dialedNumber;
    private final String callerId;
    private final Instant createdAt = Instant.now();
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.INITIALIZING);

    // Channel IDs
    private String externalMediaChannelId;
    private String snoopChannelId;
    private String inboundBridgeId;
    private String tapBridgeId;

    // RTP endpoints
    private String localRtpAddress;
    private int localRtpPort;
    private InetSocketAddress remoteRtpPeer;

    // Media settings
    private String codec = "PCMU";
    private int ssrc = (int) (System.currentTimeMillis() & 0xFFFFFFFFL);

    // Bot integration
    private BotSession botSession;

    // RTP endpoints
    private transient RtpSymmetricEndpoint inboundRtpEndpoint;
    private transient RtpSymmetricEndpoint outboundRtpEndpoint;

    public CallSession(String sessionId, String appName, String inboundChannelId,
                       String dialedNumber, String callerId) {
        this.sessionId = sessionId;
        this.appName = appName;
        this.inboundChannelId = inboundChannelId;
        this.dialedNumber = dialedNumber;
        this.callerId = callerId;
    }

    public void initializeRtp(String localAddress, int localPort) {
        this.localRtpAddress = localAddress;
        this.localRtpPort = localPort;
    }

    public void setRemoteRtpPeer(String address, int port) {
        // This will be handled by the RTP endpoint
    }

    public void transitionTo(SessionState newState) {
        SessionState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            System.out.println("Session " + sessionId + " state: " + oldState + " -> " + newState);
        }
    }

    public void terminate() {
        state.set(SessionState.TERMINATED);
        // Cleanup RTP endpoints
        if (inboundRtpEndpoint != null) inboundRtpEndpoint.stop();
        if (outboundRtpEndpoint != null) outboundRtpEndpoint.stop();
        if (botSession != null) botSession.close();
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public String getAppName() { return appName; }
    public String getInboundChannelId() { return inboundChannelId; }
    public String getDialedNumber() { return dialedNumber; }
    public String getCallerId() { return callerId; }
    public Instant getCreatedAt() { return createdAt; }
    public SessionState getState() { return state.get(); }

    public String getExternalMediaChannelId() { return externalMediaChannelId; }
    public void setExternalMediaChannelId(String externalMediaChannelId) {
        this.externalMediaChannelId = externalMediaChannelId;
    }

    public String getSnoopChannelId() { return snoopChannelId; }
    public void setSnoopChannelId(String snoopChannelId) { this.snoopChannelId = snoopChannelId; }

    public String getInboundBridgeId() { return inboundBridgeId; }
    public void setInboundBridgeId(String inboundBridgeId) { this.inboundBridgeId = inboundBridgeId; }

    public String getTapBridgeId() { return tapBridgeId; }
    public void setTapBridgeId(String tapBridgeId) { this.tapBridgeId = tapBridgeId; }

    public String getLocalRtpAddress() { return localRtpAddress; }
    public int getLocalRtpPort() { return localRtpPort; }
    public InetSocketAddress getRemoteRtpPeer() { return remoteRtpPeer; }
    public void setRemoteRtpPeer(InetSocketAddress remoteRtpPeer) { this.remoteRtpPeer = remoteRtpPeer; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }

    public int getSsrc() { return ssrc; }
    public void setSsrc(int ssrc) { this.ssrc = ssrc; }

    public Optional<BotSession> getBotSession() {
        return java.util.Optional.ofNullable(botSession);
    }
    public void setBotSession(BotSession botSession) { this.botSession = botSession; }

    public RtpSymmetricEndpoint getInboundRtpEndpoint() { return inboundRtpEndpoint; }
    public void setInboundRtpEndpoint(RtpSymmetricEndpoint inboundRtpEndpoint) { this.inboundRtpEndpoint = inboundRtpEndpoint; }

    public RtpSymmetricEndpoint getOutboundRtpEndpoint() { return outboundRtpEndpoint; }
    public void setOutboundRtpEndpoint(RtpSymmetricEndpoint outboundRtpEndpoint) { this.outboundRtpEndpoint = outboundRtpEndpoint; }

    public enum SessionState {
        INITIALIZING,
        CONNECTING,
        BRIDGING,
        ACTIVE,
        BARGE_IN,
        TERMINATING,
        TERMINATED
    }
}