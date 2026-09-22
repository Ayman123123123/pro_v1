package com.red.sovereign.voicebridge.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.red.sovereign.voicebridge.config.BotProperties;
import com.red.sovereign.voicebridge.rtp.RtpPacketizer;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.http.client.HttpClient;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OpenAiRealtimeBotSession implements BotSession {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeBotSession.class);

    private final BotProperties botProperties;
    private final RtpPacketizer packetizer;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private reactor.netty.http.client.WebSocketClient webSocketClient;
    private reactor.netty.http.client.WebSocketSession wsSession;
    private final Sinks.Many<String> outboundSink = Sinks.many().unicast().onBackpressureBuffer();
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean truncating = new AtomicBoolean(false);

    private String sessionId;

    public OpenAiRealtimeBotSession(BotProperties botProperties, RtpPacketizer packetizer, MeterRegistry meterRegistry) {
        this.botProperties = botProperties;
        this.packetizer = packetizer;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        if (!"openai-realtime".equals(botProperties.getProvider())) {
            log.info("OpenAI Realtime bot not configured as provider, skipping initialization");
            return;
        }
        log.info("OpenAI Realtime Bot Session initialized");
    }

    @Override
    public void start() throws Exception {
        if (active.getAndSet(true)) {
            return;
        }

        this.sessionId = "bot-" + System.currentTimeMillis();
        log.info("Starting OpenAI Realtime bot session: {}", sessionId);

        String wsUrl = botProperties.getOpenaiRealtimeUrl() + "?model=" + botProperties.getOpenaiModel();

        HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(30))
            .wiretap(true);

        webSocketClient = new ReactorNettyWebSocketClient(httpClient);

        wsSession = webSocketClient.execute(wsUrl, webSocketSession -> {
            // Send session configuration
            sendSessionConfig();

            // Handle incoming messages
            return webSocketSession.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .doOnNext(this::handleIncomingMessage)
                .then()
                .thenMany(outboundSink.asFlux()
                    .map(webSocketSession::textMessage)
                    .doOnError(e -> log.error("Error sending to OpenAI", e)));
        }).block();

        log.info("OpenAI Realtime WebSocket connected: {}", sessionId);
    }

    private void sendSessionConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("type", "session.update");
        ObjectNode session = config.putObject("session");
        session.put("modalities", objectMapper.createArrayNode().add("audio").add("text"));
        session.put("instructions", botProperties.getSystemPrompt());
        session.put("voice", "alloy");
        session.put("input_audio_format", "pcm16");
        session.put("output_audio_format", "pcm16");
        session.put("input_audio_transcription", objectMapper.createObjectNode().put("model", "whisper-1"));
        session.put("turn_detection", objectMapper.createObjectNode()
            .put("type", "server_vad")
            .put("threshold", botProperties.getVadThreshold())
            .put("prefix_padding_ms", botProperties.getVadPrefixPaddingMs())
            .put("silence_duration_ms", botProperties.getVadSilenceDurationMs()));
        session.put("temperature", botProperties.getTemperature());
        session.put("max_response_output_tokens", botProperties.getMaxTokens());

        sendMessage(config.toString());
    }

    @Override
    public void handleAudioInput(byte[] pcm16Audio) {
        if (!active.get() || truncating.get()) return;

        // Send audio to OpenAI Realtime API as input_audio_buffer.append
        ObjectNode append = objectMapper.createObjectNode();
        append.put("type", "input_audio_buffer.append");
        // Convert PCM16 to base64
        String base64Audio = java.util.Base64.getEncoder().encodeToString(pcm16Audio);
        append.put("audio", base64Audio);

        sendMessage(append.toString());
    }

    @Override
    public void handleDtmf(String digit) {
        log.info("DTMF received in bot session: {}", digit);
        // Could trigger specific bot behaviors
    }

    @Override
    public void sendAudioOutput(byte[] pcm16Audio) {
        // This is called when we need to send audio back to Asterisk
        // The actual sending is handled by the RTP layer
        // Here we would queue it for the RTP sender
    }

    @Override
    public void handleTruncation() {
        log.info("Truncation/barge-in triggered for bot session {}", sessionId);
        truncating.set(true);

        // Send truncation command to OpenAI
        ObjectNode truncate = objectMapper.createObjectNode();
        truncate.put("type", "response.cancel");
        sendMessage(truncate.toString());

        // Clear input buffer
        ObjectNode clear = objectMapper.createObjectNode();
        clear.put("type", "input_audio_buffer.clear");
        sendMessage(clear.toString());

        // Reset truncating flag after a short delay
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            truncating.set(false);
        }).start();
    }

    @Override
    public void close() {
        if (active.getAndSet(false)) {
            log.info("Closing OpenAI Realtime bot session: {}", sessionId);
            if (wsSession != null) {
                wsSession.close().block();
            }
            if (outboundSink != null) {
                outboundSink.tryEmitComplete();
            }
        }
    }

    @Override
    public boolean isActive() {
        return active.get() && wsSession != null && wsSession.isOpen();
    }

    @Override
    public String getSessionId() {
        return sessionId != null ? sessionId : "openai-bot-" + System.currentTimeMillis();
    }

    private void sendMessage(String message) {
        if (wsSession != null && wsSession.isOpen()) {
            wsSession.send(Mono.just(wsSession.textMessage(message))).block();
        }
    }

    private void handleIncomingMessage(String message) {
        try {
            // Parse OpenAI Realtime events
            // Handle: session.created, session.updated, response.audio.delta, response.done, etc.
            log.debug("OpenAI message: {}", message);
        } catch (Exception e) {
            log.warn("Error handling OpenAI message", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        close();
    }
}