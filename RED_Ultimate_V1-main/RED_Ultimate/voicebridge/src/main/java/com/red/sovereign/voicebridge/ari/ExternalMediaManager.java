package com.red.sovereign.voicebridge.ari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.red.sovereign.voicebridge.session.CallSession;
import com.red.sovereign.voicebridge.config.AriProperties;
import com.red.sovereign.voicebridge.config.RtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Component
public class ExternalMediaManager {

    private static final Logger log = LoggerFactory.getLogger(ExternalMediaManager.class);

    private final AriProperties ariProperties;
    private final RtpProperties rtpProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    public ExternalMediaManager(AriProperties ariProperties, RtpProperties rtpProperties) {
        this.ariProperties = ariProperties;
        this.rtpProperties = rtpProperties;
    }

    @PostConstruct
    public void init() {
        String baseUrl = ariProperties.getBaseUrl();
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .build();
    }

    /**
     * Create an ExternalMedia channel for the given call session.
     * Returns the channel ID of the created ExternalMedia channel.
     */
    public Mono<String> createExternalMediaChannel(CallSession session) {
        String appName = ariProperties.getStasisAppName();
        String externalHost = String.format("%s:%d",
            session.getLocalRtpAddress(),
            session.getLocalRtpPort());

        String format = mapCodecToAriFormat(session.getCodec());

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("app", appName);
        requestBody.put("external_host", externalHost);
        requestBody.put("format", format);
        requestBody.put("direction", "both");
        requestBody.put("transport", "udp");
        requestBody.put("encapsulation", "rtp");

        // Add channel variables for RTP peer discovery
        ObjectNode variables = objectMapper.createObjectNode();
        variables.put("VOICEBRIDGE_SESSION_ID", session.getSessionId());
        variables.put("VOICEBRIDGE_RTP_PORT", session.getLocalRtpPort());
        requestBody.set("variables", variables);

        String url = String.format("/channels/externalMedia?app=%s", appName);

        log.info("Creating ExternalMedia channel for session {}: host={}, format={}, codec={}",
            session.getSessionId(), externalHost, format, session.getCodec());

        return webClient.post()
            .uri(url)
            .bodyValue(requestBody.toString())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(response -> {
                String channelId = response.get("id").asText();
                log.info("ExternalMedia channel created: {} for session {}", channelId, session.getSessionId());
                return channelId;
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Failed to create ExternalMedia channel: {} - {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
                return Mono.error(new AriException("ExternalMedia creation failed: " + ex.getMessage()));
            });
    }

    /**
     * Get the UNICASTRTP peer variables from the ExternalMedia channel.
     * These tell us where to send outbound RTP.
     */
    public Mono<UnicastRtpPeer> getUnicastRtpPeer(String channelId) {
        String url = String.format("/channels/%s/variables", channelId);

        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(vars -> {
                String peerAddr = vars.get("UNICASTRTP_PEER_ADDRESS").asText();
                int peerPort = vars.get("UNICASTRTP_PEER_PORT").asInt();
                String localAddr = vars.get("UNICASTRTP_LOCAL_ADDRESS").asText();
                int localPort = vars.get("UNICASTRTP_LOCAL_PORT").asInt();

                log.info("UNICASTRTP peer for {}: {}:{} (local {}:{})",
                    channelId, peerAddr, peerPort, localAddr, localPort);

                return new UnicastRtpPeer(peerAddr, peerPort, localAddr, localPort);
            })
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.error("Failed to get UNICASTRTP vars for {}: {}", channelId, ex.getMessage());
                return Mono.error(new AriException("UNICASTRTP vars missing: " + ex.getMessage()));
            });
    }

    /**
     * Delete the ExternalMedia channel.
     */
    public Mono<Void> deleteExternalMediaChannel(String channelId) {
        String url = String.format("/channels/%s", channelId);

        return webClient.delete()
            .uri(url)
            .retrieve()
            .toBodilessEntity()
            .then()
            .doOnSuccess(v -> log.info("Deleted ExternalMedia channel: {}", channelId))
            .onErrorResume(WebClientResponseException.class, ex -> {
                log.warn("Failed to delete ExternalMedia channel {}: {}", channelId, ex.getMessage());
                return Mono.empty();
            });
    }

    private String mapCodecToAriFormat(String codec) {
        return switch (codec.toUpperCase()) {
            case "PCMU", "ULAW" -> "ulaw";
            case "PCMA", "ALAW" -> "alaw";
            case "G722" -> "g722";
            case "OPUS" -> "opus";
            default -> "ulaw";
        };
    }

    public record UnicastRtpPeer(String peerAddress, int peerPort, String localAddress, int localPort) {}

    public static class AriException extends RuntimeException {
        public AriException(String message) { super(message); }
        public AriException(String message, Throwable cause) { super(message, cause); }
    }
}