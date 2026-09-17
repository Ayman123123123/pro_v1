import { Router, RouterOptions, RtpCapabilities, createAudioLevelObserver } from 'mediasoup';
import { WorkerManager } from './worker.js';
import { MediaCodecs, Room, RoomConfig, AudioLevelObserver as AudioLevelObserverType } from './types.js';

export const MEDIA_CODECS: MediaCodecs[] = [
  {
    kind: 'audio',
    mimeType: 'audio/opus',
    clockRate: 48000,
    channels: 2,
    parameters: {
      useinbandfec: 1,
      usedtx: 1,
      stereo: 0,
      maxplaybackrate: 48000,
      maxaveragebitrate: 64000,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/VP9',
    clockRate: 90000,
    parameters: {
      'profile-id': 0,
      'x-google-start-bitrate': 1000,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/VP8',
    clockRate: 90000,
    parameters: {
      'x-google-start-bitrate': 800,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '42001f',
      'level-asymmetry-allowed': 1,
      'x-google-start-bitrate': 800,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '42e01f',
      'level-asymmetry-allowed': 1,
      'x-google-start-bitrate': 800,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/H264',
    clockRate: 90000,
    parameters: {
      'packetization-mode': 1,
      'profile-level-id': '640032',
      'level-asymmetry-allowed': 1,
    },
  },
  {
    kind: 'video',
    mimeType: 'video/AV1',
    clockRate: 90000,
    parameters: {
      'profile': 0,
      'level-idx': 2,
    },
  },
];

export class RouterManager {
  private workerManager: WorkerManager;
  private rooms: Map<string, Room> = new Map();
  private roomCleanupDelay: number;

  constructor(workerManager: WorkerManager, roomCleanupDelayMs: number) {
    this.workerManager = workerManager;
    this.roomCleanupDelay = roomCleanupDelayMs;
  }

  async createRoom(roomId: string, createdBy: string, config?: Partial<RoomConfig>): Promise<Room> {
    if (this.rooms.has(roomId)) {
      throw new Error(`Room ${roomId} already exists`);
    }

    const worker = this.workerManager.getLeastLoadedWorker();
    const router = await worker.createRouter({ mediaCodecs: MEDIA_CODECS });

    const audioLevelObserver = await router.createAudioLevelObserver({
      maxEntries: 1,
      threshold: -80,
      interval: 800,
    });

    const room: Room = {
      id: roomId,
      router,
      peers: new Map(),
      audioLevelObserver,
      cleanupTimer: null,
      createdAt: Date.now(),
      createdBy,
      config: {
        enableRecording: config?.enableRecording ?? false,
        enableLiveStream: config?.enableLiveStream ?? false,
        enableE2EE: config?.enableE2EE ?? false,
        maxPeers: config?.maxPeers ?? 100,
        recordingLayout: config?.recordingLayout ?? 'grid',
        lastN: config?.lastN ?? 9,
      },
      pipeTransports: new Map(),
    };

    this.setupAudioLevelObserver(room);
    this.rooms.set(roomId, room);
    this.workerManager.updateWorkerStats(worker.pid, { roomCount: this.getWorkerRoomCount(worker.pid) });

    console.log(`Room created: ${roomId} on worker ${worker.pid} (total rooms: ${this.rooms.size})`);
    return room;
  }

  getRoom(roomId: string): Room | undefined {
    return this.rooms.get(roomId);
  }

  getOrCreateRoom(roomId: string, createdBy: string, config?: Partial<RoomConfig>): Promise<Room> {
    const existing = this.rooms.get(roomId);
    if (existing) {
      if (existing.cleanupTimer) {
        clearTimeout(existing.cleanupTimer);
        existing.cleanupTimer = null;
        console.log(`Room cleanup cancelled for ${roomId} — peer rejoined`);
      }
      return Promise.resolve(existing);
    }
    return this.createRoom(roomId, createdBy, config);
  }

  private setupAudioLevelObserver(room: Room): void {
    room.audioLevelObserver.on('volumes', (volumes) => {
      if (volumes.length === 0) return;
      const top = volumes[0];
      const peerId = this.findPeerByProducer(room, top.producerId);
      if (peerId) {
        this.broadcastToRoom(room, null, {
          type: 'activeSpeaker',
          peerId,
        });
      }
    });

    room.audioLevelObserver.on('silence', () => {
      this.broadcastToRoom(room, null, {
        type: 'activeSpeaker',
        peerId: '',
      });
    });
  }

  private findPeerByProducer(room: Room, producerId: string): string | null {
    for (const [id, peer] of room.peers) {
      if (peer.producers.has(producerId)) {
        return id;
      }
    }
    return null;
  }

  addPeer(roomId: string, peer: any): void {
    const room = this.rooms.get(roomId);
    if (!room) throw new Error(`Room ${roomId} not found`);

    if (room.peers.size >= room.config.maxPeers) {
      throw new Error('Room is full');
    }

    room.peers.set(peer.id, peer);
    const worker = this.workerManager.getWorkerByPid(room.router.workerPid);
    if (worker) {
      this.workerManager.updateWorkerStats(worker.pid, { peerCount: this.getWorkerPeerCount(worker.pid) });
    }

    console.log(`Peer ${peer.id} joined room ${roomId} (peers: ${room.peers.size})`);
  }

  removePeer(roomId: string, peerId: string): void {
    const room = this.rooms.get(roomId);
    if (!room) return;

    room.peers.delete(peerId);

    const worker = this.workerManager.getWorkerByPid(room.router.workerPid);
    if (worker) {
      this.workerManager.updateWorkerStats(worker.pid, { peerCount: this.getWorkerPeerCount(worker.pid) });
    }

    this.broadcastToRoom(room, peerId, { type: 'peerLeft', peerId });

    if (room.peers.size === 0) {
      this.scheduleRoomCleanup(roomId);
    }

    console.log(`Peer ${peerId} left room ${roomId}`);
  }

  private scheduleRoomCleanup(roomId: string): void {
    const room = this.rooms.get(roomId);
    if (!room || room.cleanupTimer) return;

    room.cleanupTimer = setTimeout(() => {
      const r = this.rooms.get(roomId);
      if (r && r.peers.size === 0) {
        this.closeRoom(roomId);
        console.log(`Room ${roomId} cleaned up after ${this.roomCleanupDelay}ms idle`);
      }
    }, this.roomCleanupDelay);
  }

  async closeRoom(roomId: string): Promise<void> {
    const room = this.rooms.get(roomId);
    if (!room) return;

    if (room.cleanupTimer) {
      clearTimeout(room.cleanupTimer);
      room.cleanupTimer = null;
    }

    // Notify peers BEFORE tearing down transports/router.
    this.broadcastToRoom(room, null, { type: 'roomClosed', roomId });

    for (const peer of room.peers.values()) {
      this.cleanupPeerMedia(peer);
    }
    room.peers.clear();

    for (const pipeTransport of room.pipeTransports.values()) {
      try {
        pipeTransport.close();
      } catch {
        // Already closed — ignore.
      }
    }
    room.pipeTransports.clear();

    try {
      room.audioLevelObserver.close();
    } catch {
      // Already closed — ignore.
    }

    room.router.close();
    this.rooms.delete(roomId);

    const worker = this.workerManager.getWorkerByPid(room.router.workerPid);
    if (worker) {
      this.workerManager.updateWorkerStats(worker.pid, { roomCount: this.getWorkerRoomCount(worker.pid) });
    }

    console.log(`Room ${roomId} closed`);
  }

  /** Close every room hosted on a dead worker (called on WorkerManager 'workerDied'). */
  async closeRoomsOnWorker(workerPid: number): Promise<void> {
    const roomIds = Array.from(this.rooms.values())
      .filter((room) => {
        try {
          return room.router.workerPid === workerPid;
        } catch {
          return false;
        }
      })
      .map((room) => room.id);
    await Promise.all(roomIds.map((id) => this.closeRoom(id)));
  }

  private cleanupPeerMedia(peer: any): void {
    for (const consumer of peer.consumers.values()) consumer.close();
    for (const producer of peer.producers.values()) producer.close();
    for (const transport of peer.transports.values()) transport.close();
  }

  getRtpCapabilities(roomId: string): RtpCapabilities | null {
    const room = this.rooms.get(roomId);
    return room?.router.rtpCapabilities ?? null;
  }

  getRoomPeerCount(roomId: string): number {
    return this.rooms.get(roomId)?.peers.size ?? 0;
  }

  getAllRooms(): Room[] {
    return Array.from(this.rooms.values());
  }

  getRoomCount(): number {
    return this.rooms.size;
  }

  getTotalPeerCount(): number {
    let count = 0;
    for (const room of this.rooms.values()) {
      count += room.peers.size;
    }
    return count;
  }

  getWorkerRoomCount(workerPid: number): number {
    let count = 0;
    for (const room of this.rooms.values()) {
      if (room.router.workerPid === workerPid) count++;
    }
    return count;
  }

  getWorkerPeerCount(workerPid: number): number {
    let count = 0;
    for (const room of this.rooms.values()) {
      if (room.router.workerPid === workerPid) {
        count += room.peers.size;
      }
    }
    return count;
  }

  broadcastToRoom(room: Room, excludedPeerId: string | null, payload: any): void {
    for (const [id, peer] of room.peers) {
      if (id !== excludedPeerId && peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify(payload));
      }
    }
  }

  async createPipeTransport(roomId: string): Promise<any> {
    const room = this.rooms.get(roomId);
    if (!room) throw new Error(`Room ${roomId} not found`);

    const pipeTransport = await room.router.createPipeTransport({
      listenInfo: { protocol: 'udp', ip: '127.0.0.1' },
      enableSctp: false,
      enableRtx: true,
      enableSrtp: false,
    });

    room.pipeTransports.set(pipeTransport.id, pipeTransport);
    return pipeTransport;
  }

  async connectPipeTransports(roomId1: string, transportId1: string, roomId2: string, transportId2: string): Promise<void> {
    const room1 = this.rooms.get(roomId1);
    const room2 = this.rooms.get(roomId2);
    if (!room1 || !room2) throw new Error('Room not found');

    const transport1 = room1.pipeTransports.get(transportId1);
    const transport2 = room2.pipeTransports.get(transportId2);
    if (!transport1 || !transport2) throw new Error('Pipe transport not found');

    // Bidirectional wiring (the old code only connected one direction).
    await transport1.connect({ ip: '127.0.0.1', port: transport2.tuple.localPort });
    await transport2.connect({ ip: '127.0.0.1', port: transport1.tuple.localPort });
  }

  /**
   * Preferred inter-router piping (mediasoup 3.24): let mediasoup manage the
   * underlying PipeTransports instead of hand-wiring a one-way pair.
   */
  async pipeProducerToRouter(sourceRoomId: string, targetRoomId: string, producerId: string): Promise<any> {
    const source = this.rooms.get(sourceRoomId);
    const target = this.rooms.get(targetRoomId);
    if (!source || !target) throw new Error('Room not found');

    const { pipeProducer } = await source.router.pipeToRouter({
      producerId,
      targetRouter: target.router,
    });
    return pipeProducer;
  }

  /** Pipe every producer of one room into another router. */
  async pipeRoomToRouter(sourceRoomId: string, targetRoomId: string): Promise<any[]> {
    const source = this.rooms.get(sourceRoomId);
    if (!source) throw new Error('Room not found');

    const producerIds = new Set<string>();
    for (const peer of source.peers.values()) {
      for (const producerId of peer.producers.keys()) {
        producerIds.add(producerId);
      }
    }

    const piped: any[] = [];
    for (const producerId of producerIds) {
      piped.push(await this.pipeProducerToRouter(sourceRoomId, targetRoomId, producerId));
    }
    return piped;
  }

  getRoomStats(roomId: string): any {
    const room = this.rooms.get(roomId);
    if (!room) return null;

    const peers = Array.from(room.peers.values());
    return {
      roomId,
      peerCount: peers.length,
      producerCount: peers.reduce((sum, p) => sum + p.producers.size, 0),
      consumerCount: peers.reduce((sum, p) => sum + p.consumers.size, 0),
      createdAt: room.createdAt,
      config: room.config,
    };
  }
}