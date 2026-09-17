import { loadPackageDefinition, Server, ServerCredentials, ServerUnaryCall, sendUnaryData, handleUnaryCall, handleServerStreamingCall, ServerWritableStream, UntypedServiceImplementation, ServiceDefinition } from '@grpc/grpc-js';
import { loadSync } from '@grpc/proto-loader';
import { join } from 'path';
import { RouterManager } from './router.js';
import { WorkerManager } from './worker.js';
import { RecordingManager } from './recording.js';
import { LiveStreamManager } from './livestream.js';
import { AuthManager } from './auth.js';
import { MediaManager } from './media.js';
import { TransportManager } from './transport.js';
import { Config } from './config.js';
import pino from 'pino';

const logger = pino({ name: 'grpc-service' });

const PROTO_PATH = join(process.cwd(), 'src/proto/sfu.proto');

const packageDefinition = loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});

const proto = loadPackageDefinition(packageDefinition) as any;
const sfuService = proto.red.sfu.v1.SFUService;

export interface GrpcDependencies {
  routerManager: RouterManager;
  workerManager: WorkerManager;
  recordingManager: RecordingManager;
  liveStreamManager: LiveStreamManager;
  authManager: AuthManager;
  mediaManager: MediaManager;
  transportManager: TransportManager;
  config: Config;
}

export function createGrpcService(deps: GrpcDependencies): UntypedServiceImplementation {
  const { routerManager, workerManager, recordingManager, liveStreamManager, authManager, mediaManager, transportManager, config } = deps;

  return {
    CreateRoom: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, created_by, config: roomConfig } = call.request;
        const room = await routerManager.createRoom(room_id, created_by, {
          enableRecording: roomConfig?.enable_recording,
          enableLiveStream: roomConfig?.enable_live_stream,
          enableE2EE: roomConfig?.enable_e2ee,
          maxPeers: roomConfig?.max_peers,
          recordingLayout: roomConfig?.recording_layout,
          lastN: roomConfig?.last_n,
        });

        callback(null, {
          room_id: room.id,
          rtp_capabilities: room.router.rtpCapabilities,
          created_at: String(room.createdAt),
        });
      } catch (error) {
        logger.error({ error }, 'CreateRoom failed');
        callback(error as Error, null);
      }
    },

    JoinRoom: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, display_name, can_produce, can_consume, metadata, ticket, token } = call.request;

        let claims;
        try {
          claims = authManager.authenticate(ticket || token || '');
        } catch {
          return callback(new Error('Unauthorized'), null);
        }

        const room = await routerManager.getOrCreateRoom(room_id, created_by, {});

        // Check if peer already exists
        if (room.peers.has(peer_id)) {
          return callback(new Error('Peer already in room'), null);
        }

        const peer = {
          id: peer_id,
          accountId: claims.sub,
          redId: peer_id,
          displayName: display_name,
          ws: null,
          transports: new Map(),
          producers: new Map(),
          consumers: new Map(),
          canProduce: can_produce ?? authManager.canProduce(claims),
          canConsume: can_consume ?? authManager.canConsume(claims),
          metadata: metadata || {},
          joinedAt: Date.now(),
        };

        routerManager.addPeer(room_id, peer);

        const existingProducers: any[] = [];
        for (const [id, p] of room.peers) {
          if (id !== peer_id) {
            for (const producer of p.producers.values()) {
              existingProducers.push({
                producer_id: producer.id,
                peer_id: id,
                kind: producer.kind,
                display_name: producer.appData?.displayName,
              });
            }
          }
        }

        callback(null, {
          peer_id,
          rtp_capabilities: room.router.rtpCapabilities,
          existing_producers: existingProducers,
          room_info: {
            room_id: room.id,
            created_by: room.createdBy,
            created_at: String(room.createdAt),
            peer_count: room.peers.size,
            recording_active: !!room.recording,
            live_stream_active: !!room.liveStream,
          },
        });
      } catch (error) {
        logger.error({ error }, 'JoinRoom failed');
        callback(error as Error, null);
      }
    },

    LeaveRoom: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id } = call.request;
        routerManager.removePeer(room_id, peer_id);
        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'LeaveRoom failed');
        callback(error as Error, null);
      }
    },

    CreateTransport: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, direction } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        const transport = await transportManager.createWebRtcTransport(room.router, direction === 'TRANSPORT_DIRECTION_SEND' ? 'send' : 'recv');
        peer.transports.set(transport.id, transport);

        const options = transportManager.getTransportOptions(transport);
        callback(null, {
          transport_id: options.id,
          ice_parameters: options.iceParameters,
          ice_candidates: options.iceCandidates,
          dtls_parameters: options.dtlsParameters,
          sctp_parameters: options.sctpParameters,
        });
      } catch (error) {
        logger.error({ error }, 'CreateTransport failed');
        callback(error as Error, null);
      }
    },

    ConnectTransport: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, transport_id, dtls_parameters } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        const transport = peer.transports.get(transport_id);
        if (!transport) return callback(new Error('Transport not found'), null);

        await transportManager.connectTransport(transport, dtls_parameters);
        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'ConnectTransport failed');
        callback(error as Error, null);
      }
    },

    RestartIce: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, transport_id } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        const transport = peer.transports.get(transport_id);
        if (!transport) return callback(new Error('Transport not found'), null);

        const iceParameters = await transportManager.restartIce(transport);
        callback(null, { ice_parameters: iceParameters });
      } catch (error) {
        logger.error({ error }, 'RestartIce failed');
        callback(error as Error, null);
      }
    },

    CreateProducer: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, transport_id, options } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        if (!peer.canProduce) return callback(new Error('Produce not permitted'), null);

        const transport = peer.transports.get(transport_id);
        if (!transport) return callback(new Error('Transport not found'), null);

        const result = await mediaManager.produce(transport, {
          kind: options.kind === 'KIND_AUDIO' ? 'audio' : 'video',
          rtpParameters: options.rtp_parameters,
          encodings: options.encodings?.encodings,
          simulcast: options.simulcast,
          appData: options.app_data,
        }, peer, room);

        callback(null, {
          producer_id: result.producerId,
          transport_id,
        });
      } catch (error) {
        logger.error({ error }, 'CreateProducer failed');
        callback(error as Error, null);
      }
    },

    CreateConsumer: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, producer_id, transport_id, options } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        if (!peer.canConsume) return callback(new Error('Consume not permitted'), null);

        const transport = peer.transports.get(transport_id);
        if (!transport) return callback(new Error('Transport not found'), null);

        const result = await mediaManager.consume(transport, producer_id, options.rtp_capabilities, peer, room, {
          paused: options.paused,
          preferredLayers: options.preferred_layers ? {
            spatialLayer: options.preferred_layers.spatial_layer,
            temporalLayer: options.preferred_layers.temporal_layer,
          } : undefined,
          enableRtx: options.enable_rtx,
          pipe: options.pipe,
        });

        callback(null, {
          consumer_id: result.consumerId,
          rtp_parameters: result.rtpParameters,
          producer_info: {
            producer_id: producer_id,
            peer_id: result.consumer.producerId,
            kind: result.consumer.kind,
            display_name: '',
          },
        });
      } catch (error) {
        logger.error({ error }, 'CreateConsumer failed');
        callback(error as Error, null);
      }
    },

    Pause: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, producer_id, consumer_id } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        if (producer_id) {
          await mediaManager.pauseProducer(peer, producer_id, room);
        } else if (consumer_id) {
          await mediaManager.pauseConsumer(peer, consumer_id);
        }

        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'Pause failed');
        callback(error as Error, null);
      }
    },

    Resume: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, producer_id, consumer_id } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        if (producer_id) {
          await mediaManager.resumeProducer(peer, producer_id, room);
        } else if (consumer_id) {
          await mediaManager.resumeConsumer(peer, consumer_id);
        }

        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'Resume failed');
        callback(error as Error, null);
      }
    },

    SetLayers: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, consumer_id, spatial_layer, temporal_layer } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        await mediaManager.setConsumerPreferredLayers(peer, consumer_id, spatial_layer, temporal_layer);
        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'SetLayers failed');
        callback(error as Error, null);
      }
    },

    KeyFrame: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, peer_id, consumer_id } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        const peer = room.peers.get(peer_id);
        if (!peer) return callback(new Error('Peer not found'), null);

        await mediaManager.requestKeyFrame(peer, consumer_id);
        callback(null, { success: true });
      } catch (error) {
        logger.error({ error }, 'KeyFrame failed');
        callback(error as Error, null);
      }
    },

    GetRoomStats: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id } = call.request;
        const stats = routerManager.getRoomStats(room_id);
        if (!stats) return callback(new Error('Room not found'), null);

        callback(null, {
          room_id: stats.roomId,
          peer_count: stats.peerCount,
          producer_count: stats.producerCount,
          consumer_count: stats.consumerCount,
          total_bytes_sent: '0',
          total_bytes_received: '0',
          total_packets_lost: '0',
          avg_rtt_ms: 0,
          avg_jitter_ms: 0,
          peers: [],
        });
      } catch (error) {
        logger.error({ error }, 'GetRoomStats failed');
        callback(error as Error, null);
      }
    },

    StartRecording: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, options } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        if (!room.config.enableRecording) return callback(new Error('Recording not enabled'), null);
        if (room.recording) return callback(new Error('Recording already in progress'), null);

        const session = await recordingManager.startRecording(room, {
          outputPath: options.output_path,
          format: options.format === 'CONTAINER_MP4' ? 'mp4' : options.format === 'CONTAINER_WEBM' ? 'webm' : 'mp4',
          layout: options.layout === 'RECORDING_LAYOUT_GRID' ? 'grid' :
                  options.layout === 'RECORDING_LAYOUT_SPEAKER' ? 'speaker' :
                  options.layout === 'RECORDING_LAYOUT_PIP' ? 'pip' : 'grid',
          width: options.width,
          height: options.height,
          framerate: options.framerate,
          videoBitrate: options.video_bitrate,
          audioBitrate: options.audio_bitrate,
          includeAudio: options.include_audio,
          metadata: options.metadata,
        });

        callback(null, { recording_id: session.id, status: session.status });
      } catch (error) {
        logger.error({ error }, 'StartRecording failed');
        callback(error as Error, null);
      }
    },

    StopRecording: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { recording_id } = call.request;
        const success = await recordingManager.stopRecording(recording_id);
        callback(null, { success });
      } catch (error) {
        logger.error({ error }, 'StopRecording failed');
        callback(error as Error, null);
      }
    },

    StartLiveStream: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { room_id, options } = call.request;
        const room = routerManager.getRoom(room_id);
        if (!room) return callback(new Error('Room not found'), null);

        if (!room.config.enableLiveStream) return callback(new Error('Live streaming not enabled'), null);
        if (room.liveStream) return callback(new Error('Live stream already in progress'), null);

        const session = await liveStreamManager.startLiveStream(room, {
          rtmpUrl: options.rtmp_url,
          streamKey: options.stream_key,
          format: options.format === 'CONTAINER_FLV' ? 'flv' : 'mp4',
          layout: options.layout === 'RECORDING_LAYOUT_GRID' ? 'grid' :
                  options.layout === 'RECORDING_LAYOUT_SPEAKER' ? 'speaker' :
                  options.layout === 'RECORDING_LAYOUT_PIP' ? 'pip' : 'grid',
          width: options.width,
          height: options.height,
          framerate: options.framerate,
          videoBitrate: options.video_bitrate,
          audioBitrate: options.audio_bitrate,
          includeAudio: options.include_audio,
          rtmpOutputs: options.rtmp_outputs?.map((o: any) => ({ url: o.url, streamKey: o.stream_key })),
          srtUrl: options.srt_url,
        });

        callback(null, { stream_id: session.id, status: session.status });
      } catch (error) {
        logger.error({ error }, 'StartLiveStream failed');
        callback(error as Error, null);
      }
    },

    StopLiveStream: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const { stream_id } = call.request;
        const success = await liveStreamManager.stopLiveStream(stream_id);
        callback(null, { success });
      } catch (error) {
        logger.error({ error }, 'StopLiveStream failed');
        callback(error as Error, null);
      }
    },

    GetWorkerStats: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const workerStats = workerManager.getWorkerStats();
        const workers = workerManager.getAllWorkers();

        callback(null, {
          worker_count: workers.length,
          workers: workerStats.map(w => ({
            pid: w.pid,
            room_count: w.roomCount,
            peer_count: w.peerCount,
            cpu_usage_percent: w.cpuUsage,
            memory_usage_bytes: String(w.memoryUsage),
          })),
          total_rooms: routerManager.getRoomCount(),
          total_peers: routerManager.getTotalPeerCount(),
          cpu_usage_percent: workerStats.reduce((sum, w) => sum + w.cpuUsage, 0),
          memory_usage_bytes: String(workerStats.reduce((sum, w) => sum + w.memoryUsage, 0)),
        });
      } catch (error) {
        logger.error({ error }, 'GetWorkerStats failed');
        callback(error as Error, null);
      }
    },

    HealthCheck: async (call: ServerUnaryCall<any, any>, callback: sendUnaryData<any>) => {
      try {
        const healthy = workerManager.getWorkerCount() > 0;
        callback(null, {
          status: healthy ? 'HEALTH_STATUS_UP' : 'HEALTH_STATUS_STARTING',
          workers: workerManager.getWorkerCount(),
          rooms: routerManager.getRoomCount(),
          peers: routerManager.getTotalPeerCount(),
          uptime_seconds: Math.floor(process.uptime()),
          version: '2.0.0',
        });
      } catch (error) {
        logger.error({ error }, 'HealthCheck failed');
        callback(error as Error, null);
      }
    },

    Subscribe: (call: ServerWritableStream<any, any>) => {
      const { room_id, peer_id, ticket, token, event_types } = call.request;

      try {
        authManager.authenticate(ticket || token || '');
      } catch {
        call.emit('error', new Error('Unauthorized'));
        return;
      }

      const room = routerManager.getRoom(room_id);
      if (!room) {
        call.emit('error', new Error('Room not found'));
        return;
      }

      // Send initial events
      const sendEvent = (type: string, payload: any) => {
        call.write({
          type: `SFU_EVENT_TYPE_${type.toUpperCase()}`,
          room_id,
          peer_id,
          ...payload,
          timestamp_ms: Date.now(),
        });
      };

      // Subscribe to room events
      const onNewProducer = (data: any) => sendEvent('NEW_PRODUCER', data);
      const onProducerClosed = (data: any) => sendEvent('PRODUCER_CLOSED', data);
      const onProducerPaused = (data: any) => sendEvent('PRODUCER_PAUSED', data);
      const onProducerResumed = (data: any) => sendEvent('PRODUCER_RESUMED', data);
      const onActiveSpeaker = (data: any) => sendEvent('ACTIVE_SPEAKER', data);
      const onPeerJoined = (data: any) => sendEvent('PEER_JOINED', data);
      const onPeerLeft = (data: any) => sendEvent('PEER_LEFT', data);
      const onNetworkDegraded = (data: any) => sendEvent('NETWORK_DEGRADED', data);
      const onConsumerScore = (data: any) => sendEvent('CONSUMER_SCORE', data);
      const onConsumerLayersChanged = (data: any) => sendEvent('CONSUMER_LAYERS_CHANGED', data);
      const onRoomClosed = (data: any) => sendEvent('ROOM_CLOSED', data);
      const onRecordingStarted = (data: any) => sendEvent('RECORDING_STARTED', data);
      const onRecordingStopped = (data: any) => sendEvent('RECORDING_STOPPED', data);
      const onLiveStreamStarted = (data: any) => sendEvent('LIVE_STREAM_STARTED', data);
      const onLiveStreamStopped = (data: any) => sendEvent('LIVE_STREAM_STOPPED', data);
      const onIceRestartNeeded = (data: any) => sendEvent('ICE_RESTART_NEEDED', data);
      const onConnectionStateChange = (data: any) => sendEvent('CONNECTION_STATE_CHANGE', data);

      routerManager.on('newProducer', onNewProducer);
      routerManager.on('producerClosed', onProducerClosed);
      routerManager.on('producerPaused', onProducerPaused);
      routerManager.on('producerResumed', onProducerResumed);
      routerManager.on('activeSpeaker', onActiveSpeaker);
      routerManager.on('peerJoined', onPeerJoined);
      routerManager.on('peerLeft', onPeerLeft);
      routerManager.on('networkDegraded', onNetworkDegraded);
      routerManager.on('consumerScore', onConsumerScore);
      routerManager.on('consumerLayersChanged', onConsumerLayersChanged);
      routerManager.on('roomClosed', onRoomClosed);
      routerManager.on('recordingStarted', onRecordingStarted);
      routerManager.on('recordingStopped', onRecordingStopped);
      routerManager.on('liveStreamStarted', onLiveStreamStarted);
      routerManager.on('liveStreamStopped', onLiveStreamStopped);
      routerManager.on('iceRestartNeeded', onIceRestartNeeded);
      routerManager.on('connectionStateChange', onConnectionStateChange);

      call.on('close', () => {
        routerManager.off('newProducer', onNewProducer);
        routerManager.off('producerClosed', onProducerClosed);
        routerManager.off('producerPaused', onProducerPaused);
        routerManager.off('producerResumed', onProducerResumed);
        routerManager.off('activeSpeaker', onActiveSpeaker);
        routerManager.off('peerJoined', onPeerJoined);
        routerManager.off('peerLeft', onPeerLeft);
        routerManager.off('networkDegraded', onNetworkDegraded);
        routerManager.off('consumerScore', onConsumerScore);
        routerManager.off('consumerLayersChanged', onConsumerLayersChanged);
        routerManager.off('roomClosed', onRoomClosed);
        routerManager.off('recordingStarted', onRecordingStarted);
        routerManager.off('recordingStopped', onRecordingStopped);
        routerManager.off('liveStreamStarted', onLiveStreamStarted);
        routerManager.off('liveStreamStopped', onLiveStreamStopped);
        routerManager.off('iceRestartNeeded', onIceRestartNeeded);
        routerManager.off('connectionStateChange', onConnectionStateChange);
      });
    },
  };
}

export function startGrpcServer(deps: GrpcDependencies, port: number): Server {
  const server = new Server();
  const service = createGrpcService(deps);
  server.addService(sfuService, service);
  
  return new Promise((resolve, reject) => {
    server.bindAsync(`0.0.0.0:${port}`, ServerCredentials.createInsecure(), (err, port) => {
      if (err) {
        logger.error({ err }, 'Failed to bind gRPC server');
        reject(err);
      } else {
        logger.info({ port }, 'gRPC server started');
        resolve(server);
      }
    });
  });
}