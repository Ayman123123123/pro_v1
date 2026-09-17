import 'node:process';
import 'node:console';

import Fastify, { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { createServer } from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import pino from 'pino';
import { loadConfig, Config } from './config.js';
import { WorkerManager } from './worker.js';
import { RouterManager } from './router.js';
import { MediaManager } from './media.js';
import { TransportManager } from './transport.js';
import { RecordingManager } from './recording.js';
import { LiveStreamManager } from './livestream.js';
import { AuthManager } from './auth.js';
import { clientErrorPayload, clientErrorCode } from './protocol.js';
import { SFUStats, HealthStatus, AuthClaims, WebSocketMessage, ProducerAppData, RoomConfig } from './types.js';

const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  transport: process.env.NODE_ENV !== 'production' ? { target: 'pino-pretty', options: { colorize: true } } : undefined,
});

interface PeerContext {
  roomId: string | null;
  peerId: string | null;
  room: any;
  peer: any;
  ws: WebSocket;
  claims: AuthClaims;
}

interface ServerDependencies {
  config: Config;
  workerManager: WorkerManager;
  routerManager: RouterManager;
  mediaManager: MediaManager;
  transportManager: TransportManager;
  recordingManager: RecordingManager;
  liveStreamManager: LiveStreamManager;
  authManager: AuthManager;
  fastify: FastifyInstance;
  wss: WebSocketServer;
}

async function buildServer(): Promise<ServerDependencies> {
  const config = loadConfig();

  logger.info({ version: '2.0.0', config: { ...config, jwtSecret: '[REDACTED]', sfuTicketSecret: '[REDACTED]' } }, 'Starting RED Media SFU');

  // Initialize Worker Manager
  const workerManager = new WorkerManager(config);
  await workerManager.initialize();

  // Initialize Managers
  const routerManager = new RouterManager(workerManager, config.roomCleanupDelayMs);
  const mediaManager = new MediaManager(config.maxProducersPerKind);
  const transportManager = new TransportManager(config);
  const recordingManager = new RecordingManager(mediaManager, config.recording?.ffmpegPath || 'ffmpeg', config.recording?.outputDir || './recordings');
  const liveStreamManager = new LiveStreamManager(config.liveStream?.defaultRtmpUrl || '', config.recording?.ffmpegPath || 'ffmpeg');
  const authManager = new AuthManager(config);

  // Create Fastify instance
  const fastify = Fastify({
    logger: false,
    disableRequestLogging: true,
  });

  // Register plugins
  await fastify.register(import('@fastify/cors'), {
    origin: true,
    credentials: true,
  });

  await fastify.register(import('@fastify/rate-limit'), {
    max: 100,
    timeWindow: '1 minute',
    keyGenerator: (req: FastifyRequest) => req.ip,
  });

  await fastify.register(import('@fastify/websocket'), {
    options: {
      maxPayload: 16 * 1024 * 1024,
    },
  });

  // Health check endpoint
  fastify.get('/health', async (request: FastifyRequest, reply: FastifyReply) => {
    const healthy = workerManager.getWorkerCount() > 0;
    const status: HealthStatus = {
      status: healthy ? 'UP' : 'STARTING',
      workers: workerManager.getWorkerCount(),
      rooms: routerManager.getRoomCount(),
      peers: routerManager.getTotalPeerCount(),
      uptime: Math.floor(process.uptime()),
      version: '2.0.0',
    };
    reply.code(healthy ? 200 : 503).send(status);
  });

  // Metrics endpoint (Prometheus)
  fastify.get('/metrics', async (request: FastifyRequest, reply: FastifyReply) => {
    // Auth check for metrics
    try {
      authManager.authenticate(request.headers.authorization as string);
    } catch {
      return reply.code(401).send({ error: 'Unauthorized' });
    }

    const allRooms = routerManager.getAllRooms();
    let totalProducers = 0;
    let totalConsumers = 0;
    let totalBytesSent = 0;
    let totalBytesReceived = 0;
    let totalPacketsLost = 0;
    let totalRtt = 0;
    let totalJitter = 0;
    let statCount = 0;

    for (const room of allRooms) {
      for (const peer of room.peers.values()) {
        totalProducers += peer.producers.size;
        totalConsumers += peer.consumers.size;

        for (const producer of peer.producers.values()) {
          try {
            const stats = await producer.getStats();
            for (const stat of stats.values()) {
              totalBytesSent += stat.bytesSent || 0;
              totalPacketsLost += stat.packetsLost || 0;
              totalRtt += stat.roundTripTime || 0;
              statCount++;
            }
          } catch {
            // Ignore stats errors
          }
        }

        for (const consumer of peer.consumers.values()) {
          try {
            const stats = await consumer.getStats();
            for (const stat of stats.values()) {
              totalBytesReceived += stat.bytesReceived || 0;
              totalPacketsLost += stat.packetsLost || 0;
              totalJitter += stat.jitter || 0;
              statCount++;
            }
          } catch {
            // Ignore stats errors
          }
        }
      }
    }

    const workerStats = workerManager.getWorkerStats();
    const totalCpu = workerStats.reduce((sum, w) => sum + w.cpuUsage, 0);
    const totalMemory = workerStats.reduce((sum, w) => sum + w.memoryUsage, 0);

    const metrics = [
      `# HELP sfu_workers Total number of mediasoup workers`,
      `# TYPE sfu_workers gauge`,
      `sfu_workers ${workerManager.getWorkerCount()}`,
      `# HELP sfu_rooms Total number of active rooms`,
      `# TYPE sfu_rooms gauge`,
      `sfu_rooms ${routerManager.getRoomCount()}`,
      `# HELP sfu_peers Total number of connected peers`,
      `# TYPE sfu_peers gauge`,
      `sfu_peers ${routerManager.getTotalPeerCount()}`,
      `# HELP sfu_producers Total number of active producers`,
      `# TYPE sfu_producers gauge`,
      `sfu_producers ${totalProducers}`,
      `# HELP sfu_consumers Total number of active consumers`,
      `# TYPE sfu_consumers gauge`,
      `sfu_consumers ${totalConsumers}`,
      `# HELP sfu_bytes_sent_total Total bytes sent`,
      `# TYPE sfu_bytes_sent_total counter`,
      `sfu_bytes_sent_total ${totalBytesSent}`,
      `# HELP sfu_bytes_received_total Total bytes received`,
      `# TYPE sfu_bytes_received_total counter`,
      `sfu_bytes_received_total ${totalBytesReceived}`,
      `# HELP sfu_packets_lost_total Total packets lost`,
      `# TYPE sfu_packets_lost_total counter`,
      `sfu_packets_lost_total ${totalPacketsLost}`,
      `# HELP sfu_worker_cpu_usage_total Total CPU usage across workers`,
      `# TYPE sfu_worker_cpu_usage_total counter`,
      `sfu_worker_cpu_usage_total ${totalCpu}`,
      `# HELP sfu_worker_memory_usage_bytes Total memory usage across workers`,
      `# TYPE sfu_worker_memory_usage_bytes gauge`,
      `sfu_worker_memory_usage_bytes ${totalMemory}`,
      `# HELP sfu_avg_rtt_ms Average RTT in milliseconds`,
      `# TYPE sfu_avg_rtt_ms gauge`,
      `sfu_avg_rtt_ms ${statCount > 0 ? (totalRtt / statCount * 1000).toFixed(2) : 0}`,
      `# HELP sfu_avg_jitter_ms Average jitter in milliseconds`,
      `# TYPE sfu_avg_jitter_ms gauge`,
      `sfu_avg_jitter_ms ${statCount > 0 ? (totalJitter / statCount * 1000).toFixed(2) : 0}`,
    ];

    reply.header('Content-Type', 'text/plain; version=0.0.4; charset=utf-8');
    reply.send(metrics.join('\n') + '\n');
  });

  // SFU info endpoint
  fastify.get('/sfu/info', async (request: FastifyRequest, reply: FastifyReply) => {
    reply.send({
      version: '2.0.0',
      mediasoupVersion: (await import('mediasoup')).version,
      workers: workerManager.getWorkerCount(),
      rooms: routerManager.getRoomCount(),
      peers: routerManager.getTotalPeerCount(),
      codecs: (await import('./router.js')).MEDIA_CODECS.map(c => c.mimeType),
    });
  });

  // Create HTTP server for WebSocket upgrade
  const httpServer = createServer(fastify.server);

  // WebSocket Server
  const wss = new WebSocketServer({ noServer: true });

  // Handle HTTP upgrade for WebSocket
  httpServer.on('upgrade', async (req, socket, head) => {
    if (!req.url?.startsWith('/sfu/connect')) {
      socket.destroy();
      return;
    }

    let claims: AuthClaims;
    try {
      const authHeader = req.headers.authorization;
      if (!authHeader) {
        socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
        socket.destroy();
        return;
      }
      claims = authManager.authenticate(authHeader);
    } catch (error) {
      logger.warn({ error: error instanceof Error ? error.message : 'Unknown' }, 'WebSocket auth failed');
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }

    wss.handleUpgrade(req, socket, head, (ws) => {
      wss.emit('connection', ws, req, claims);
    });
  });

  // Handle WebSocket connections
  wss.on('connection', (ws: WebSocket, req, claims: AuthClaims) => {
    const context: PeerContext = {
      roomId: null,
      peerId: null,
      room: null,
      peer: null,
      ws,
      claims,
    };

    logger.info({ redId: claims.redId, sub: claims.sub }, 'WebSocket connected');

    ws.on('message', async (raw: Buffer) => {
      let message: WebSocketMessage;
      try {
        message = JSON.parse(raw.toString());
        const { type, requestId } = message;

        const send = (payload: any) => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ requestId, ...payload }));
          }
        };

        const sendError = (error: Error) => {
          logger.debug({ error: error.message, type: message.type }, 'Client error');
          send(clientErrorPayload(error));
        };

        // Join room
        if (type === 'join') {
          if (context.room) throw new Error('Already joined');
          const roomId = String(message.roomId || '');
          if (!/^[A-Za-z0-9_-]{4,128}$/.test(roomId)) throw new Error('Invalid roomId');

          // Validate room access from ticket
          if (!authManager.validateRoomAccess(claims, roomId)) {
            throw new Error('Ticket not bound to this room');
          }

          const room = await routerManager.getOrCreateRoom(roomId, claims.redId, message.config as Partial<RoomConfig>);
          const peerId = claims.redId;

          // Replace existing connection from same peer
          const existing = room.peers.get(peerId);
          if (existing) {
            existing.ws.close(4001, 'replaced');
            for (const t of existing.transports.values()) t.close();
          }

          const peer = {
            id: peerId,
            accountId: claims.sub,
            redId: peerId,
            displayName: message.displayName,
            ws,
            transports: new Map(),
            producers: new Map(),
            consumers: new Map(),
            canProduce: authManager.canProduce(claims),
            canConsume: authManager.canConsume(claims),
            metadata: message.metadata || {},
            joinedAt: Date.now(),
          };

          routerManager.addPeer(roomId, peer);
          context.roomId = roomId;
          context.peerId = peerId;
          context.room = room;
          context.peer = peer;

          // Get existing producers in room
          const existingProducers: any[] = [];
          for (const [id, p] of room.peers) {
            if (id !== peerId) {
              for (const producer of p.producers.values()) {
                existingProducers.push({
                  peerId: id,
                  producerId: producer.id,
                  kind: producer.kind,
                  appData: producer.appData,
                });
              }
            }
          }

          logger.info({ peerId, roomId, peers: room.peers.size }, 'Peer joined room');
          send({
            status: 'joined',
            peerId,
            rtpCapabilities: room.router.rtpCapabilities,
            existingProducers,
            roomInfo: {
              roomId: room.id,
              createdBy: room.createdBy,
              createdAt: room.createdAt,
              peerCount: room.peers.size,
              recordingActive: !!room.recording,
              liveStreamActive: !!room.liveStream,
            },
          });
          return;
        }

        const peer = context.peer;
        if (!peer) throw new Error('Join a room first');
        const room = context.room;

        // Create Transport
        if (type === 'createTransport') {
          if (peer.transports.size >= 4) throw new Error('Too many transports');
          const transport = await transportManager.createWebRtcTransport(room.router, message.direction);
          peer.transports.set(transport.id, transport);
          transport.on('close', () => peer.transports.delete(transport.id));
          send({
            status: 'transportCreated',
            direction: message.direction,
            transportOptions: transportManager.getTransportOptions(transport),
          });
          return;
        }

        // Connect Transport
        if (type === 'connectTransport') {
          const transport = peer.transports.get(message.transportId);
          if (!transport) throw new Error('Transport not found');
          await transportManager.connectTransport(transport, message.dtlsParameters);
          send({ status: 'transportConnected', transportId: transport.id });
          return;
        }

        // Produce
        if (type === 'produce') {
          if (!peer.canProduce) throw new Error('Produce not permitted by ticket');
          const transport = peer.transports.get(message.transportId);
          if (!transport) throw new Error('Transport not found');

          const result = await mediaManager.produce(
            transport,
            {
              kind: message.kind,
              rtpParameters: message.rtpParameters,
              encodings: message.encodings,
              simulcast: message.simulcast,
              appData: message.appData,
            },
            peer,
            room
          );

          send({ status: 'producing', producerId: result.producerId });
          return;
        }

        // Consume
        if (type === 'consume') {
          if (!peer.canConsume) throw new Error('Consume not permitted by ticket');
          const transport = peer.transports.get(message.transportId);
          if (!transport) throw new Error('Transport not found');

          const result = await mediaManager.consume(
            transport,
            message.producerId,
            message.rtpCapabilities,
            peer,
            room,
            {
              paused: message.paused ?? true,
              preferredLayers: message.preferredLayers,
            }
          );

          send({
            status: 'consuming',
            consumerId: result.consumerId,
            producerId: message.producerId,
            kind: result.consumer.kind,
            rtpParameters: result.rtpParameters,
          });
          return;
        }

        // Resume Consumer
        if (type === 'resumeConsumer') {
          await mediaManager.resumeConsumer(peer, message.consumerId);
          send({ status: 'consumerResumed', consumerId: message.consumerId });
          return;
        }

        // Pause Producer
        if (type === 'pauseProducer') {
          await mediaManager.pauseProducer(peer, message.producerId, room);
          send({ status: 'producerPaused', producerId: message.producerId });
          return;
        }

        // Resume Producer
        if (type === 'resumeProducer') {
          await mediaManager.resumeProducer(peer, message.producerId, room);
          send({ status: 'producerResumed', producerId: message.producerId });
          return;
        }

        // Pause Consumer
        if (type === 'pauseConsumer') {
          await mediaManager.pauseConsumer(peer, message.consumerId);
          send({ status: 'consumerPaused', consumerId: message.consumerId });
          return;
        }

        // Set Consumer Preferred Layers
        if (type === 'setConsumerPreferredLayers') {
          await mediaManager.setConsumerPreferredLayers(
            peer,
            message.consumerId,
            message.spatialLayer,
            message.temporalLayer
          );
          send({ status: 'preferredLayersSet', consumerId: message.consumerId });
          return;
        }

        // Request Key Frame
        if (type === 'requestKeyFrame') {
          await mediaManager.requestKeyFrame(peer, message.consumerId);
          send({ status: 'keyFrameRequested', consumerId: message.consumerId });
          return;
        }

        // Restart ICE
        if (type === 'restartIce') {
          const transport = peer.transports.get(message.transportId);
          if (!transport) throw new Error('Transport not found');
          const iceParameters = await transportManager.restartIce(transport);
          send({ status: 'iceRestarted', iceParameters });
          return;
        }

        // Leave Room
        if (type === 'leave') {
          cleanupPeer(context);
          send({ status: 'left' });
          return;
        }

        // Get Room Stats
        if (type === 'getRoomStats') {
          const stats = routerManager.getRoomStats(context.roomId!);
          send({ status: 'ok', stats });
          return;
        }

        // Start Recording
        if (type === 'startRecording') {
          if (!room.config.enableRecording) throw new Error('Recording not enabled for this room');
          if (room.recording) throw new Error('Recording already in progress');

          const options = message.options as any;
          const session = await recordingManager.startRecording(room, {
            outputPath: options.outputPath,
            format: options.format || 'mp4',
            layout: options.layout || 'grid',
            width: options.width || 1280,
            height: options.height || 720,
            framerate: options.framerate || 30,
            videoBitrate: options.videoBitrate || 2500,
            audioBitrate: options.audioBitrate || 128,
            includeAudio: options.includeAudio !== false,
            metadata: options.metadata,
          });

          routerManager.broadcastToRoom(room, null, {
            type: 'recordingStarted',
            recordingId: session.id,
            roomId: room.id,
          });

          send({ status: 'recordingStarted', recordingId: session.id });
          return;
        }

        // Stop Recording
        if (type === 'stopRecording') {
          if (!room.recording) throw new Error('No active recording');
          await recordingManager.stopRecording(room.recording.id);

          routerManager.broadcastToRoom(room, null, {
            type: 'recordingStopped',
            recordingId: room.recording.id,
            roomId: room.id,
          });

          send({ status: 'recordingStopped' });
          return;
        }

        // Start Live Stream
        if (type === 'startLiveStream') {
          if (!room.config.enableLiveStream) throw new Error('Live streaming not enabled for this room');
          if (room.liveStream) throw new Error('Live stream already in progress');

          const options = message.options as any;
          const session = await liveStreamManager.startLiveStream(room, {
            rtmpUrl: options.rtmpUrl,
            streamKey: options.streamKey,
            format: options.format || 'flv',
            layout: options.layout || 'grid',
            width: options.width || 1280,
            height: options.height || 720,
            framerate: options.framerate || 30,
            videoBitrate: options.videoBitrate || 2500,
            audioBitrate: options.audioBitrate || 128,
            includeAudio: options.includeAudio !== false,
            rtmpOutputs: options.rtmpOutputs,
            srtUrl: options.srtUrl,
          });

          routerManager.broadcastToRoom(room, null, {
            type: 'liveStreamStarted',
            streamId: session.id,
            roomId: room.id,
            rtmpUrl: options.rtmpUrl,
          });

          send({ status: 'liveStreamStarted', streamId: session.id });
          return;
        }

        // Stop Live Stream
        if (type === 'stopLiveStream') {
          if (!room.liveStream) throw new Error('No active live stream');
          await liveStreamManager.stopLiveStream(room.liveStream.id);

          routerManager.broadcastToRoom(room, null, {
            type: 'liveStreamStopped',
            streamId: room.liveStream.id,
            roomId: room.id,
          });

          send({ status: 'liveStreamStopped' });
          return;
        }

        // Create Pipe Transport (for inter-room communication)
        if (type === 'createPipeTransport') {
          const pipeTransport = await routerManager.createPipeTransport(room.id);
          send({
            status: 'pipeTransportCreated',
            transportId: pipeTransport.id,
            tuple: pipeTransport.tuple,
          });
          return;
        }

        // Connect Pipe Transports
        if (type === 'connectPipeTransports') {
          await routerManager.connectPipeTransports(
            message.roomId1,
            message.transportId1,
            message.roomId2,
            message.transportId2
          );
          send({ status: 'pipeTransportsConnected' });
          return;
        }

        // Pipe Producer to Router
        if (type === 'pipeProducer') {
          const pipeProducer = await routerManager.pipeProducerToRouter(
            message.sourceRoomId,
            message.targetRoomId,
            message.producerId
          );
          send({ status: 'producerPiped', producerId: pipeProducer.id });
          return;
        }

        throw new Error('Unknown message type');
      } catch (error) {
        logger.error({ error: error instanceof Error ? error.message : 'Unknown', type: message?.type }, 'SFU error');
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify(clientErrorPayload(error as Error)));
        }
      }
    });

    ws.on('close', (code, reason) => {
      logger.info({ redId: claims.redId, code, reason: reason.toString() }, 'WebSocket closed');
      cleanupPeer(context);
    });

    ws.on('error', (error) => {
      logger.error({ error: error.message, redId: claims.redId }, 'WebSocket error');
    });
  });

  // Handle worker death
  workerManager.on('workerDied', async ({ pid }) => {
    logger.error({ pid }, 'Worker died, closing rooms on that worker');
    await routerManager.closeRoomsOnWorker(pid);
  });

  // Start HTTP server
  await new Promise<void>((resolve) => {
    httpServer.listen(config.port, '0.0.0.0', () => {
      logger.info({
        port: config.port,
        workers: workerManager.getWorkerCount(),
        rtcPortRange: `${config.rtcMinPort}-${config.rtcMaxPort}`,
      }, 'RED Media SFU started');
      resolve();
    });
  });

  return {
    config,
    workerManager,
    routerManager,
    mediaManager,
    transportManager,
    recordingManager,
    liveStreamManager,
    authManager,
    fastify,
    wss,
  };
}

function cleanupPeer(context: PeerContext): void {
  if (!context.peer || !context.room) return;

  const { roomId, peerId, room, peer } = context;

  // Close all media objects
  mediaManager.cleanupPeer(peer);

  // Notify room
  routerManager.removePeer(roomId!, peerId!);

  context.room = null;
  context.peer = null;
  context.roomId = null;
  context.peerId = null;
}

// Global mediaManager reference for cleanup (will be set after buildServer)
let mediaManager: MediaManager;
let routerManager: RouterManager;

async function main(): Promise<void> {
  try {
    const deps = await buildServer();
    mediaManager = deps.mediaManager;
    routerManager = deps.routerManager;

    // Graceful shutdown
    const shutdown = async (signal: string) => {
      logger.info({ signal }, 'Shutting down...');
      try {
        await deps.wss.close();
        await deps.fastify.close();
        await deps.workerManager.closeAll();
        logger.info('Shutdown complete');
        process.exit(0);
      } catch (error) {
        logger.error({ error }, 'Shutdown error');
        process.exit(1);
      }
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));
    process.on('unhandledRejection', (reason) => {
      logger.error({ reason }, 'Unhandled rejection');
    });
    process.on('uncaughtException', (error) => {
      logger.error({ error }, 'Uncaught exception');
      shutdown('uncaughtException');
    });
  } catch (error) {
    logger.error({ error }, 'Failed to start server');
    process.exit(1);
  }
}

main();