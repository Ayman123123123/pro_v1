import type { Router, Worker, WebRtcTransport, Producer, Consumer, PipeTransport, AudioLevelObserver, RtpCapabilities, IceParameters, IceCandidate, DtlsParameters, SctpParameters, RtpParameters } from 'mediasoup';
import type { WebSocket } from 'ws';

export interface MediaCodecs {
  kind: 'audio' | 'video';
  mimeType: string;
  clockRate: number;
  channels?: number;
  parameters?: Record<string, string | number>;
}

export interface Peer {
  id: string;
  accountId: string;
  redId: string;
  displayName?: string;
  ws: WebSocket;
  transports: Map<string, WebRtcTransport>;
  producers: Map<string, Producer>;
  consumers: Map<string, Consumer>;
  canProduce: boolean;
  canConsume: boolean;
  metadata: Record<string, string>;
  joinedAt: number;
}

export interface Room {
  id: string;
  router: Router;
  peers: Map<string, Peer>;
  audioLevelObserver: AudioLevelObserver;
  cleanupTimer: NodeJS.Timeout | null;
  createdAt: number;
  createdBy: string;
  config: RoomConfig;
  recording?: RecordingSession;
  liveStream?: LiveStreamSession;
  pipeTransports: Map<string, PipeTransport>;
}

export interface RoomConfig {
  enableRecording: boolean;
  enableLiveStream: boolean;
  enableE2EE: boolean;
  maxPeers: number;
  recordingLayout: RecordingLayout;
  lastN: number;
}

export type RecordingLayout = 'grid' | 'speaker' | 'pip' | 'custom';

export interface RecordingSession {
  id: string;
  roomId: string;
  options: RecordingOptions;
  process: any;
  startedAt: number;
  status: 'starting' | 'active' | 'stopping' | 'stopped' | 'error';
}

export interface LiveStreamSession {
  id: string;
  roomId: string;
  options: LiveStreamOptions;
  process: any;
  startedAt: number;
  status: 'starting' | 'active' | 'stopping' | 'stopped' | 'error';
}

export interface RecordingOptions {
  outputPath: string;
  format: 'mp4' | 'webm';
  layout: RecordingLayout;
  width: number;
  height: number;
  framerate: number;
  videoBitrate: number;
  audioBitrate: number;
  includeAudio: boolean;
  metadata?: Record<string, string>;
}

export interface RtmpOutput {
  url: string;
  streamKey?: string;
}

export interface LiveStreamOptions {
  rtmpUrl: string;
  streamKey: string;
  format: 'flv' | 'mp4';
  layout: RecordingLayout;
  width: number;
  height: number;
  framerate: number;
  videoBitrate: number;
  audioBitrate: number;
  includeAudio: boolean;
  // Additional RTMP outputs (YouTube, Twitch, Custom)
  rtmpOutputs?: RtmpOutput[];
  // SRT output for lower latency
  srtUrl?: string;
}

export interface TransportInfo {
  id: string;
  iceParameters: IceParameters;
  iceCandidates: IceCandidate[];
  dtlsParameters: DtlsParameters;
  sctpParameters: SctpParameters | undefined;
  direction: 'send' | 'recv';
}

export interface ProducerInfo {
  id: string;
  peerId: string;
  kind: 'audio' | 'video';
  rtpParameters: any;
  appData: ProducerAppData;
}

export interface ProducerAppData {
  peerId: string;
  redId: string;
  kind: 'audio' | 'video';
  simulcast: boolean;
  displayName?: string;
}

export interface ConsumerInfo {
  id: string;
  producerId: string;
  peerId: string;
  kind: 'audio' | 'video';
  rtpParameters: any;
  preferredLayers?: { spatialLayer: number; temporalLayer: number };
}

export interface WorkerInfo {
  pid: number;
  roomCount: number;
  peerCount: number;
  cpuUsage: number;
  memoryUsage: number;
}

export interface SFUStats {
  workers: WorkerInfo[];
  totalRooms: number;
  totalPeers: number;
  totalProducers: number;
  totalConsumers: number;
}

export interface RTTStat {
  min: number;
  max: number;
  avg: number;
}

export interface JitterStat {
  min: number;
  max: number;
  avg: number;
}

export interface WebSocketMessage {
  type: string;
  requestId?: string;
  [key: string]: any;
}

export interface WebSocketResponse {
  requestId?: string;
  status?: string;
  error?: string;
  [key: string]: any;
}

export interface JoinMessage extends WebSocketMessage {
  type: 'join';
  roomId: string;
}

export interface CreateTransportMessage extends WebSocketMessage {
  type: 'createTransport';
  direction: 'send' | 'recv';
}

export interface ConnectTransportMessage extends WebSocketMessage {
  type: 'connectTransport';
  transportId: string;
  dtlsParameters: DtlsParameters;
}

export interface ProduceMessage extends WebSocketMessage {
  type: 'produce';
  transportId: string;
  kind: 'audio' | 'video';
  rtpParameters: any;
  simulcast?: boolean;
  encodings?: any[];
  appData?: ProducerAppData;
}

export interface ConsumeMessage extends WebSocketMessage {
  type: 'consume';
  transportId: string;
  producerId: string;
  rtpCapabilities: any;
  paused?: boolean;
  preferredLayers?: { spatialLayer: number; temporalLayer: number };
}

export interface ResumeConsumerMessage extends WebSocketMessage {
  type: 'resumeConsumer';
  consumerId: string;
}

export interface PauseProducerMessage extends WebSocketMessage {
  type: 'pauseProducer';
  producerId: string;
}

export interface ResumeProducerMessage extends WebSocketMessage {
  type: 'resumeProducer';
  producerId: string;
}

export interface SetConsumerPreferredLayersMessage extends WebSocketMessage {
  type: 'setConsumerPreferredLayers';
  consumerId: string;
  spatialLayer: number;
  temporalLayer: number;
}

export interface RequestKeyFrameMessage extends WebSocketMessage {
  type: 'requestKeyFrame';
  consumerId: string;
}

export interface RestartIceMessage extends WebSocketMessage {
  type: 'restartIce';
  transportId: string;
}

export interface LeaveMessage extends WebSocketMessage {
  type: 'leave';
}

export type ClientMessage =
  | JoinMessage
  | CreateTransportMessage
  | ConnectTransportMessage
  | ProduceMessage
  | ConsumeMessage
  | ResumeConsumerMessage
  | PauseProducerMessage
  | ResumeProducerMessage
  | SetConsumerPreferredLayersMessage
  | RequestKeyFrameMessage
  | RestartIceMessage
  | LeaveMessage;

export interface ServerEvents {
  'newProducer': { peerId: string; producerId: string; kind: string; appData?: ProducerAppData };
  'producerClosed': { peerId: string; producerId: string; consumerId: string };
  'producerPaused': { peerId: string; producerId: string };
  'producerResumed': { peerId: string; producerId: string };
  'activeSpeaker': { peerId: string };
  'peerJoined': { peerId: string; displayName?: string; producers: ProducerInfo[] };
  'peerLeft': { peerId: string };
  'networkDegraded': { peerId: string; consumerId: string; score: number };
  'consumerScore': { consumerId: string; score: number; producerScore: number };
  'consumerLayersChanged': { consumerId: string; spatialLayer: number | null; temporalLayer: number | null };
  'roomClosed': { roomId: string };
  'recordingStarted': { recordingId: string; roomId: string };
  'recordingStopped': { recordingId: string; roomId: string };
  'liveStreamStarted': { streamId: string; roomId: string; rtmpUrl: string };
  'liveStreamStopped': { streamId: string; roomId: string };
  'iceRestartNeeded': { transportId: string; iceParameters?: IceParameters };
  'connectionStateChange': { peerId: string; state: 'connected' | 'disconnected' | 'failed' };
}

export type EventNames = keyof ServerEvents;

export interface AuthClaims {
  sub: string;
  redId: string;
  exp: number;
  iat?: number;
  iss?: string;
  aud?: string | string[];
  sfuGroupId?: string;
  sfuCanProduce?: boolean;
  sfuCanConsume?: boolean;
  [key: string]: any;
}

export interface GrpcRoomInfo {
  roomId: string;
  peerCount: number;
  producerCount: number;
  consumerCount: number;
  recordingActive: boolean;
  liveStreamActive: boolean;
  createdAt: number;
}

export interface GrpcPeerInfo {
  peerId: string;
  displayName: string;
  producerCount: number;
  consumerCount: number;
  joinedAt: number;
}

export interface HealthStatus {
  status: 'UP' | 'STARTING' | 'DOWN';
  workers: number;
  rooms: number;
  peers: number;
  uptime: number;
  version: string;
}