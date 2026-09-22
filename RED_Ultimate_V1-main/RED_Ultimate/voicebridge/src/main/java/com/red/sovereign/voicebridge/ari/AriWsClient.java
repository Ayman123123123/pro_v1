package com.red.sovereign.voicebridge.ari;

import org.asteriskjava.live.AriChannel;
import org.asteriskjava.live.AriException;
import org.asteriskjava.manager.event.AriEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class AriWsClient implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AriWsClient.class);

    private final AriProperties ariProperties;
    private final AriEventHandler eventHandler;

    private WebSocketSession session;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean running = false;

    public AriWsClient(AriProperties ariProperties, AriEventHandler eventHandler) {
        this.ariProperties = ariProperties;
        this.eventHandler = eventHandler;
    }

    @PostConstruct
    public void connect() {
        running = true;
        doConnect();
    }

    private void doConnect() {
        try {
            String wsUrl = ariProperties.getBaseUrl().replace("http", "ws") + ariProperties.getWebSocketPath();
            log.info("Connecting to ARI WebSocket: {}", wsUrl);

            StandardWebSocketClient client = new StandardWebSocketClient();
            client.doHandshake(this, new URI(wsUrl)).whenComplete((wsSession, ex) -> {
                if (ex != null) {
                    log.error("ARI WebSocket connection failed: {}", ex.getMessage());
                    scheduleReconnect();
                } else {
                    this.session = wsSession;
                    log.info("ARI WebSocket connected successfully");
                }
            });
        } catch (Exception e) {
            log.error("Failed to initiate ARI WebSocket connection", e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running || !ariProperties.getAutoReconnect()) return;
        log.info("Scheduling ARI reconnect in {} ms", ariProperties.getReconnectIntervalMs());
        reconnectScheduler.schedule(this::doConnect, ariProperties.getReconnectIntervalMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void disconnect() {
        running = false;
        reconnectScheduler.shutdownNow();
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception e) {
                log.warn("Error closing ARI WebSocket", e);
            }
        }
    }

    public void sendEvent(String eventJson) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(eventJson));
            } catch (Exception e) {
                log.error("Failed to send ARI event", e);
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("ARI WebSocket session established: {}", session.getId());
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            String payload = message.getPayload().toString();
            log.trace("Received ARI event: {}", payload);
            eventHandler.handleEvent(payload);
        } catch (Exception e) {
            log.error("Error handling ARI event", e);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("ARI WebSocket transport error", exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.warn("ARI WebSocket closed: {}", status);
        if (running) {
            scheduleReconnect();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}