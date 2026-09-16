import { z } from 'zod';

export const ConfigSchema = z.object({
  port: z.number().int().min(1).max(65535).default(4000),
  rtcMinPort: z.number().int().min(1024).max(65535).default(40000),
  rtcMaxPort: z.number().int().min(1024).max(65535).default(40100),
  workerCount: z.number().int().min(1).default(() => Math.min(4, require('os').cpus().length)),
  announcedIp: z.string().default(''),
  jwtSecret: z.string().min(32),
  sfuTicketSecret: z.string().min(32).optional(),
  roomCleanupDelayMs: z.number().int().min(1000).default(30_000),
  maxProducersPerKind: z.number().int().min(1).max(10).default(4),
  grpcPort: z.number().int().min(1).max(65535).default(50051),
  enableMetrics: z.boolean().default(true),
  enableTracing: z.boolean().default(true),
  logLevel: z.enum(['error', 'warn', 'info', 'debug']).default('info'),
  turnServer: z.object({
    urls: z.array(z.string()).default([]),
    username: z.string().default(''),
    credential: z.string().default(''),
  }).optional(),
  recording: z.object({
    outputDir: z.string().default('./recordings'),
    ffmpegPath: z.string().default('ffmpeg'),
  }).optional(),
  liveStream: z.object({
    defaultRtmpUrl: z.string().default(''),
  }).optional(),
});

export type Config = z.infer<typeof ConfigSchema>;

export function loadConfig(): Config {
  const config = ConfigSchema.parse({
    port: Number(process.env.PORT) || 4000,
    rtcMinPort: Number(process.env.RTC_MIN_PORT) || 40000,
    rtcMaxPort: Number(process.env.RTC_MAX_PORT) || 40100,
    workerCount: Number(process.env.MEDIASOUP_WORKERS) || Math.min(4, require('os').cpus().length),
    announcedIp: process.env.MEDIASOUP_ANNOUNCED_IP || '',
    jwtSecret: process.env.JWT_SECRET || '',
    sfuTicketSecret: process.env.SFU_TICKET_SECRET || process.env.JWT_SECRET,
    roomCleanupDelayMs: Number(process.env.ROOM_CLEANUP_DELAY_MS) || 30_000,
    maxProducersPerKind: Number(process.env.MAX_PRODUCERS_PER_KIND) || 4,
    grpcPort: Number(process.env.GRPC_PORT) || 50051,
    enableMetrics: process.env.ENABLE_METRICS !== 'false',
    enableTracing: process.env.ENABLE_TRACING !== 'false',
    logLevel: (process.env.LOG_LEVEL as Config['logLevel']) || 'info',
    turnServer: process.env.TURN_URLS ? {
      urls: process.env.TURN_URLS.split(','),
      username: process.env.TURN_USERNAME || '',
      credential: process.env.TURN_CREDENTIAL || '',
    } : undefined,
    recording: process.env.RECORDING_OUTPUT_DIR ? {
      outputDir: process.env.RECORDING_OUTPUT_DIR,
      ffmpegPath: process.env.FFMPEG_PATH || 'ffmpeg',
    } : undefined,
    liveStream: process.env.DEFAULT_RTMP_URL ? {
      defaultRtmpUrl: process.env.DEFAULT_RTMP_URL,
    } : undefined,
  });

  if (!config.announcedIp) {
    console.warn('MEDIASOUP_ANNOUNCED_IP is unset; LAN/WAN ICE candidates may be unreachable');
  }

  return config;
}