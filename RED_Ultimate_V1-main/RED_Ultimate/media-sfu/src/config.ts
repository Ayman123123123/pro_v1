import { cpus } from 'os';
import { z } from 'zod';

const rejectPlaceholder = (name: string) =>
  z.string().min(32).refine(
    (v) => {
      const norm = v.trim().toLowerCase();
      return norm !== 'change-me' && norm !== 'changeme' && norm !== 'change_me';
    },
    { message: `${name} must not be the placeholder value 'change-me'` },
  );

export const ConfigSchema = z.object({
  port: z.coerce.number().int().min(1).max(65535).default(4000),
  rtcMinPort: z.coerce.number().int().min(1024).max(65535).default(40000),
  rtcMaxPort: z.coerce.number().int().min(1024).max(65535).default(40100),
  workerCount: z.coerce.number().int().min(1).default(() => Math.min(4, cpus().length)),
  announcedIp: z.string().default(''),
  jwtSecret: rejectPlaceholder('JWT_SECRET'),
  sfuTicketSecret: rejectPlaceholder('SFU_TICKET_SECRET'),
  jwtIssuer: z.string().min(1).optional(),
  jwtAudience: z.string().min(1).optional(),
  roomCleanupDelayMs: z.coerce.number().int().min(1000).default(30_000),
  maxProducersPerKind: z.coerce.number().int().min(1).max(10).default(4),
  grpcPort: z.coerce.number().int().min(1).max(65535).default(50051),
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
  otel: z.object({
    serviceName: z.string().default('red-media-sfu'),
    endpoint: z.string().optional(),
    exporter: z.enum(['prometheus', 'otlp', 'console']).default('prometheus'),
  }).optional(),
}).refine((config) => config.sfuTicketSecret !== config.jwtSecret, {
  path: ['sfuTicketSecret'], message: 'SFU_TICKET_SECRET must differ from JWT_SECRET',
});

export type Config = z.infer<typeof ConfigSchema>;

export function loadConfig(): Config {
  const config = ConfigSchema.parse({
    port: process.env.PORT ?? 4000,
    rtcMinPort: process.env.RTC_MIN_PORT ?? 40000,
    rtcMaxPort: process.env.RTC_MAX_PORT ?? 40100,
    workerCount: process.env.MEDIASOUP_WORKERS,
    announcedIp: process.env.MEDIASOUP_ANNOUNCED_IP ?? '',
    jwtSecret: process.env.JWT_SECRET ?? '',
    sfuTicketSecret: process.env.SFU_TICKET_SECRET ?? '',
    jwtIssuer: process.env.JWT_ISSUER,
    jwtAudience: process.env.JWT_AUDIENCE,
    roomCleanupDelayMs: process.env.ROOM_CLEANUP_DELAY_MS ?? 30_000,
    maxProducersPerKind: process.env.MAX_PRODUCERS_PER_KIND ?? 4,
    grpcPort: process.env.GRPC_PORT ?? 50051,
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
    otel: process.env.OTEL_ENDPOINT ? {
      serviceName: process.env.OTEL_SERVICE_NAME || 'red-media-sfu',
      endpoint: process.env.OTEL_ENDPOINT,
      exporter: (process.env.OTEL_EXPORTER as Config['otel']['exporter']) || 'prometheus',
    } : undefined,
  });

  if (process.env.NODE_ENV === 'production' && !config.announcedIp) {
    throw new Error('MEDIASOUP_ANNOUNCED_IP is required in production; refusing to start without a public announced IP');
  }

  if (!config.announcedIp) {
    console.warn('MEDIASOUP_ANNOUNCED_IP is unset; LAN/WAN ICE candidates may be unreachable');
  }

  return config;
}
