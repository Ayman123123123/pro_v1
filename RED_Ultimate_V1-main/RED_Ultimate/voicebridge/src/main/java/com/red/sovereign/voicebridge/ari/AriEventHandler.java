package com.red.sovereign.voicebridge.ari;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red.sovereign.voicebridge.session.CallSession;
import com.red.sovereign.voicebridge.session.CallSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AriEventHandler {

    private static final Logger log = LoggerFactory.getLogger(AriEventHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CallSessionManager sessionManager;
    private final ExternalMediaManager externalMediaManager;
    private final AriBridgeImpl bridgeImpl;

    // Event type handlers
    private final Map<String, EventHandler> handlers = new ConcurrentHashMap<>();

    public AriEventHandler(CallSessionManager sessionManager,
                           ExternalMediaManager externalMediaManager,
                           AriBridgeImpl bridgeImpl) {
        this.sessionManager = sessionManager;
        this.externalMediaManager = externalMediaManager;
        this.bridgeImpl = bridgeImpl;

        // Register event handlers
        handlers.put("StasisStart", this::handleStasisStart);
        handlers.put("StasisEnd", this::handleStasisEnd);
        handlers.put("ChannelStateChange", this::handleChannelStateChange);
        handlers.put("ChannelDtmfReceived", this::handleDtmf);
        handlers.put("ChannelHangupRequest", this::handleHangupRequest);
        handlers.put("ChannelEnteredBridge", this::handleChannelEnteredBridge);
        handlers.put("ChannelLeftBridge", this::handleChannelLeftBridge);
    }

    public void handleEvent(String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String eventType = root.get("type").asText();
            String appName = root.get("application").asText();

            EventHandler handler = handlers.get(eventType);
            if (handler != null) {
                handler.handle(root, appName);
            } else {
                log.debug("Unhandled ARI event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error parsing ARI event", e);
        }
    }

    private void handleStasisStart(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        JsonNode channel = event.get("channel");
        String dialedNumber = channel.get("dialplan").get("exten").asText("");
        String callerId = channel.get("caller").get("number").asText("");

        log.info("StasisStart: channel={}, app={}, exten={}, caller={}", channelId, appName, dialedNumber, callerId);

        // Create call session
        CallSession session = sessionManager.createSession(channelId, appName, dialedNumber, callerId);
        session.setInboundChannelId(channelId);

        // Create ExternalMedia channel for duplex audio
        externalMediaManager.createExternalMediaChannel(session)
            .thenAccept(extMediaChannelId -> {
                session.setExternalMediaChannelId(extMediaChannelId);
                // Bridge inbound channel with ExternalMedia (both directions)
                bridgeImpl.createDuplexBridge(session);
            })
            .exceptionally(ex -> {
                log.error("Failed to create ExternalMedia for session {}", session.getSessionId(), ex);
                sessionManager.terminateSession(session.getSessionId());
                return null;
            });
    }

    private void handleStasisEnd(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        log.info("StasisEnd: channel={}, app={}", channelId, appName);

        sessionManager.findSessionByChannelId(channelId)
            .ifPresent(session -> sessionManager.terminateSession(session.getSessionId()));
    }

    private void handleChannelStateChange(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        String state = event.get("channel").get("state").asText();
        log.debug("ChannelStateChange: channel={}, state={}", channelId, state);
    }

    private void handleDtmf(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        String digit = event.get("digit").asText();
        log.info("DTMF received: channel={}, digit={}", channelId, digit);

        sessionManager.findSessionByChannelId(channelId)
            .ifPresent(session -> session.getBotSession().ifPresent(bot -> bot.handleDtmf(digit)));
    }

    private void handleHangupRequest(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        log.info("HangupRequest: channel={}", channelId);
        sessionManager.findSessionByChannelId(channelId)
            .ifPresent(session -> sessionManager.terminateSession(session.getSessionId()));
    }

    private void handleChannelEnteredBridge(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        String bridgeId = event.get("bridge").get("id").asText();
        log.debug("ChannelEnteredBridge: channel={}, bridge={}", channelId, bridgeId);
    }

    private void handleChannelLeftBridge(JsonNode event, String appName) {
        String channelId = event.get("channel").get("id").asText();
        String bridgeId = event.get("bridge").get("id").asText();
        log.debug("ChannelLeftBridge: channel={}, bridge={}", channelId, bridgeId);
    }

    @FunctionalInterface
    interface EventHandler {
        void handle(JsonNode event, String appName);
    }
}