package com.red.sovereign.voicebridge.ari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.red.sovereign.voicebridge.session.CallSession;
import com.red.sovereign.voicebridge.config.AriProperties;
import com.red.sovereign.voicebridge.config.RtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

@Component
public class AriBridgeImpl {

    private static final Logger log = LoggerFactory.getLogger(AriBridgeImpl.class);

    private final AriProperties ariProperties;
    private final RtpProperties rtpProperties;
    private final ExternalMediaManager externalMediaManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    public AriBridgeImpl(AriProperties ariProperties,
                         RtpProperties rtpProperties,
                         ExternalMediaManager externalMediaManager) {
        this.ariProperties = ariProperties;
        this.rtpProperties = rtpProperties;
        this.externalMediaManager = externalMediaManager;
    }

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .baseUrl(ariProperties.getBaseUrl())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    /**
     * Create the dual-bridge duplex media graph for a call session.
     * 
     * Graph structure:
     * - Inbound Bridge (mixing): inbound_channel + external_media_out
     * - Tap Bridge (mixing): snoop_inbound + external_media_in
     * 
     * This is the VoiceBridge "duplex trick" for full-duplex media.
     */
    public CompletableFuture<Void> createDuplexBridge(CallSession session) {
        String appName = ariProperties.getStasisAppName();
        String inboundChannelId = session.getInboundChannelId();
        String extMediaChannelId = session.getExternalMediaChannelId();

        log.info("Creating duplex bridge for session {}", session.getSessionId());

        return createMixingBridge(appName, "inbound-" + session.getSessionId())
            .thenCompose(inboundBridgeId -> {
                session.setInboundBridgeId(inboundBridgeId);
                log.info("Created inbound bridge: {} for session {}", inboundBridgeId, session.getSessionId());

                // Add inbound channel to inbound bridge
                return addChannelToBridge(appName, inboundBridgeId, inboundChannelId)
                    .thenCompose(v -> {
                        // Add ExternalMedia channel to inbound bridge (for OUTBOUND audio from bot)
                        return addChannelToBridge(appName, inboundBridgeId, extMediaChannelId);
                    })
                    .thenCompose(v -> {
                        // Create snoop inbound channel on inbound channel
                        return createSnoopChannel(appName, inboundChannelId, "snoop-" + session.getSessionId());
                    })
                    .thenCompose(snoopChannelId -> {
                        session.setSnoopChannelId(snoopChannelId);
                        log.info("Created snoop channel: {} for session {}", snoopChannelId, session.getSessionId());

                        // Create tap bridge for inbound audio capture
                        return createMixingBridge(appName, "tap-" + session.getSessionId())
                            .thenCompose(tapBridgeId -> {
                                session.setTapBridgeId(tapBridgeId);
                                log.info("Created tap bridge: {} for session {}", tapBridgeId, session.getSessionId());

                                // Add snoop channel to tap bridge (for INBOUND audio to bot)
                                return addChannelToBridge(appName, tapBridgeId, snoopChannelId)
                                    .thenCompose(v -> {
                                        // Add same ExternalMedia channel to tap bridge (for INBOUND audio from caller)
                                        return addChannelToBridge(appName, tapBridgeId, extMediaChannelId);
                                    })
                                    .thenCompose(v -> {
                                        // Get UNICASTRTP peer for outbound RTP
                                        return externalMediaManager.getUnicastRtpPeer(extMediaChannelId);
                                    })
                                    .thenAccept(peer -> {
                                        session.setRemoteRtpPeer(peer.peerAddress(), peer.peerPort());
                                        log.info("Duplex bridge ready for session {}: remote RTP peer {}:{}",
                                            session.getSessionId(), peer.peerAddress(), peer.peerPort());
                                    });
                            });
                    });
            })
            .toFuture()
            .exceptionally(ex -> {
                log.error("Failed to create duplex bridge for session {}", session.getSessionId(), ex);
                throw new RuntimeException("Duplex bridge creation failed", ex);
            });
    }

    private Mono<String> createMixingBridge(String appName, String bridgeName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("type", "mixing");
        body.put("name", bridgeName);
        body.put("mixing", true);
        body.put("dtmf_events", true);
        body.put("proxy_media", true);

        String url = String.format("/bridges?app=%s", appName);

        return webClient.post()
            .uri(url)
            .bodyValue(body.toString())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(resp -> resp.get("id").asText())
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Failed to create mixing bridge: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.error(new AriException("Bridge creation failed: " + ex.getMessage()));
            });
    }

    private Mono<Void> addChannelToBridge(String appName, String bridgeId, String channelId) {
        String url = String.format("/bridges/%s/addChannel?app=%s&channel=%s", bridgeId, appName, channelId);

        return webClient.post()
            .uri(url)
            .retrieve()
            .toBodilessEntity()
            .then()
            .doOnSuccess(v -> log.info("Added channel {} to bridge {}", channelId, bridgeId))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Failed to add channel {} to bridge {}: {} - {}",
                    channelId, bridgeId, ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.error(new AriException("Add channel to bridge failed: " + ex.getMessage()));
            });
    }

    private Mono<String> createSnoopChannel(String appName, String channelId, String snoopName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("channel", channelId);
        body.put("spy", "in");  // Only spy on inbound audio (caller -> Asterisk)
        body.put("whisper", "out"); // Allow whispering outbound
        body.put("app", appName);
        body.put("appArgs", "snoop-" + snoopName);

        String url = String.format("/channels?app=%s", appName);

        return webClient.post()
            .uri(url)
            .bodyValue(body.toString())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(resp -> resp.get("id").asText())
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Failed to create snoop channel: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.error(new AriException("Snoop channel creation failed: " + ex.getMessage()));
            });
    }

    public CompletableFuture<Void> teardownBridge(CallSession session) {
        String appName = ariProperties.getStasisAppName();

        log.info("Tearing down bridge for session {}", session.getSessionId());

        return CompletableFuture.allOf(
            // Delete bridges
            deleteBridge(appName, session.getInboundBridgeId()),
            deleteBridge(appName, session.getTapBridgeId()),
            // Delete snoop channel
            deleteChannel(appName, session.getSnoopChannelId()),
            // Delete ExternalMedia channel
            externalMediaManager.deleteExternalMediaChannel(session.getExternalMediaChannelId())
        ).exceptionally(ex -> {
            log.warn("Error during bridge teardown for session {}", session.getSessionId(), ex);
            return null;
        });
    }

    private CompletableFuture<Void> deleteBridge(String appName, String bridgeId) {
        if (bridgeId == null) return CompletableFuture.completedFuture(null);

        String url = String.format("/bridges/%s?app=%s", bridgeId, appName);
        return webClient.delete()
            .uri(url)
            .retrieve()
            .toBodilessEntity()
            .then()
            .doOnSuccess(v -> log.info("Deleted bridge: {}", bridgeId))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.warn("Failed to delete bridge {}: {}", bridgeId, ex.getMessage());
                return Mono.empty();
            })
            .toFuture();
    }

    private CompletableFuture<Void> deleteChannel(String appName, String channelId) {
        if (channelId == null) return CompletableFuture.completedFuture(null);

        String url = String.format("/channels/%s?app=%s", channelId, appName);
        return webClient.delete()
            .uri(url)
            .retrieve()
            .toBodilessEntity()
            .then()
            .doOnSuccess(v -> log.info("Deleted channel: {}", channelId))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.warn("Failed to delete channel {}: {}", channelId, ex.getMessage());
                return Mono.empty();
            })
            .toFuture();
    }

    public static class AriException extends RuntimeException {
        public AriException(String message) { super(message); }
        public AriException(String message, Throwable cause) { super(message, cause); }
    }
}