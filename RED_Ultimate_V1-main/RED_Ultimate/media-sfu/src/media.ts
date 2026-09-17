import { Router, WebRtcTransport, Producer, Consumer, RtpParameters, RtpCapabilities, ProducerOptions, ConsumerOptions } from 'mediasoup';
import { Peer, Room, ProducerAppData } from './types.js';
import { EventEmitter } from 'events';

export interface ProduceResult {
  producer: Producer;
  producerId: string;
}

export interface ConsumeResult {
  consumer: Consumer;
  consumerId: string;
  rtpParameters: RtpParameters;
}

export class MediaManager extends EventEmitter {
  private readonly maxProducersPerKind: number;

  constructor(maxProducersPerKind: number = 4) {
    super();
    // Mirrors config.maxProducersPerKind (default 4).
    this.maxProducersPerKind = maxProducersPerKind;
  }

  async produce(
    transport: WebRtcTransport,
    options: ProducerOptions,
    peer: Peer,
    room: Room
  ): Promise<ProduceResult> {
    if (options.kind !== 'audio' && options.kind !== 'video') {
      throw new Error('Invalid kind: expected audio|video');
    }
    const codecs = (options.rtpParameters as RtpParameters | undefined)?.codecs;
    if (!options.rtpParameters || !Array.isArray(codecs) || codecs.length === 0) {
      throw new Error('Invalid rtpParameters: missing codecs');
    }
    if (options.kind === 'video' && options.encodings && options.encodings.length > 3) {
      throw new Error('Max 3 encodings for video');
    }

    const kindCount = Array.from(peer.producers.values())
      .filter(p => p.kind === options.kind).length;

    if (kindCount >= this.maxProducersPerKind) {
      throw new Error(`Max ${this.maxProducersPerKind} producers per kind`);
    }

    const producer = await transport.produce({
      kind: options.kind,
      rtpParameters: options.rtpParameters,
      encodings: options.encodings,
      appData: {
        ...options.appData,
        peerId: peer.id,
        redId: peer.redId,
        kind: options.kind,
        simulcast: options.simulcast ?? false,
        displayName: options.appData?.displayName,
      } as ProducerAppData,
    });

    peer.producers.set(producer.id, producer);

    producer.on('transportclose', () => {
      peer.producers.delete(producer.id);
    });

    producer.on('producerclose', () => {
      peer.producers.delete(producer.id);
      this.notifyProducerClosed(room, peer.id, producer.id);
    });

    if (producer.kind === 'audio' && room.audioLevelObserver) {
      try {
        await room.audioLevelObserver.addProducer({ producerId: producer.id });
      } catch (error) {
        console.debug(`Audio level observer addProducer failed: ${error}`);
      }
    }

    this.emit('newProducer', { peerId: peer.id, producerId: producer.id, kind: producer.kind, appData: producer.appData });
    this.broadcastNewProducer(room, peer.id, producer);

    return { producer, producerId: producer.id };
  }

  async consume(
    transport: WebRtcTransport,
    producerId: string,
    rtpCapabilities: RtpCapabilities,
    peer: Peer,
    room: Room,
    options: Partial<ConsumerOptions> = {}
  ): Promise<ConsumeResult> {
    const producer = this.findProducer(room, producerId);
    if (!producer) {
      throw new Error('Producer not found');
    }

    if (!room.router.canConsume({ producerId, rtpCapabilities })) {
      throw new Error('Cannot consume producer');
    }

    const consumer = await transport.consume({
      producerId,
      rtpCapabilities,
      paused: options.paused ?? true,
      preferredLayers: options.preferredLayers,
      enableRtx: options.enableRtx ?? true,
      pipe: options.pipe ?? false,
    });

    peer.consumers.set(consumer.id, consumer);

    consumer.on('transportclose', () => {
      peer.consumers.delete(consumer.id);
    });

    consumer.on('producerclose', () => {
      peer.consumers.delete(consumer.id);
      this.notifyProducerClosed(room, peer.id, consumer.id, producerId);
    });

    consumer.on('score', (score) => {
      this.emit('consumerScore', { consumerId: consumer.id, score: score.score, producerScore: score.producerScore });
      if (score.score < 5 && score.producerScore >= 7) {
        this.broadcastNetworkDegraded(room, peer.id, consumer.id, score.score);
      }
    });

    consumer.on('layerschange', (layers) => {
      console.debug(`[Simulcast] Consumer ${consumer.id} spatial layer: ${layers?.spatialLayer ?? 'none'}`);
      this.emit('consumerLayersChanged', { consumerId: consumer.id, spatialLayer: layers?.spatialLayer ?? null, temporalLayer: layers?.temporalLayer ?? null });
      this.broadcastLayersChanged(room, peer.id, consumer.id, layers);
    });

    if (!options.paused) {
      await consumer.resume();
    }

    return {
      consumer,
      consumerId: consumer.id,
      rtpParameters: consumer.rtpParameters,
    };
  }

  async pauseProducer(peer: Peer, producerId: string, room: Room): Promise<void> {
    const producer = peer.producers.get(producerId);
    if (!producer) throw new Error('Producer not found');

    await producer.pause();
    this.emit('producerPaused', { peerId: peer.id, producerId });
    this.broadcastProducerPaused(room, peer.id, producerId);
  }

  async resumeProducer(peer: Peer, producerId: string, room: Room): Promise<void> {
    const producer = peer.producers.get(producerId);
    if (!producer) throw new Error('Producer not found');

    await producer.resume();
    this.emit('producerResumed', { peerId: peer.id, producerId });
    this.broadcastProducerResumed(room, peer.id, producerId);
  }

  async pauseConsumer(peer: Peer, consumerId: string): Promise<void> {
    const consumer = peer.consumers.get(consumerId);
    if (!consumer) throw new Error('Consumer not found');

    await consumer.pause();
  }

  async resumeConsumer(peer: Peer, consumerId: string): Promise<void> {
    const consumer = peer.consumers.get(consumerId);
    if (!consumer) throw new Error('Consumer not found');

    await consumer.resume();
  }

  async setConsumerPreferredLayers(
    peer: Peer,
    consumerId: string,
    spatialLayer: number,
    temporalLayer: number
  ): Promise<void> {
    const consumer = peer.consumers.get(consumerId);
    if (!consumer) throw new Error('Consumer not found');

    await consumer.setPreferredLayers({ spatialLayer, temporalLayer });
  }

  async requestKeyFrame(peer: Peer, consumerId: string): Promise<void> {
    const consumer = peer.consumers.get(consumerId);
    if (!consumer) throw new Error('Consumer not found');

    await consumer.requestKeyFrame();
  }

  async getProducerStats(producer: Producer): Promise<any> {
    return producer.getStats();
  }

  async getConsumerStats(consumer: Consumer): Promise<any> {
    return consumer.getStats();
  }

  async getTransportStats(transport: WebRtcTransport): Promise<any> {
    return transport.getStats();
  }

  private findProducer(room: Room, producerId: string): Producer | undefined {
    for (const peer of room.peers.values()) {
      const producer = peer.producers.get(producerId);
      if (producer) return producer;
    }
    return undefined;
  }

  private broadcastNewProducer(room: Room, peerId: string, producer: Producer): void {
    const appData = producer.appData as ProducerAppData;
    for (const [id, peer] of room.peers) {
      if (id !== peerId && peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'newProducer',
          peerId,
          producerId: producer.id,
          kind: producer.kind,
          appData: {
            displayName: appData.displayName,
            simulcast: appData.simulcast,
          },
        }));
      }
    }
  }

  private notifyProducerClosed(room: Room, peerId: string, consumerId: string, producerId?: string): void {
    this.emit('producerClosed', { peerId, consumerId, producerId });
    for (const [id, peer] of room.peers) {
      if (peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'producerClosed',
          peerId,
          consumerId,
          producerId,
        }));
      }
    }
  }

  private broadcastProducerPaused(room: Room, peerId: string, producerId: string): void {
    for (const [id, peer] of room.peers) {
      if (id !== peerId && peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'producerPaused',
          peerId,
          producerId,
        }));
      }
    }
  }

  private broadcastProducerResumed(room: Room, peerId: string, producerId: string): void {
    for (const [id, peer] of room.peers) {
      if (id !== peerId && peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'producerResumed',
          peerId,
          producerId,
        }));
      }
    }
  }

  private broadcastNetworkDegraded(room: Room, peerId: string, consumerId: string, score: number): void {
    for (const [id, peer] of room.peers) {
      if (peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'networkDegraded',
          peerId,
          consumerId,
          score,
        }));
      }
    }
  }

  private broadcastLayersChanged(room: Room, peerId: string, consumerId: string, layers: any): void {
    for (const [id, peer] of room.peers) {
      if (peer.ws.readyState === 1) {
        peer.ws.send(JSON.stringify({
          type: 'consumerLayersChanged',
          consumerId,
          spatialLayer: layers?.spatialLayer ?? null,
          temporalLayer: layers?.temporalLayer ?? null,
        }));
      }
    }
  }

  cleanupPeer(peer: Peer): void {
    for (const consumer of peer.consumers.values()) consumer.close();
    for (const producer of peer.producers.values()) producer.close();
    for (const transport of peer.transports.values()) transport.close();
    peer.producers.clear();
    peer.consumers.clear();
    peer.transports.clear();
  }
}