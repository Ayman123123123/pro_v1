import { spawn, ChildProcess } from 'child_process';
import { mkdirSync, statfsSync } from 'fs';
import { dirname } from 'path';
import { Room, RecordingOptions, RecordingSession } from './types.js';
import { MediaManager } from './media.js';

export class RecordingManager {
  private recordings: Map<string, RecordingSession> = new Map();
  private mediaManager: MediaManager;
  private ffmpegPath: string;
  private outputDir: string;

  constructor(mediaManager: MediaManager, ffmpegPath: string = 'ffmpeg', outputDir: string = './recordings') {
    this.mediaManager = mediaManager;
    this.ffmpegPath = ffmpegPath;
    this.outputDir = outputDir;
  }

  async startRecording(room: Room, options: RecordingOptions): Promise<RecordingSession> {
    const recordingId = `rec_${room.id}_${Date.now()}`;
    const outputPath = options.outputPath || `${this.outputDir}/${recordingId}.${options.format}`;

    mkdirSync(dirname(outputPath), { recursive: true });
    mkdirSync(this.outputDir, { recursive: true });
    try {
      if (typeof statfsSync === 'function') {
        const stat = statfsSync(dirname(outputPath));
        const freeBytes = Number(stat.bfree) * Number(stat.bsize);
        if (freeBytes < 100 * 1024 * 1024) {
          throw new Error('Insufficient disk space for recording');
        }
      }
    } catch (error) {
      if (error instanceof Error && error.message === 'Insufficient disk space for recording') {
        throw error;
      }
    }

    const ffmpegArgs = this.buildFfmpegArgs(room, options, outputPath);

    console.log(`Starting recording ${recordingId} for room ${room.id}`);
    console.log(`FFmpeg command: ${this.ffmpegPath} ${ffmpegArgs.join(' ')}`);

    const process = spawn(this.ffmpegPath, ffmpegArgs, {
      stdio: ['pipe', 'pipe', 'pipe'],
      detached: false,
    });
    let startReject: ((err: Error) => void) | undefined;
    let startTimeout: NodeJS.Timeout | undefined;

    const session: RecordingSession = {
      id: recordingId,
      roomId: room.id,
      options: { ...options, outputPath },
      process,
      startedAt: Date.now(),
      status: 'starting',
    };

    this.recordings.set(recordingId, session);
    room.recording = session;

    process.stdout?.on('data', (data) => {
      console.debug(`[Recording ${recordingId}] stdout: ${data}`);
    });

    process.stderr?.on('data', (data) => {
      console.debug(`[Recording ${recordingId}] stderr: ${data}`);
    });

    process.on('close', (code) => {
      console.log(`Recording ${recordingId} ended with code ${code}`);
      session.status = code === 0 ? 'stopped' : 'error';
      this.recordings.delete(recordingId);
      if (room.recording?.id === recordingId) {
        room.recording = undefined;
      }
    });

    process.on('error', (error) => {
      console.error(`Recording ${recordingId} error:`, error);
      session.status = 'error';
      if (startTimeout) clearTimeout(startTimeout);
      startTimeout = undefined;
      startReject?.(error);
      startReject = undefined;
    });

    await new Promise<void>((resolve, reject) => {
      startReject = reject;
      startTimeout = setTimeout(() => {
        startReject = undefined;
        startTimeout = undefined;
        reject(new Error('Recording start timeout'));
      }, 10000);

      const onData = (data: Buffer) => {
        const output = data.toString();
        if (output.includes('Output #0') || output.includes('Press [q] to stop')) {
          if (startTimeout) clearTimeout(startTimeout);
          startTimeout = undefined;
          startReject = undefined;
          process.stderr?.off('data', onData);
          session.status = 'active';
          resolve();
        }
      };
      process.stderr?.on('data', onData);
    });

    return session;
  }

  async stopRecording(recordingId: string): Promise<boolean> {
    const session = this.recordings.get(recordingId);
    if (!session) return false;

    console.log(`Stopping recording ${recordingId}`);
    session.status = 'stopping';

    if (session.process && !session.process.killed) {
      session.process.stdin?.write('q');
      await new Promise<void>((resolve) => {
        const proc: ChildProcess = session.process;
        const onClose = () => {
          if (termTimer) clearTimeout(termTimer);
          if (killTimer) clearTimeout(killTimer);
          proc.off('close', onClose);
          resolve();
        };
        proc.on('close', onClose);
        const termTimer = setTimeout(() => {
          if (!proc.killed) {
            proc.kill('SIGTERM');
          }
        }, 5000);
        const killTimer = setTimeout(() => {
          if (!proc.killed) {
            proc.kill('SIGKILL');
          }
          proc.off('close', onClose);
          resolve();
        }, 10000);
      });
    }

    return true;
  }

  getRecording(recordingId: string): RecordingSession | undefined {
    return this.recordings.get(recordingId);
  }

  getRoomRecording(roomId: string): RecordingSession | undefined {
    for (const session of this.recordings.values()) {
      if (session.roomId === roomId) return session;
    }
    return undefined;
  }

  getAllRecordings(): RecordingSession[] {
    return Array.from(this.recordings.values());
  }

  private buildFfmpegArgs(room: Room, options: RecordingOptions, outputPath: string): string[] {
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

    const layoutFilter = this.buildLayoutFilter(room, options);
    args.push('-filter_complex', layoutFilter, '-map', '[out]');
    if (options.includeAudio) {
      args.push('-map', '1:a');
    }

    args.push(
      '-c:v', options.format === 'webm' ? 'libvpx-vp9' : 'libx264',
      '-b:v', `${options.videoBitrate}k`,
      '-preset', 'veryfast',
      '-tune', 'zerolatency',
      '-g', String(options.framerate * 2),
    );

    if (options.includeAudio) {
      args.push('-c:a', 'libopus', '-b:a', `${options.audioBitrate}k`);
    }

    if (options.format === 'mp4') {
      args.push('-movflags', '+faststart');
    }
    args.push('-shortest');

    args.push(
      '-f', options.format,
      outputPath,
    );

    return args;
  }

  buildLayoutFilter(room: Room, options: RecordingOptions): string {
    const peers = Array.from(room.peers.values()).filter(p => p.producers.size > 0);
    const videoProducers = peers.flatMap(p =>
      Array.from(p.producers.values()).filter(pr => pr.kind === 'video')
    );

    switch (options.layout) {
      case 'grid':
        return this.buildGridLayout(videoProducers.length, options.width, options.height);
      case 'speaker':
        return this.buildSpeakerLayout(videoProducers.length, options.width, options.height);
      case 'pip':
        return this.buildPipLayout(videoProducers.length, options.width, options.height);
      default:
        return this.buildGridLayout(videoProducers.length, options.width, options.height);
    }
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

  private buildSpeakerLayout(count: number, width: number, height: number): string {
    if (count === 0) return `color=black:${width}x${height}[out]`;
    if (count === 1) return `[0:v]scale=w=${width}:h=${height}[out]`;

    const mainW = Math.floor(width * 0.75);
    const mainH = Math.floor(height * 0.75);
    const thumbW = Math.floor(width * 0.2);
    const thumbH = Math.floor(height * 0.2);

    let filter = `[0:v]scale=${mainW}:${mainH}[main];`;
    for (let i = 1; i < count; i++) {
      filter += `[${i}:v]scale=${thumbW}:${thumbH}[thumb${i}];`;
    }

    const thumbRefs = Array.from({ length: count - 1 }, (_, i) => `[thumb${i + 1}]`).join('');
    filter += `[main]${thumbRefs}xstack=inputs=${count}:layout=0_0|${mainW}_0|${mainW}_${thumbH}[out]`;

    return filter;
  }

  private buildPipLayout(count: number, width: number, height: number): string {
    if (count === 0) return `color=black:${width}x${height}[out]`;
    if (count === 1) return `[0:v]scale=w=${width}:h=${height}[out]`;

    const mainW = width;
    const mainH = height;
    const pipW = Math.floor(width * 0.25);
    const pipH = Math.floor(height * 0.25);

    let filter = `[0:v]scale=${mainW}:${mainH}[main];`;
    for (let i = 1; i < count; i++) {
      filter += `[${i}:v]scale=${pipW}:${pipH}[pip${i}];`;
    }

    const pipRefs = Array.from({ length: count - 1 }, (_, i) => `[pip${i + 1}]`).join('');
    filter += `[main]${pipRefs}overlay=x=W-w-${10}:y=H-h-${10}:repeatlast=0[out]`;

    return filter;
  }
}