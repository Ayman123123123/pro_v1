package com.red.sovereign.voicebridge.bot;

import com.red.sovereign.voicebridge.rtp.RtpPacketizer;

public interface BotSession {

    void start() throws Exception;

    void handleAudioInput(byte[] pcm16Audio);

    void handleDtmf(String digit);

    void sendAudioOutput(byte[] pcm16Audio);

    void handleTruncation();

    void close();

    boolean isActive();

    String getSessionId();

    default String getSessionIdDefault() {
        return "unknown";
    }
}