package com.red.sovereign.voicebridge.rtp;

import com.red.sovereign.voicebridge.config.RtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public class RtpPacketizer {

    private static final Logger log = LoggerFactory.getLogger(RtpPacketizer.class);

    private static final int RTP_VERSION = 2;
    private static final int RTP_HEADER_SIZE = 12;
    private static final int RTP_PAYLOAD_TYPE_PCMU = 0;
    private static final int RTP_PAYLOAD_TYPE_PCMA = 8;
    private static final int RTP_PAYLOAD_TYPE_G722 = 9;
    private static final int RTP_PAYLOAD_TYPE_OPUS = 111;

    private final RtpProperties rtpProperties;
    private final int frameSizeSamples;
    private final int frameSizeBytes;

    public RtpPacketizer(RtpProperties rtpProperties) {
        this.rtpProperties = rtpProperties;
        this.frameSizeSamples = (rtpProperties.getSampleRate() * rtpProperties.getFrameSizeMs()) / 1000;
        this.frameSizeBytes = frameSizeSamples * 2; // 16-bit samples

        log.info("RTP Packetizer initialized: sampleRate={}, frameSizeMs={}, frameSamples={}, frameBytes={}",
            rtpProperties.getSampleRate(), rtpProperties.getFrameSizeMs(), frameSizeSamples, frameSizeBytes);
    }

    /**
     * Create an RTP packet from raw PCM16 audio samples.
     * 
     * @param payload PCM16 audio data (16-bit signed, little-endian)
     * @param sequence RTP sequence number
     * @param timestamp RTP timestamp (increments by frameSizeSamples per packet)
     * @param ssrc Synchronization source identifier
     * @param payloadType RTP payload type (0=PCMU, 8=PCMA, 9=G722, 111=OPUS)
     * @return ByteBuffer containing the complete RTP packet
     */
    public ByteBuffer packetize(byte[] payload, int sequence, long timestamp, int ssrc, int payloadType) {
        if (payload.length != frameSizeBytes) {
            throw new IllegalArgumentException("Payload size " + payload.length +
                " != expected frame size " + frameSizeBytes);
        }

        ByteBuffer buffer = ByteBuffer.allocate(RTP_HEADER_SIZE + payload.length);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // RTP Header (12 bytes)
        // Byte 0: V=2, P=0, X=0, CC=0
        buffer.put((byte) (RTP_VERSION << 6));

        // Byte 1: M=0, PT=payloadType
        buffer.put((byte) payloadType);

        // Bytes 2-3: Sequence number (16-bit, big-endian)
        buffer.putShort((short) sequence);

        // Bytes 4-7: Timestamp (32-bit, big-endian)
        buffer.putInt((int) timestamp);

        // Bytes 8-11: SSRC (32-bit, big-endian)
        buffer.putInt(ssrc);

        // Payload
        buffer.put(payload);

        buffer.flip();
        return buffer;
    }

    /**
     * Parse an incoming RTP packet.
     */
    public ParsedRtpPacket parse(ByteBuffer buffer) {
        if (buffer.remaining() < RTP_HEADER_SIZE) {
            throw new IllegalArgumentException("Buffer too small for RTP header");
        }

        buffer.mark();
        byte firstByte = buffer.get();
        int version = (firstByte >> 6) & 0x03;
        if (version != RTP_VERSION) {
            throw new IllegalArgumentException("Invalid RTP version: " + version);
        }

        byte secondByte = buffer.get();
        int payloadType = secondByte & 0x7F;
        boolean marker = (secondByte & 0x80) != 0;

        int sequence = buffer.getShort() & 0xFFFF;
        int timestamp = buffer.getInt();
        int ssrc = buffer.getInt();

        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);

        return new ParsedRtpPacket(version, payloadType, marker, sequence, timestamp, ssrc, payload);
    }

    public int getFrameSizeSamples() { return frameSizeSamples; }
    public int getFrameSizeBytes() { return frameSizeBytes; }
    public int getSampleRate() { return rtpProperties.getSampleRate(); }
    public int getFrameSizeMs() { return rtpProperties.getFrameSizeMs(); }

    public int getPayloadTypeForCodec(String codec) {
        return switch (codec.toUpperCase()) {
            case "PCMU", "ULAW" -> RTP_PAYLOAD_TYPE_PCMU;
            case "PCMA", "ALAW" -> RTP_PAYLOAD_TYPE_PCMA;
            case "G722" -> RTP_PAYLOAD_TYPE_G722;
            case "OPUS" -> RTP_PAYLOAD_TYPE_OPUS;
            default -> RTP_PAYLOAD_TYPE_PCMU;
        };
    }

    public record ParsedRtpPacket(
        int version,
        int payloadType,
        boolean marker,
        int sequence,
        int timestamp,
        int ssrc,
        byte[] payload
    ) {}
}