package com.red.sovereign.voicebridge.session;

import com.red.sovereign.voicebridge.ari.ExternalMediaManager;
import com.red.sovereign.voicebridge.bot.BotSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Optional;

@Component
public class CallSessionManager {

    private static final Logger log = LoggerFactory.getLogger(CallSessionManager.class);

    private final ConcurrentMap<String, CallSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CallSession> sessionsByInboundChannel = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CallSession> sessionsByExternalMediaChannel = new ConcurrentHashMap<>();

    public CallSession createSession(String inboundChannelId, String appName,
                                     String dialedNumber, String callerId) {
        String sessionId = UUID.randomUUID().toString();
        CallSession session = new CallSession(sessionId, appName, inboundChannelId,
            dialedNumber, callerId);

        sessionsById.put(sessionId, session);
        sessionsByInboundChannel.put(inboundChannelId, session);

        log.info("Created call session: id={}, channel={}, exten={}, caller={}",
            sessionId, inboundChannelId, dialedNumber, callerId);

        return session;
    }

    public Optional<CallSession> findSessionById(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public Optional<CallSession> findSessionByChannelId(String channelId) {
        return Optional.ofNullable(sessionsByInboundChannel.get(channelId))
            .or(() -> Optional.ofNullable(sessionsByExternalMediaChannel.get(channelId)));
    }

    public void registerExternalMediaChannel(CallSession session, String channelId) {
        sessionsByExternalMediaChannel.put(channelId, session);
    }

    public void terminateSession(String sessionId) {
        CallSession session = sessionsById.remove(sessionId);
        if (session != null) {
            sessionsByInboundChannel.remove(session.getInboundChannelId());
            sessionsByExternalMediaChannel.remove(session.getExternalMediaChannelId());
            session.terminate();
            log.info("Terminated call session: {}", sessionId);
        }
    }

    public int getActiveSessionCount() {
        return sessionsById.size();
    }
}