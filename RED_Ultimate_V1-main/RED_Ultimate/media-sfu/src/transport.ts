import { EventEmitter } from 'events';
import { Router, WebRtcTransport, PipeTransport, IceParameters, DtlsParameters, IceCandidate, SctpParameters } from 'mediasoup';
import { Config } from './config.js';

export interface TransportOptions {
  id: string;
  iceParameters: IceParameters;
  iceCandidates: IceCandidate[];
  dtlsParameters: DtlsParameters;
  sctpParameters: SctpParameters | undefined;
}

export class TransportManager extends EventEmitter {
  private config: Config;

  constructor(config: Config) {
    super();
    this.config = config;
  }

  async createWebRtcTransport(router: Router, direction: 'send' | 'recv'): Promise<WebRtcTransport> {
    if (direction !== 'send' && direction !== 'recv') {
      throw new Error('Invalid message format');
    }
    const listenInfo = { protocol: 'udp' as const, ip: '0.0.0.0', announcedAddress: this.config.announcedIp || undefined };
    const listenInfoTcp = { protocol: 'tcp' as const, ip: '0.0.0.0', announcedAddress: this.config.announcedIp || undefined };

    const transport = await router.createWebRtcTransport({
      listenInfos: [listenInfo, listenInfoTcp],
      enableUdp: true,
      enableTcp: true,
      preferUdp: true,
      initialAvailableOutgoingBitrate: 1_000_000,
      minimumAvailableOutgoingBitrate: 100_000,
      maxSctpMessageSize: 262144,
      enableSctp: true,
      appData: { direction },
    });

    transport.on('dtlsstatechange', (state) => {
      if (state === 'failed') {
        console.warn(`DTLS state failed on transport ${transport.id}`);
      }
      if (state === 'closed' && !transport.closed) {
        try {
          transport.close();
        } catch {
          // Already closed — ignore.
        }
      }
    });

    transport.on('iceselectedtuplechange', (tuple) => {
      console.debug(`ICE tuple selected for transport ${transport.id}: ${JSON.stringify(tuple)}`);
    });

    transport.on('bwe', (bwe) => {
      if (this.config.logLevel === 'debug') {
        console.debug(`[BWE] Transport ${transport.id}: available outgoing bitrate ${bwe.availableOutgoingBitrate} bps`);
      }
    });

    return transport;
  }

  async createPipeTransport(router: Router): Promise<PipeTransport> {
    const transport = await router.createPipeTransport({
      listenInfo: { protocol: 'udp', ip: '127.0.0.1' },
      enableSctp: false,
      enableRtx: true,
      enableSrtp: false,
    });

    return transport;
  }

  getTransportOptions(transport: WebRtcTransport | PipeTransport): TransportOptions {
    return {
      id: transport.id,
      iceParameters: transport.iceParameters,
      iceCandidates: transport.iceCandidates,
      dtlsParameters: transport.dtlsParameters,
      sctpParameters: transport.sctpParameters,
    };
  }

  async connectTransport(transport: WebRtcTransport, dtlsParameters: DtlsParameters): Promise<void> {
    await transport.connect({ dtlsParameters });
  }

  async restartIce(transport: WebRtcTransport): Promise<IceParameters> {
    const iceParameters = await transport.restartIce();
    // Consumers must forward this to the client as an 'iceRestartNeeded' event.
    this.emit('iceRestartNeeded', { transportId: transport.id, iceParameters });
    return iceParameters;
  }

  closeTransport(transport: WebRtcTransport | PipeTransport): void {
    try {
      transport.removeAllListeners();
    } catch {
      // Ignore listener-removal failures.
    }
    if (transport.closed) return;
    try {
      transport.close();
    } catch {
      // Already closed — ignore.
    }
  }
}