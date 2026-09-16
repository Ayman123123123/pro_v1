import { spawn, ChildProcess } from 'child_process';
import { Room, LiveStreamOptions, LiveStreamSession } from './types.js';

export class LiveStreamManager {
  private streams: Map<string, LiveStreamSession> = new Map();
  private defaultRtmpUrl: string;

  constructor(defaultRtmpUrl: string = '') {
    this.defaultRtmpUrl = defaultRtmpUrl;
  }

  async startLiveStream(room: Room, options: LiveStreamOptions): Promise<LiveStreamSession> {
    const streamId = `stream_${room.id}_${Date.now()}`;
    const rtmpUrl = options.rtmpUrl || this.defaultRtmpUrl;

    if (!rtmpUrl) {
      throw new Error('RTMP URL is required');
    }

    const fullUrl = `${rtmpUrl}/${options.streamKey}`;
    const ffmpegArgs = this.buildFfmpegArgs(options, fullUrl);

    console.log(`Starting live stream ${streamId} for room ${room.id} to ${rtmpUrl}`);
    console.log(`FFmpeg command: ffmpeg ${ffmpegArgs.join(' ')}`);

    const process = spawn('ffmpeg', ffmpegArgs, {
      stdio: ['pipe', 'pipe', 'pipe'],
      detached: false,
    });

    const session: LiveStreamSession = {
      id: streamId,
      roomId: room.id,
      options: { ...options, rtmpUrl: fullUrl },
      process,
      startedAt: Date.now(),
      status: 'starting',
    };

    this.streams.set(streamId, session);
    room.liveStream = session;

    process.stdout?.on('data', (data) => {
      console.debug(`[LiveStream ${streamId}] stdout: ${data}`);
    });

    process.stderr?.on('data', (data) => {
      console.debug(`[LiveStream ${streamId}] stderr: ${data}`);
    });

    process.on('close', (code) => {
      console.log(`Live stream ${streamId} ended with code ${code}`);
      session.status = code === 0 ? 'stopped' : 'error';
      this.streams.delete(streamId);
      if (room.liveStream?.id === streamId) {
        room.liveStream = undefined;
      }
    });

    process.on('error', (error) => {
      console.error(`Live stream ${streamId} error:`, error);
      session.status = 'error';
    });

    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Live stream start timeout'));
      }, 15000);

      process.stderr?.on('data', (data) => {
        const output = data.toString();
        if (output.includes('Output #0') || output.includes('Press [q] to stop')) {
          clearTimeout(timeout);
          session.status = 'active';
          resolve();
        }
      });
    });

    return session;
  }

  async stopLiveStream(streamId: string): Promise<boolean> {
    const session = this.streams.get(streamId);
    if (!session) return false;

    console.log(`Stopping live stream ${streamId}`);
    session.status = 'stopping';

    if (session.process && !session.process.killed) {
      session.process.stdin?.write('q');
      await new Promise<void>((resolve) => {
        session.process.on('close', () => resolve());
        setTimeout(() => {
          if (!session.process.killed) {
            session.process.kill('SIGKILL');
          }
          resolve();
        }, 5000);
      });
    }

    return true;
  }

  getStream(streamId: string): LiveStreamSession | undefined {
    return this.streams.get(streamId);
  }

  getRoomStream(roomId: string): LiveStreamSession | undefined {
    for (const session of this.streams.values()) {
      if (session.roomId === roomId) return session;
    }
    return undefined;
  }

  getAllStreams(): LiveStreamSession[] {
    return Array.from(this.streams.values());
  }

  private buildFfmpegArgs(options: LiveStreamOptions, rtmpUrl: string): string[] {
    const args: string[] = [
      '-y',
      '-f', 'rawvideo',
      '-pix_fmt', 'yuv420p',
      '-s', `${options.width}x${options.height}`,
      '-r', String(options.framerate),
      '-i', '-',
    ];

    if (options.includeAudio) {
      args.push('-f', 's16le', '-ar', '48000', '-ac', '2', '-i', '-');
    }

    args.push(
      '-c:v', 'libx264',
      '-b:v', `${options.videoBitrate}k`,
      '-preset', 'veryfast',
      '-tune', 'zerolatency',
      '-g', String(options.framerate * 2),
      '-f', 'flv',
    );

    if (options.includeAudio) {
      args.push('-c:a', 'aac', '-b:a', `${options.audioBitrate}k`, '-ar', '44100');
    }

    args.push(rtmpUrl);

    return args;
  }

  buildLayoutFilter(options: LiveStreamOptions): string {
    // Similar to recording layouts but optimized for streaming
    return this.buildGridLayout(9, options.width, options.height);
  }

  private buildGridLayout(count: number, width: number, height: number): string {
    if (count === 0) return `color=black:${width}x${height}[out]`;
    if (count === 1) return `[0:v]scale=w=${width}:h=${height}[out]`;

    const cols = Math.ceil(Math.sqrt(count));
    const rows = Math.ceil(count / cols);
    const cellW = Math.floor(width / cols);
    const cellH = Math.floor(height / rows);

    let filter = '';
    for (let i = 0; i < count; i++) {
      filter += `[${i}:v]scale=${cellW}:${cellH}[v${i}];`;
    }

    for (let r = 0; r < rows; r++) {
      const rowInputs = [];
      for (let c = 0; c < cols; c++) {
        const idx = r * cols + c;
        if (idx < count) {
          rowInputs.push(`[v${idx}]`);
        } else {
          rowInputs.push(`color=black:${cellW}x${cellH}[black${idx}]`);
        }
      }
      filter += `${rowInputs.join('')}hstack=inputs=${cols}[row${r}];`;
    }

    const rowRefs = Array.from({ length: rows }, (_, i) => `[row${i}]`).join('');
    filter += `${rowRefs}vstack=inputs=${rows}[out]`;

    return filter;
  }
}