import { spawn, ChildProcess } from 'child_process';
import { Room, LiveStreamOptions, LiveStreamSession, RtmpOutput } from './types.js';
import { EventEmitter } from 'events';

export class LiveStreamManager extends EventEmitter {
  private streams: Map<string, LiveStreamSession> = new Map();
  private defaultRtmpUrl: string;
  private ffmpegPath: string;

  constructor(defaultRtmpUrl: string = '', ffmpegPath: string = 'ffmpeg') {
    super();
    this.defaultRtmpUrl = defaultRtmpUrl;
    // Unified on config.recording.ffmpegPath (default 'ffmpeg').
    this.ffmpegPath = ffmpegPath;
  }

  async startLiveStream(room: Room, options: LiveStreamOptions): Promise<LiveStreamSession> {
    this.validateOptions(options);
    if (room.liveStream || this.getRoomStream(room.id)) {
      throw new Error('Live stream already in progress');
    }
    const streamId = `stream_${room.id}_${Date.now()}`;
    
    // Support multiple output targets (RTMP + SRT)
    const outputs: Array<{url: string, args: string[]}> = [];
    
    // Primary RTMP output
    if (options.rtmpUrl || this.defaultRtmpUrl) {
      const rtmpUrl = options.rtmpUrl || this.defaultRtmpUrl;
      const fullUrl = `${rtmpUrl}/${options.streamKey}`;
      outputs.push({ url: fullUrl, args: this.buildRtmpArgs(options, fullUrl) });
    }
    
    // Additional RTMP outputs (YouTube, Twitch, Custom)
    if (options.rtmpOutputs && options.rtmpOutputs.length > 0) {
      for (const output of options.rtmpOutputs) {
        outputs.push({ url: output.url, args: this.buildRtmpArgs(options, output.url) });
      }
    }
    
    // SRT output
    if (options.srtUrl) {
      outputs.push({ url: options.srtUrl, args: this.buildSrtArgs(options, options.srtUrl) });
    }
    
    if (outputs.length === 0) {
      throw new Error('At least one output URL (RTMP or SRT) is required');
    }

    // For multiple outputs, we use tee muxer
    const ffmpegArgs = outputs.length === 1 
      ? outputs[0].args 
      : this.buildTeeArgs(options, outputs);

    console.log(`Starting live stream ${streamId} for room ${room.id} to ${outputs.length} output(s)`);
    console.log(`FFmpeg command: ${this.ffmpegPath} ${this.redactForLog(ffmpegArgs, options)}`);

    const process = spawn(this.ffmpegPath, ffmpegArgs, {
      stdio: ['pipe', 'pipe', 'pipe'],
      detached: false,
    });

    const session: LiveStreamSession = {
      id: streamId,
      roomId: room.id,
      options: { ...options, rtmpUrl: outputs[0].url },
      process,
      startedAt: Date.now(),
      status: 'starting',
    };

    this.streams.set(streamId, session);
    room.liveStream = session;

    let startReject: ((err: Error) => void) | undefined;
    let startTimeout: NodeJS.Timeout | undefined;

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
        reject(new Error('Live stream start timeout'));
      }, 15000);

      const onData = (data: Buffer) => {
        const output = data.toString();
        if (output.includes('Output #0') || output.includes('Press [q] to stop')) {
          if (startTimeout) clearTimeout(startTimeout);
          startTimeout = undefined;
          startReject = undefined;
          process.stderr?.off('data', onData);
          session.status = 'active';
          this.emit('liveStreamStarted', { streamId: session.id, roomId: room.id, rtmpUrl: options.rtmpUrl });
          resolve();
        }
      };
      process.stderr?.on('data', onData);
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

    this.emit('liveStreamStopped', { streamId, roomId: session.roomId });
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

  private validateOptions(options: LiveStreamOptions): void {
    if (options.format !== 'flv' && options.format !== 'mp4') {
      throw new Error('Invalid livestream options');
    }
    if (options.layout !== 'grid' && options.layout !== 'speaker' && options.layout !== 'pip' && options.layout !== 'custom') {
      throw new Error('Invalid livestream options');
    }
    if (!Number.isInteger(options.width) || options.width < 160 || options.width > 3840) {
      throw new Error('Invalid livestream options');
    }
    if (!Number.isInteger(options.height) || options.height < 120 || options.height > 2160) {
      throw new Error('Invalid livestream options');
    }
    if (!Number.isInteger(options.framerate) || options.framerate < 1 || options.framerate > 60) {
      throw new Error('Invalid livestream options');
    }
    if (!Number.isInteger(options.videoBitrate) || options.videoBitrate < 100 || options.videoBitrate > 20000) {
      throw new Error('Invalid livestream options');
    }
    if (!Number.isInteger(options.audioBitrate) || options.audioBitrate < 32 || options.audioBitrate > 512) {
      throw new Error('Invalid livestream options');
    }
    if (options.layout === 'custom') {
      const custom = (options as { customLayout?: { regions?: unknown[] } }).customLayout;
      if (!custom || !Array.isArray(custom.regions) || custom.regions.length === 0) {
        throw new Error('Invalid livestream options');
      }
    }
    // مفتاح البث يُحقن في سطر أوامر ffmpeg — يُمنع أي بياض/تحكم لمنع حقن الوسائط.
    if (options.streamKey !== undefined && options.streamKey !== '') {
      if (typeof options.streamKey !== 'string' || options.streamKey.length > 256 || /[\s"'`$\\;|&<>]/.test(options.streamKey)) {
        throw new Error('Invalid livestream options');
      }
    }
    if (options.rtmpUrl && !this.isAllowedRtmpUrl(options.rtmpUrl)) {
      throw new Error('Invalid livestream options');
    }
    if (this.defaultRtmpUrl && !this.isAllowedRtmpUrl(this.defaultRtmpUrl)) {
      throw new Error('Invalid livestream options');
    }
    for (const out of options.rtmpOutputs ?? []) {
      if (!out || !this.isAllowedRtmpUrl(out.url)) {
        throw new Error('Invalid livestream options');
      }
      if (out.streamKey && /[\s"'`$\\;|&<>]/.test(out.streamKey)) {
        throw new Error('Invalid livestream options');
      }
    }
    if (options.srtUrl && !options.srtUrl.startsWith('srt://')) {
      throw new Error('Invalid livestream options');
    }
  }

  private isAllowedRtmpUrl(url: string): boolean {
    return url.startsWith('rtmp://') || url.startsWith('rtmps://');
  }

  private buildRtmpArgs(options: LiveStreamOptions, rtmpUrl: string): string[] {
    const args: string[] = [
      '-y',
      '-re',
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
      '-maxrate', `${options.videoBitrate}k`,
      '-bufsize', `${options.videoBitrate * 2}k`,
      '-preset', 'veryfast',
      '-tune', 'zerolatency',
      '-g', String(options.framerate * 2),
      '-f', 'flv',
    );

    if (options.includeAudio) {
      args.push('-c:a', 'aac', '-b:a', `${options.audioBitrate}k`, '-ar', '44100');
    }

    args.push(
      '-reconnect', '1',
      '-reconnect_streamed', '1',
      '-reconnect_delay_max', '5',
      rtmpUrl,
    );

    return args;
  }

  private buildSrtArgs(options: LiveStreamOptions, srtUrl: string): string[] {
    const args: string[] = [
      '-y',
      '-re',
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
      '-maxrate', `${options.videoBitrate}k`,
      '-bufsize', `${options.videoBitrate * 2}k`,
      '-preset', 'veryfast',
      '-tune', 'zerolatency',
      '-g', String(options.framerate * 2),
      '-f', 'mpegts',
    );

    if (options.includeAudio) {
      args.push('-c:a', 'aac', '-b:a', `${options.audioBitrate}k`, '-ar', '44100');
    }

    // SRT options for low latency
    const srtOptions = `srt://${srtUrl}?mode=caller&latency=120&peerlatency=120&pbkeylen=0`;
    args.push(srtOptions);

    return args;
  }

  private buildTeeArgs(options: LiveStreamOptions, outputs: Array<{url: string, args: string[]}>): string[] {
    const args: string[] = [
      '-y',
      '-re',
      '-f', 'rawvideo',
      '-pix_fmt', 'yuv420p',
      '-s', `${options.width}x${options.height}`,
      '-r', String(options.framerate),
      '-i', '-',
    ];

    if (options.includeAudio) {
      args.push('-f', 's16le', '-ar', '48000', '-ac', '2', '-i', '-');
    }

    // Build filter complex for tee muxer
    let filterComplex = '';
    const outputSelectors: string[] = [];
    
    outputs.forEach((output, index) => {
      const selector = `[out${index}]`;
      outputSelectors.push(selector);
      
      if (output.url.startsWith('srt://')) {
        filterComplex += `${selector} -c:v libx264 -b:v ${options.videoBitrate}k -preset veryfast -tune zerolatency -g ${options.framerate * 2} -f mpegts `;
        if (options.includeAudio) {
          filterComplex += `-c:a aac -b:a ${options.audioBitrate}k -ar 44100 `;
        }
        filterComplex += `${output.url}|`;
      } else {
        filterComplex += `${selector} -c:v libx264 -b:v ${options.videoBitrate}k -preset veryfast -tune zerolatency -g ${options.framerate * 2} -f flv `;
        if (options.includeAudio) {
          filterComplex += `-c:a aac -b:a ${options.audioBitrate}k -ar 44100 `;
        }
        filterComplex += `${output.url}|`;
      }
    });
    
    // Remove trailing |
    filterComplex = filterComplex.slice(0, -1);

    args.push(
      '-filter_complex', filterComplex,
      '-map', '0:v',
    );
    
    if (options.includeAudio) {
      args.push('-map', '1:a');
    }

    return args;
  }

  buildLayoutFilter(options: LiveStreamOptions): string {
    // Similar to recording layouts but optimized for streaming
    return this.buildGridLayout(9, options.width, options.height);
  }

  private redactForLog(args: string[], options: LiveStreamOptions): string {
    const secrets = [
      options.streamKey,
      ...(options.rtmpOutputs ?? []).map((o) => o.streamKey).filter((k): k is string => Boolean(k)),
    ].filter(Boolean);
    let cmd = args.join(' ');
    for (const secret of secrets) {
      cmd = cmd.split(secret).join('[REDACTED]');
    }
    return cmd;
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