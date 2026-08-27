'use strict';

const crypto = require('crypto');
const http = require('http');
const os = require('os');
const mediasoup = require('mediasoup');
const { WebSocketServer } = require('ws');
const { clientErrorPayload } = require('./protocol');

// ─── Configuration ─────────────────────────────────────────────────────────

const PORT = Number(process.env.PORT || 4000);
const RTC_MIN_PORT = Number(process.env.RTC_MIN_PORT || 40000);
const RTC_MAX_PORT = Number(process.env.RTC_MAX_PORT || 40200);
const WORKER_COUNT = Math.max(1, Number(process.env.MEDIASOUP_WORKERS || Math.min(4, os.cpus().length)));
const ANNOUNCED_IP = process.env.MEDIASOUP_ANNOUNCED_IP || '';
const JWT_SECRET = process.env.JWT_SECRET || '';

// Empty room cleanup delay (ms) — prevents immediate cleanup on brief disconnects
const ROOM_CLEANUP_DELAY_MS = Number(process.env.ROOM_CLEANUP_DELAY_MS || 30_000);

// Max producers per peer per kind (rate limiting)
const MAX_PRODUCERS_PER_KIND = Number(process.env.MAX_PRODUCERS_PER_KIND || 2);

if (!JWT_SECRET || JWT_SECRET.length < 32) throw new Error('JWT_SECRET must contain at least 32 characters');
if (!ANNOUNCED_IP) console.warn('MEDIASOUP_ANNOUNCED_IP is unset; LAN/WAN ICE candidates may be unreachable');

// ─── Codecs ────────────────────────────────────────────────────────────────

const mediaCodecs = [
  // Audio: Opus with full FEC + DTX (silence suppression)
  {
    kind: 'audio',
    mimeType: 'audio/opus',
    clockRate: 48000,
    channels: 2,
    parameters: {
      useinbandfec: 1,   // Forward Error Correction
      usedtx: 1,         // Discontinuous Transmission (saves bandwidth in silence)
      stereo: 0,         // Mono for calls (bandwidth efficiency)
      maxplaybackrate: 48000,
      maxaveragebitrate: 64000
    }
  },
  // Video: VP9 — best quality/bitrate ratio, simulcast-friendly
  {
    kind: 'video',
    mimeType: 'video/VP9',
    clockRate: 90000,
    parameters: {
      'profile-id': 0,
      'x-google-start-bitrate': 1000
    }
  },
  // Video: VP8 — fallback for older devices
  {
    kind: 'video',
    mimeType: 'video/VP8',
    clockRate: 90000,
    parameters: {
      'x-google-start-bitrate': 800
    }
  },
  // Video: H264 — hardware acceleration on iOS/Android (HW decode)
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '42e01f',     // Baseline 3.1 — widest compatibility
      'level-asymmetry-allowed': 1,
      'x-google-start-bitrate': 800
    }
  },
  // Video: H264 High — for devices supporting High profile (better quality)
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '640032',    // High Level 5 — 1080p capable
      'level-asymmetry-allowed': 1
    }
  }
];

// ─── Workers & Rooms ───────────────────────────────────────────────────────

const workers = [];
const rooms = new Map(); // roomId → { router, peers, cleanupTimer }
let workerIndex = 0;

// ─── JWT Auth ─────────────────────────────────────────────────────────────

function base64UrlDecode(value) {
  return Buffer.from(value.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

function authenticate(header) {
  const token = String(header || '').replace(/^Bearer\s+/i, '');
  const parts = token.split('.');
  if (parts.length !== 3) throw new Error('Unauthorized');
  const key = crypto.createHash('sha256').update(JWT_SECRET, 'utf8').digest();
  const expected = crypto.createHmac('sha256', key).update(`${parts[0]}.${parts[1]}`).digest();
  const supplied = base64UrlDecode(parts[2]);
  if (expected.length !== supplied.length || !crypto.timingSafeEqual(expected, supplied)) throw new Error('Unauthorized');
  const claims = JSON.parse(base64UrlDecode(parts[1]).toString('utf8'));
  if (!claims.sub || !claims.redId || !claims.exp || claims.exp * 1000 <= Date.now()) throw new Error('Expired or invalid token');
  return claims;
}

// ─── Worker Management ────────────────────────────────────────────────────

async function createWorker() {
  const worker = await mediasoup.createWorker({
    logLevel: 'warn',
    rtcMinPort: RTC_MIN_PORT,
    rtcMaxPort: RTC_MAX_PORT,
    // Enable DTLS debug for diagnosing connection issues in development
    logTags: process.env.NODE_ENV === 'development' ? ['dtls', 'rtp'] : []
  });
  worker.on('died', () => {
    console.error(`mediasoup worker ${worker.pid} died — restarting worker`);
    // Remove dead worker and replace
    const idx = workers.indexOf(worker);
    if (idx !== -1) workers.splice(idx, 1);
    createWorker().catch(e => { console.error('Failed to replace worker:', e); process.exit(1); });
  });
  workers.push(worker);
  return worker;
}

function nextWorker() {
  return workers[workerIndex++ % workers.length];
}

// ─── Room Management ──────────────────────────────────────────────────────

async function roomFor(id) {
  let room = rooms.get(id);
  if (!room) {
    const router = await nextWorker().createRouter({ mediaCodecs });
    room = { router, peers: new Map(), cleanupTimer: null };
    rooms.set(id, room);
    console.log(`Room created: ${id} (total rooms: ${rooms.size})`);
  } else if (room.cleanupTimer) {
    // Cancel pending cleanup — someone rejoined
    clearTimeout(room.cleanupTimer);
    room.cleanupTimer = null;
    console.log(`Room cleanup cancelled for ${id} — peer rejoined`);
  }
  return room;
}

function scheduleRoomCleanup(roomId, room) {
  if (room.peers.size > 0) return; // Still has peers
  if (room.cleanupTimer) return;   // Already scheduled

  room.cleanupTimer = setTimeout(() => {
    const r = rooms.get(roomId);
    if (r && r.peers.size === 0) {
      r.router.close();
      rooms.delete(roomId);
      console.log(`Room ${roomId} cleaned up after ${ROOM_CLEANUP_DELAY_MS}ms idle`);
    }
  }, ROOM_CLEANUP_DELAY_MS);
}

// ─── Transport Factory ────────────────────────────────────────────────────

async function createTransport(router) {
  const listenInfo = { protocol: 'udp', ip: '0.0.0.0' };
  const listenInfoTcp = { protocol: 'tcp', ip: '0.0.0.0' };
  if (ANNOUNCED_IP) {
    listenInfo.announcedAddress = ANNOUNCED_IP;
    listenInfoTcp.announcedAddress = ANNOUNCED_IP;
  }

  const transport = await router.createWebRtcTransport({
    listenInfos: [listenInfo, listenInfoTcp],
    enableUdp: true,
    enableTcp: true,
    preferUdp: true,
    initialAvailableOutgoingBitrate: 1_000_000,    // 1 Mbps start
    minimumAvailableOutgoingBitrate: 100_000,      // Min BWE threshold
    maxSctpMessageSize: 262144,
    // Bandwidth estimation - REMB (receiver-side) + TWCC (transport-wide)
    enableSctp: false  // Not needed for A/V calls
  });

  // Track BWE (Bandwidth Estimation)
  transport.on('bwe', (bwe) => {
    if (process.env.NODE_ENV === 'development') {
       console.debug(`[BWE] Transport ${transport.id}: available outgoing bitrate ${bwe.availableOutgoingBitrate} bps`);
    }
  });

  transport.on('dtlsstatechange', state => {
    if (state === 'failed') console.warn(`DTLS state failed on transport ${transport.id}`);
    if (state === 'closed') transport.close();
  });
  transport.on('iceselectedtuplechange', tuple => {
    console.debug(`ICE tuple selected for transport ${transport.id}: ${JSON.stringify(tuple)}`);
  });
  return transport;
}

function transportOptions(transport) {
  return {
    id: transport.id,
    iceParameters: transport.iceParameters,
    iceCandidates: transport.iceCandidates,
    dtlsParameters: transport.dtlsParameters,
    sctpParameters: transport.sctpParameters
  };
}

//  Pipe Transport (For Broadcast/1-to-N scaling across workers) 

async function createPipeTransport(router) {
  const listenInfo = { protocol: 'udp', ip: '127.0.0.1' };
  const transport = await router.createPipeTransport({
    listenInfo,
    enableSctp: false,
    enableRtx: true, // Retransmission is good for inter-router
    enableSrtp: false // No SRTP needed on loopback
  });
  return transport;
}

//  Messaging Helpers ────────────────────────────────────────────────────

function send(ws, requestId, payload) {
  if (ws.readyState === 1) ws.send(JSON.stringify({ requestId, ...payload }));
}

function sendError(ws, requestId, error) {
  // لا تُسرَّب رسالة الخطأ الداخلية للعميل — رمز ثابت فقط عبر بروتوكول العقد.
  send(ws, requestId, clientErrorPayload(error));
}

function requirePeer(context) {
  if (!context.room || !context.peer) throw new Error('Join a room first');
  return context.peer;
}

function broadcast(room, excludedPeerId, payload) {
  for (const [id, peer] of room.peers) {
    if (id !== excludedPeerId) send(peer.ws, null, payload);
  }
}

function cleanupPeer(context) {
  const { roomId, peerId, room, peer } = context;
  if (!room || !peer) return;

  // Close all media objects
  for (const consumer of peer.consumers.values()) consumer.close();
  for (const producer of peer.producers.values()) producer.close();
  for (const transport of peer.transports.values()) transport.close();

  room.peers.delete(peerId);
  broadcast(room, peerId, { type: 'peerLeft', peerId });

  // Schedule delayed cleanup instead of immediate router close
  if (room.peers.size === 0) {
    scheduleRoomCleanup(roomId, room);
  }

  context.room = null;
  context.peer = null;
  context.roomId = null;
  context.peerId = null;

  console.log(`Peer ${peerId} left room ${roomId}`);
}

// ─── HTTP Server ──────────────────────────────────────────────────────────

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    const healthy = workers.length > 0;
    res.writeHead(healthy ? 200 : 503, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({
      status: healthy ? 'UP' : 'STARTING',
      workers: workers.length,
      rooms: rooms.size,
      peers: [...rooms.values()].reduce((n, r) => n + r.peers.size, 0),
      codecs: mediaCodecs.map(c => c.mimeType)
    }));
  }
  if (req.url === '/metrics') {
    try { authenticate(req.headers.authorization); } catch {
      res.writeHead(401); return res.end();
    }
    const allPeers = [...rooms.values()].flatMap(r => [...r.peers.values()]);
    const producers = allPeers.reduce((n, p) => n + p.producers.size, 0);
    const consumers = allPeers.reduce((n, p) => n + p.consumers.size, 0);
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({
      workers: workers.length,
      rooms: rooms.size,
      peers: allPeers.length,
      producers,
      consumers,
      rtcPortRange: `${RTC_MIN_PORT}-${RTC_MAX_PORT}`
    }));
  }
  res.writeHead(404); res.end();
});

// ─── WebSocket Server ─────────────────────────────────────────────────────

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  if (!req.url.startsWith('/sfu')) {
    socket.destroy();
    return;
  }
  let claims;
  try {
    claims = authenticate(req.headers.authorization);
  } catch {
    socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws, req, claims));
});

wss.on('connection', (ws, _req, claims) => {
  const context = { roomId: null, peerId: null, room: null, peer: null };

  ws.on('message', async raw => {
    let message;
    try {
      message = JSON.parse(raw.toString());
      const { type, requestId } = message;

      // ── join ────────────────────────────────────────────────────
      if (type === 'join') {
        if (context.room) throw new Error('Already joined');
        const roomId = String(message.roomId || '');
        if (!/^[A-Za-z0-9_-]{4,128}$/.test(roomId)) throw new Error('Invalid roomId');
        // أمان: التذكرة مربوطة بغرفة محددة (claim sfuGroupId) — لا يُسمح بالانضمام لغرفة غير الغرفة المصرَّح بها
        if (!claims.sfuGroupId || String(claims.sfuGroupId) !== roomId) throw new Error('Ticket not bound to this room');

        const room = await roomFor(roomId);
        const peerId = claims.redId;

        // Replace existing connection from same peer
        const existing = room.peers.get(peerId);
        if (existing) {
          existing.ws.close(4001, 'replaced');
          for (const t of existing.transports.values()) t.close();
        }

        const peer = {
          ws,
          accountId: claims.sub,
          redId: peerId,
          transports: new Map(),
          producers: new Map(),
          consumers: new Map()
        };
        room.peers.set(peerId, peer);
        Object.assign(context, { roomId, peerId, room, peer });

        const existingProducers = [...room.peers.entries()]
          .filter(([id]) => id !== peerId)
          .flatMap(([id, p]) =>
            [...p.producers.values()].map(producer => ({
              peerId: id,
              producerId: producer.id,
              kind: producer.kind
            }))
          );

        console.log(`Peer ${peerId} joined room ${roomId} (peers: ${room.peers.size})`);
        return send(ws, requestId, {
          status: 'joined',
          peerId,
          rtpCapabilities: room.router.rtpCapabilities,
          existingProducers
        });
      }

      const peer = requirePeer(context);

      // ── createTransport ─────────────────────────────────────────
      if (type === 'createTransport') {
        // Limit transports per peer (prevent resource exhaustion)
        if (peer.transports.size >= 4) throw new Error('Too many transports');
        const transport = await createTransport(context.room.router);
        peer.transports.set(transport.id, transport);
        transport.on('close', () => peer.transports.delete(transport.id));
        return send(ws, requestId, {
          status: 'transportCreated',
          direction: message.direction,
          transportOptions: transportOptions(transport)
        });
      }

      // ── connectTransport ────────────────────────────────────────
      if (type === 'connectTransport') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');
        await transport.connect({ dtlsParameters: message.dtlsParameters });
        return send(ws, requestId, { status: 'transportConnected', transportId: transport.id });
      }

      // ── produce ─────────────────────────────────────────────────
      if (type === 'produce') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');

        // Rate limiting: max N producers per kind per peer
        const kindCount = [...peer.producers.values()].filter(p => p.kind === message.kind).length;
        if (kindCount >= MAX_PRODUCERS_PER_KIND) throw new Error(`Max ${MAX_PRODUCERS_PER_KIND} producers per kind`);

        const producer = await transport.produce({
          kind: message.kind,
          rtpParameters: message.rtpParameters,
          appData: {
            peerId: context.peerId,
            redId: claims.redId,
            kind: message.kind,
            simulcast: message.simulcast === true  // Client can request simulcast
          }
        });
        peer.producers.set(producer.id, producer);
        producer.on('transportclose', () => peer.producers.delete(producer.id));

        // Notify all other peers about new producer
        broadcast(context.room, context.peerId, {
          type: 'newProducer',
          peerId: context.peerId,
          producerId: producer.id,
          kind: producer.kind
        });

        return send(ws, requestId, { status: 'producing', producerId: producer.id });
      }

      // ── consume ─────────────────────────────────────────────────
      if (type === 'consume') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');

        if (!context.room.router.canConsume({
          producerId: message.producerId,
          rtpCapabilities: message.rtpCapabilities
        })) throw new Error('Cannot consume producer');

        const consumer = await transport.consume({
          producerId: message.producerId,
          rtpCapabilities: message.rtpCapabilities,
          paused: true  // Always start paused; client calls resumeConsumer when ready
        });
        peer.consumers.set(consumer.id, consumer);
        consumer.on('transportclose', () => peer.consumers.delete(consumer.id));
        consumer.on('producerclose', () => {
          peer.consumers.delete(consumer.id);
          send(ws, null, {
            type: 'producerClosed',
            consumerId: consumer.id,
            producerId: message.producerId
          });
        });

        // Monitor Consumer Score for Network Degradation & Auto-fallback
        consumer.on('score', (score) => {
          if (score.score < 5 && score.producerScore >= 7) {
            // Producer is healthy but consumer network is bad
            broadcast(context.room, null, {
              type: 'networkDegraded',
              peerId: context.peerId,
              consumerId: consumer.id,
              score: score.score
            });
          }
        });

        // Monitor Simulcast layer switching dynamically
        consumer.on('layerschange', (layers) => {
          console.debug(`[Simulcast] Consumer ${consumer.id} spatial layer changed to: ${layers ? layers.spatialLayer : 'none'}`);
        });

        return send(ws, requestId, {
          status: 'consuming',
          consumerId: consumer.id,
          producerId: message.producerId,
          kind: consumer.kind,
          rtpParameters: consumer.rtpParameters
        });
      }

      // ── resumeConsumer ──────────────────────────────────────────
      if (type === 'resumeConsumer') {
        const consumer = peer.consumers.get(message.consumerId);
        if (!consumer) throw new Error('Consumer not found');
        await consumer.resume();
        return send(ws, requestId, { status: 'consumerResumed', consumerId: consumer.id });
      }

      // ── pauseProducer ───────────────────────────────────────────
      if (type === 'pauseProducer') {
        const producer = peer.producers.get(message.producerId);
        if (!producer) throw new Error('Producer not found');
        await producer.pause();
        broadcast(context.room, context.peerId, {
          type: 'producerPaused',
          producerId: producer.id,
          peerId: context.peerId
        });
        return send(ws, requestId, { status: 'producerPaused', producerId: producer.id });
      }

      // ── resumeProducer ──────────────────────────────────────────
      if (type === 'resumeProducer') {
        const producer = peer.producers.get(message.producerId);
        if (!producer) throw new Error('Producer not found');
        await producer.resume();
        broadcast(context.room, context.peerId, {
          type: 'producerResumed',
          producerId: producer.id,
          peerId: context.peerId
        });
        return send(ws, requestId, { status: 'producerResumed', producerId: producer.id });
      }

      // ── leave ───────────────────────────────────────────────────
      if (type === 'leave') {
        cleanupPeer(context);
        return send(ws, requestId, { status: 'left' });
      }

      throw new Error('Unknown message type');
    } catch (error) {
      console.error(`SFU error [${message?.type}]:`, error.message);
      sendError(ws, message?.requestId, error);
    }
  });

  ws.on('close', () => cleanupPeer(context));
  ws.on('error', error => console.error('SFU WebSocket error:', error.message));
});

// ─── Startup ──────────────────────────────────────────────────────────────

(async () => {
  for (let i = 0; i < WORKER_COUNT; i++) await createWorker();
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`RED SFU v2 listening on 0.0.0.0:${PORT}`);
    console.log(`  Workers: ${workers.length}`);
    console.log(`  RTC ports: ${RTC_MIN_PORT}-${RTC_MAX_PORT}`);
    console.log(`  Room cleanup delay: ${ROOM_CLEANUP_DELAY_MS}ms`);
    console.log(`  Codecs: ${mediaCodecs.map(c => c.mimeType).join(', ')}`);
  });
})().catch(error => {
  console.error('SFU startup error:', error);
  process.exit(1);
});
