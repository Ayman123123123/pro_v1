import { createWorker, Worker, WorkerSettings } from 'mediasoup';
import { EventEmitter } from 'events';
import { Config } from './config.js';
import { WorkerInfo } from './types.js';

export class WorkerManager extends EventEmitter {
  private workers: Worker[] = [];
  private config: Config;
  private nextWorkerIndex = 0;
  private workerStats: Map<number, WorkerInfo> = new Map();

  constructor(config: Config) {
    super();
    this.config = config;
  }

  async initialize(): Promise<void> {
    for (let i = 0; i < this.config.workerCount; i++) {
      await this.createWorker();
    }
    this.startStatsCollection();
  }

  private async createWorker(): Promise<Worker> {
    const settings: WorkerSettings = {
      logLevel: this.config.logLevel,
      rtcMinPort: this.config.rtcMinPort,
      rtcMaxPort: this.config.rtcMaxPort,
      logTags: this.config.logLevel === 'debug' ? ['dtls', 'rtp', 'sctp'] : [],
      dtlsCertificateFile: process.env.DTLS_CERT_FILE,
      dtlsPrivateKeyFile: process.env.DTLS_KEY_FILE,
    };

    const worker = await createWorker(settings);

    worker.on('died', () => {
      this.handleWorkerDied(worker);
    });

    worker.on('close', () => {
      this.workers = this.workers.filter(w => w !== worker);
      this.workerStats.delete(worker.pid);
    });

    this.workers.push(worker);
    this.workerStats.set(worker.pid, {
      pid: worker.pid,
      roomCount: 0,
      peerCount: 0,
      cpuUsage: 0,
      memoryUsage: 0,
    });

    console.log(`Created mediasoup worker ${worker.pid} (total: ${this.workers.length})`);
    this.emit('workerCreated', worker);

    return worker;
  }

  private async handleWorkerDied(worker: Worker): Promise<void> {
    console.error(`Mediasoup worker ${worker.pid} died — restarting`);

    const idx = this.workers.indexOf(worker);
    if (idx !== -1) {
      this.workers.splice(idx, 1);
    }

    this.workerStats.delete(worker.pid);

    try {
      await this.createWorker();
    } catch (error) {
      console.error('Failed to replace worker:', error);
      this.emit('workerReplaceFailed', error);
    }
  }

  getNextWorker(): Worker {
    if (this.workers.length === 0) {
      throw new Error('No workers available');
    }
    const worker = this.workers[this.nextWorkerIndex % this.workers.length];
    this.nextWorkerIndex++;
    return worker;
  }

  getWorkerByPid(pid: number): Worker | undefined {
    return this.workers.find(w => w.pid === pid);
  }

  getAllWorkers(): Worker[] {
    return [...this.workers];
  }

  getWorkerCount(): number {
    return this.workers.length;
  }

  updateWorkerStats(pid: number, stats: Partial<WorkerInfo>): void {
    const existing = this.workerStats.get(pid);
    if (existing) {
      this.workerStats.set(pid, { ...existing, ...stats });
    }
  }

  getWorkerStats(): WorkerInfo[] {
    return Array.from(this.workerStats.values());
  }

  private startStatsCollection(): void {
    setInterval(async () => {
      for (const worker of this.workers) {
        try {
          const usage = await worker.getResourceUsage();
          this.updateWorkerStats(worker.pid, {
            cpuUsage: Math.round(usage.ru_utime + usage.ru_stime),
            memoryUsage: usage.ru_maxrss * 1024,
          });
        } catch (error) {
          console.debug(`Failed to get resource usage for worker ${worker.pid}:`, error);
        }
      }
      this.emit('statsUpdated', this.getWorkerStats());
    }, 10000);
  }

  async closeAll(): Promise<void> {
    console.log('Closing all workers...');
    await Promise.all(this.workers.map(w => w.close()));
    this.workers = [];
    this.workerStats.clear();
  }

  getWorkerLoad(): Map<number, number> {
    const load = new Map<number, number>();
    for (const worker of this.workers) {
      const stats = this.workerStats.get(worker.pid);
      load.set(worker.pid, stats?.peerCount || 0);
    }
    return load;
  }

  getLeastLoadedWorker(): Worker {
    let leastLoaded = this.workers[0];
    let minPeers = Infinity;

    for (const worker of this.workers) {
      const stats = this.workerStats.get(worker.pid);
      const peerCount = stats?.peerCount || 0;
      if (peerCount < minPeers) {
        minPeers = peerCount;
        leastLoaded = worker;
      }
    }

    return leastLoaded;
  }
}