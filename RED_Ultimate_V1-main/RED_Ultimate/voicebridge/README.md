# RED Sovereign VoiceBridge

**Full-duplex voice bridge between Asterisk/FreePBX and AI voice engines via ARI + ExternalMedia**

## Architecture

```
┌─────────────┐     WebSocket      ┌─────────────┐     RTP      ┌─────────────┐
│  Browser    │ ◄─────────────────► │   Janus     │ ◄──────────► │  Asterisk   │
│  (WebRTC)   │   SIP over WS      │  (Edge SBC) │   SIP/RTP    │  (PSTN GW)  │
└─────────────┘                    └──────┬──────┘              └──────┬──────┘
                                          │                           │
                                          │ ARI/ExternalMedia         │
                                          ▼                           ▼
                                   ┌─────────────────────┐    ┌─────────────────┐
                                   │   VoiceBridge       │    │  Dindar UC2000  │
                                   │  (ARI + RTP Engine) │    │   (GSM Gateway) │
                                   └──────────┬──────────┘    └─────────────────┘
                                              │
                                              ▼
                                   ┌─────────────────────┐
                                   │    AI Voice Bot     │
                                   │ (OpenAI Realtime /  │
                                   │  Custom Pipeline)   │
                                   └─────────────────────┘
```

## Core Components

### 1. ARI Control Plane (`ari/`)
- **AriWsClient** - WebSocket client for ARI events (StasisStart, ChannelStateChange, DTMF, Hangup)
- **AriEventHandler** - Routes ARI events to appropriate handlers
- **ExternalMediaManager** - Creates/manages ExternalMedia channels via ARI REST
- **AriBridgeImpl** - Implements dual-bridge duplex media graph:
  - **Inbound Bridge**: `inbound_channel + external_media_out` (bot → caller)
  - **Tap Bridge**: `snoop_inbound + external_media_in` (caller → bot)

### 2. RTP Media Engine (`rtp/`)
- **RtpPortAllocator** - Allocates RTP/RTCP port pairs from configured range
- **RtpPacketizer** - Encodes/decodes RTP packets (PCMU, PCMA, G.722, Opus)
- **RtpSymmetricEndpoint** - Core duplex endpoint:
  - Learns remote peer from inbound RTP (NAT traversal)
  - Sends outbound RTP to learned peer
  - Implements jitter buffer and packet pacing
  - Handles barge-in detection

### 3. Session Management (`session/`)
- **CallSession** - Per-call state (channels, bridges, RTP endpoints, bot session)
- **CallSessionManager** - Thread-safe session registry with cleanup

### 4. AI Bot Integration (`bot/`)
- **BotSession** - Interface for AI backends
- **OpenAiRealtimeBotSession** - OpenAI Realtime API integration with:
  - Server-side VAD for turn detection
  - Barge-in/truncation support
  - Streaming audio (PCM16 ↔ base64)
  - Tool calling support

### 5. Configuration (`config/`)
- **VoiceBridgeProperties** - Core settings (max calls, timeouts, barge-in, recording)
- **AriProperties** - ARI connection (host, port, credentials, reconnect)
- **RtpProperties** - RTP settings (ports, codecs, frame size, jitter buffer)
- **BotProperties** - AI provider config (OpenAI Realtime, external WS)

## Media Flow (Duplex)

```
Caller (GSM)                    VoiceBridge                          AI Bot
    │                              │                                    │
    ├── SIP INVITE ──────────────►│                                    │
    │                              ├── Stasis(voicebridge) ──────────►│
    │                              │                                    │
    │◄── 183 Session Progress ────┤                                    │
    │                              │                                    │
    │◄── 200 OK ──────────────────┤                                    │
    │                              │                                    │
    │     RTP (PCMU) ─────────────►│                                    │
    │                              ├── ExternalMedia IN ─────────────►│
    │                              │                                    ├── STT → LLM → TTS
    │                              │◄── ExternalMedia OUT ────────────┤
    │     RTP (PCMU) ◄────────────┤                                    │
    │                              │                                    │
    │                              │  (Barge-in: caller speaks during TTS) │
    │                              ├── Truncate bot audio ◄───────────┤
    │                              │                                    │
    └── BYE ──────────────────────►│                                    │
                                   │                                    │
```

## Key Features

| Feature | Implementation |
|---------|----------------|
| **Full-duplex** | Dual ExternalMedia channels + symmetric RTP endpoints |
| **NAT Traversal** | Symmetric RTP learns peer from inbound packets |
| **Barge-in** | Energy detection + OpenAI `response.cancel` + buffer clear |
| **Codec Transcoding** | PCMU/PCMA ↔ PCM16 ↔ Opus (AI native) |
| **Jitter Buffer** | Adaptive buffer with configurable size |
| **Multi-tenant** | DB-driven config per Stasis app name |
| **Observability** | Prometheus metrics, structured JSON logs, distributed tracing |
| **Resilience** | ARI auto-reconnect, graceful degradation, clean teardown |

## Configuration

All runtime configuration is database-driven via `stasis_app_config` table. Environment variables provide bootstrap defaults:

```yaml
voicebridge:
  stasis-app-name: voicebridge
  max-concurrent-calls: 100
  call-timeout-seconds: 180
  barge-in-enabled: true
  recording-enabled: false

  ari:
    host: asterisk
    port: 8088
    username: voicebridge
    password: ${ARI_PASSWORD}

  rtp:
    bind-port: 12100
    port-range-start: 12100
    port-range-end: 13100
    frame-size-ms: 20
    default-codec: PCMU

  bot:
    provider: openai-realtime
    openai-api-key: ${OPENAI_API_KEY}
    openai-model: gpt-4o-realtime-preview-2024-12-17
    vad-enabled: true
    truncation-enabled: true
```

## Deployment

### Docker
```bash
# Production
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# Staging
docker compose -f docker-compose.yml -f docker-compose.staging.yml up -d

# Development (with hot reload + debug port 5005)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

### Health Checks
- **Liveness**: `GET /voicebridge/actuator/health/liveness`
- **Readiness**: `GET /voicebridge/actuator/health/readiness`
- **Metrics**: `GET /voicebridge/actuator/prometheus`

### Monitoring
- **Grafana Dashboard**: `VoiceBridge Overview` (UID: `red-sovereign-voicebridge`)
- **Key Metrics**:
  - `voicebridge_active_calls` - Current active calls
  - `voicebridge_calls_total` - Total calls by outcome
  - `voicebridge_barge_in_total` - Barge-in events
  - `voicebridge_rtp_packet_loss_total` - RTP packet loss
  - `voicebridge_bot_latency_seconds` - AI response latency

## Asterisk Integration

### Required Dialplan Contexts
```asterisk
[voicebridge-inbound]
exten => _X.,1,Answer()
 same => n,Stasis(voicebridge)
 same => n,Hangup()

[voicebridge-outbound]
exten => _X.,1,Stasis(voicebridge)
 same => n,Hangup()
```

### ARI Configuration (`ari.conf`)
```ini
[voicebridge]
enabled = yes
username = voicebridge
password = ${ARI_PASSWORD}
read = all
write = all
```

### PJSIP Endpoint for VoiceBridge
```asterisk
[voicebridge]
type = endpoint
context = voicebridge-inbound
disallow = all
allow = ulaw,alaw,opus
dtls_auto_generate_cert = yes
webrtc = yes
use_avpf = yes
media_encryption = dtls
ice_support = yes
```

## Development

### Prerequisites
- JDK 21+
- Maven 3.9+
- PostgreSQL 15+
- Asterisk 20+ with ARI enabled
- OpenAI API key (for OpenAI Realtime)

### Build
```bash
cd voicebridge
mvn clean package -DskipTests
```

### Run Locally
```bash
# With debug port 5005
mvn spring-boot:run -Dspring-boot.run.jvmArguments='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'

# With dev profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

### Tests
```bash
mvn test
mvn verify  # Includes integration tests
```

## Extending

### Custom AI Provider
Implement `BotSession` interface:
```java
@Component
public class CustomBotSession implements BotSession {
    @Override
    public void start() { /* connect to your AI */ }
    
    @Override
    public void handleAudioInput(byte[] pcm16) { /* stream to AI */ }
    
    @Override
    public void handleTruncation() { /* handle barge-in */ }
    
    @Override
    public void close() { /* cleanup */ }
}
```

Register in `AiClientFactory` with a new provider name.

### Custom Metrics
```java
@Component
public class CustomMetrics {
    private final Counter customCounter;
    
    public CustomMetrics(MeterRegistry registry) {
        this.customCounter = Counter.builder("voicebridge.custom.metric")
            .tag("type", "example")
            .register(registry);
    }
}
```

## Troubleshooting

| Symptom | Check |
|---------|-------|
| No audio | `external_media_address` in pjsip.conf, RTP port ranges, firewall |
| One-way audio | `media_address` on endpoints, symmetric RTP learning, NAT |
| ARI connection fails | `ari.conf`, firewall 8088, WebSocket path |
| Bot not responding | OpenAI API key, WebSocket connection, audio format |
| Barge-in not working | VAD threshold, truncation enabled, energy detection |

## License
Proprietary - RED Sovereign Platform