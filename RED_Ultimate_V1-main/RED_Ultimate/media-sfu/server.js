'use strict';

const crypto = require('crypto');
const http = require('http');
const os = require('os');
const mediasoup = require('mediasoup');
const { WebSocketServer } = require('ws');

const PORT = Number(process.env.PORT || 4000);
const RTC_MIN_PORT = Number(process.env.RTC_MIN_PORT || 40000);
const RTC_MAX_PORT = Number(process.env.RTC_MAX_PORT || 40100);
const WORKER_COUNT = Math.max(1, Number(process.env.MEDIASOUP_WORKERS || Math.min(4, os.cpus().length)));
const ANNOUNCED_IP = process.env.MEDIASOUP_ANNOUNCED_IP || '';
const JWT_SECRET = process.env.JWT_SECRET || '';

if (!JWT_SECRET || JWT_SECRET.length < 32) throw new Error('JWT_SECRET must contain at least 32 characters');
if (!ANNOUNCED_IP) console.warn('MEDIASOUP_ANNOUNCED_IP is unset; LAN/WAN ICE candidates may be unreachable');

const mediaCodecs = [
  { kind: 'audio', mimeType: 'audio/opus', clockRate: 48000, channels: 2, parameters: { useinbandfec: 1, usedtx: 1, stereo: 0 } },
  { kind: 'video', mimeType: 'video/VP9', clockRate: 90000, parameters: { 'profile-id': 0, 'x-google-start-bitrate': 1000 } },
  { kind: 'video', mimeType: 'video/VP8', clockRate: 90000, parameters: { 'x-google-start-bitrate': 800 } },
  { kind: 'video', mimeType: 'video/H264', clockRate: 90000, parameters: { 'packetization-mode': 1, 'profile-level-id': '42e01f', 'level-asymmetry-allowed': 1 } }
];

const workers = [];
const rooms = new Map(); // roomId -> { router, peers }
let workerIndex = 0;

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

async function createWorker() {
  const worker = await mediasoup.createWorker({ logLevel: 'warn', rtcMinPort: RTC_MIN_PORT, rtcMaxPort: RTC_MAX_PORT });
  worker.on('died', () => { console.error(`mediasoup worker ${worker.pid} died`); process.exit(1); });
  workers.push(worker);
}

function nextWorker() {
  const worker = workers[workerIndex++ % workers.length];
  return worker;
}

async function roomFor(id) {
  let room = rooms.get(id);
  if (!room) {
    room = { router: await nextWorker().createRouter({ mediaCodecs }), peers: new Map() };
    rooms.set(id, room);
  }
  return room;
}

async function createTransport(router) {
  const ip = { ip: '0.0.0.0' };
  if (ANNOUNCED_IP) ip.announcedIp = ANNOUNCED_IP;
  const transport = await router.createWebRtcTransport({ listenIps: [ip], enableUdp: true, enableTcp: true, preferUdp: true, initialAvailableOutgoingBitrate: 1_000_000 });
  transport.on('dtlsstatechange', state => { if (state === 'closed') transport.close(); });
  return transport;
}

function transportOptions(transport) {
  return { id: transport.id, iceParameters: transport.iceParameters, iceCandidates: transport.iceCandidates, dtlsParameters: transport.dtlsParameters, sctpParameters: transport.sctpParameters };
}

function send(ws, requestId, payload) {
  if (ws.readyState === 1) ws.send(JSON.stringify({ requestId, ...payload }));
}

function requirePeer(context) {
  if (!context.room || !context.peer) throw new Error('Join a room first');
  return context.peer;
}

function broadcast(room, excludedPeerId, payload) {
  for (const [id, peer] of room.peers) if (id !== excludedPeerId) send(peer.ws, null, payload);
}

function cleanupPeer(context) {
  const { roomId, peerId, room, peer } = context;
  if (!room || !peer) return;
  for (const consumer of peer.consumers.values()) consumer.close();
  for (const producer of peer.producers.values()) producer.close();
  for (const transport of peer.transports.values()) transport.close();
  room.peers.delete(peerId);
  broadcast(room, peerId, { type: 'peerLeft', peerId });
  if (room.peers.size === 0) { room.router.close(); rooms.delete(roomId); }
  context.room = null; context.peer = null; context.roomId = null; context.peerId = null;
}

const server = http.createServer((req, res) => {
  if (req.url === '/health') {
    res.writeHead(workers.length ? 200 : 503, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ status: workers.length ? 'UP' : 'STARTING', workers: workers.length, rooms: rooms.size, peers: [...rooms.values()].reduce((n, r) => n + r.peers.size, 0) }));
  }
  if (req.url === '/metrics') {
    try { authenticate(req.headers.authorization); } catch { res.writeHead(401); return res.end(); }
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ workers: workers.length, rooms: rooms.size, peers: [...rooms.values()].reduce((n, r) => n + r.peers.size, 0), producers: [...rooms.values()].reduce((n, r) => n + [...r.peers.values()].reduce((x, p) => x + p.producers.size, 0), 0) }));
  }
  res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ noServer: true });
server.on('upgrade', (req, socket, head) => {
  if (!req.url.startsWith('/sfu')) { socket.destroy(); return; }
  let claims;
  try { claims = authenticate(req.headers.authorization); } catch { socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n'); socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, ws => wss.emit('connection', ws, req, claims));
});

wss.on('connection', (ws, _req, claims) => {
  const context = { roomId: null, peerId: null, room: null, peer: null };

  ws.on('message', async raw => {
    let message;
    try {
      message = JSON.parse(raw.toString());
      const { type, requestId } = message;
      if (type === 'join') {
        if (context.room) throw new Error('Already joined');
        const roomId = String(message.roomId || '');
        if (!/^[A-Za-z0-9_-]{8,128}$/.test(roomId)) throw new Error('Invalid roomId');
        const room = await roomFor(roomId);
        const peerId = claims.redId;
        const existing = room.peers.get(peerId);
        if (existing) { existing.ws.close(4001, 'replaced'); for (const t of existing.transports.values()) t.close(); }
        const peer = { ws, accountId: claims.sub, transports: new Map(), producers: new Map(), consumers: new Map() };
        room.peers.set(peerId, peer);
        Object.assign(context, { roomId, peerId, room, peer });
        return send(ws, requestId, { status: 'joined', peerId, rtpCapabilities: room.router.rtpCapabilities,
          existingProducers: [...room.peers.entries()].filter(([id]) => id !== peerId).flatMap(([id, p]) => [...p.producers.values()].map(producer => ({ peerId: id, producerId: producer.id, kind: producer.kind }))) });
      }

      const peer = requirePeer(context);
      if (type === 'createTransport') {
        const transport = await createTransport(context.room.router);
        peer.transports.set(transport.id, transport);
        transport.on('close', () => peer.transports.delete(transport.id));
        return send(ws, requestId, { status: 'transportCreated', direction: message.direction, transportOptions: transportOptions(transport) });
      }
      if (type === 'connectTransport') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');
        await transport.connect({ dtlsParameters: message.dtlsParameters });
        return send(ws, requestId, { status: 'transportConnected', transportId: transport.id });
      }
      if (type === 'produce') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');
        const producer = await transport.produce({ kind: message.kind, rtpParameters: message.rtpParameters, appData: { peerId: context.peerId } });
        peer.producers.set(producer.id, producer);
        producer.on('transportclose', () => peer.producers.delete(producer.id));
        broadcast(context.room, context.peerId, { type: 'newProducer', peerId: context.peerId, producerId: producer.id, kind: producer.kind });
        return send(ws, requestId, { status: 'producing', producerId: producer.id });
      }
      if (type === 'consume') {
        const transport = peer.transports.get(message.transportId);
        if (!transport) throw new Error('Transport not found');
        if (!context.room.router.canConsume({ producerId: message.producerId, rtpCapabilities: message.rtpCapabilities })) throw new Error('Cannot consume producer');
        const consumer = await transport.consume({ producerId: message.producerId, rtpCapabilities: message.rtpCapabilities, paused: true });
        peer.consumers.set(consumer.id, consumer);
        consumer.on('transportclose', () => peer.consumers.delete(consumer.id));
        consumer.on('producerclose', () => { peer.consumers.delete(consumer.id); send(ws, null, { type: 'producerClosed', consumerId: consumer.id, producerId: message.producerId }); });
        return send(ws, requestId, { status: 'consuming', consumerId: consumer.id, producerId: message.producerId, kind: consumer.kind, rtpParameters: consumer.rtpParameters });
      }
      if (type === 'resumeConsumer') {
        const consumer = peer.consumers.get(message.consumerId);
        if (!consumer) throw new Error('Consumer not found');
        await consumer.resume();
        return send(ws, requestId, { status: 'consumerResumed', consumerId: consumer.id });
      }
      if (type === 'pauseProducer') {
        const producer = peer.producers.get(message.producerId);
        if (!producer) throw new Error('Producer not found');
        await producer.pause();
        return send(ws, requestId, { status: 'producerPaused', producerId: producer.id });
      }
      if (type === 'resumeProducer') {
        const producer = peer.producers.get(message.producerId);
        if (!producer) throw new Error('Producer not found');
        await producer.resume();
        return send(ws, requestId, { status: 'producerResumed', producerId: producer.id });
      }
      if (type === 'leave') { cleanupPeer(context); return send(ws, requestId, { status: 'left' }); }
      throw new Error('Unknown message type');
    } catch (error) {
      send(ws, message?.requestId, { status: 'error', error: error.message });
    }
  });

  ws.on('close', () => cleanupPeer(context));
  ws.on('error', error => console.error('SFU WebSocket error', error.message));
});

(async () => {
  for (let i = 0; i < WORKER_COUNT; i++) await createWorker();
  server.listen(PORT, '0.0.0.0', () => console.log(`RED SFU listening on 0.0.0.0:${PORT} with ${workers.length} workers`));
})().catch(error => { console.error(error); process.exit(1); });
