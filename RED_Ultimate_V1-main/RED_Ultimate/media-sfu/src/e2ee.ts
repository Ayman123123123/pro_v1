import { createHash, randomBytes } from 'crypto';
import { EventEmitter } from 'events';

export interface E2EEKeyPackage {
  identityKey: Uint8Array;
  preKeys: PreKeyBundle;
  signedPreKey: SignedPreKey;
}

export interface PreKeyBundle {
  keyId: number;
  publicKey: Uint8Array;
  signature: Uint8Array;
}

export interface SignedPreKey {
  keyId: number;
  publicKey: Uint8Array;
  signature: Uint8Array;
  timestamp: number;
}

export interface RatchetState {
  rootKey: Uint8Array;
  sendingChain: ChainState;
  receivingChain: ChainState;
  senderRatchetKey: Uint8Array;
  receiverRatchetKey: Uint8Array;
}

export interface ChainState {
  chainKey: Uint8Array;
  messageNumber: number;
}

export interface EncryptedMessage {
  header: MessageHeader;
  ciphertext: Uint8Array;
  mac: Uint8Array;
}

export interface MessageHeader {
  version: number;
  senderIdentityKey: Uint8Array;
  senderRatchetKey: Uint8Array;
  messageNumber: number;
  previousChainLength: number;
}

export interface GroupSession {
  groupId: string;
  epoch: number;
  members: Map<string, MemberState>;
  senderRatchetKeys: Map<string, Uint8Array>;
}

export interface MemberState {
  identityKey: Uint8Array;
  introductionKey: Uint8Array;
  addedBy: string;
  addedAt: number;
}

export class E2EEManager extends EventEmitter {
  private identityKeys: Map<string, Uint8Array> = new Map();
  private ratchetStates: Map<string, RatchetState> = new Map();
  private groupSessions: Map<string, GroupSession> = new Map();
  private preKeys: Map<string, Map<number, PreKeyBundle>> = new Map();
  private signedPreKeys: Map<string, Map<number, SignedPreKey>> = new Map();

  generateIdentityKey(): Uint8Array {
    return randomBytes(32);
  }

  generateKeyPair(): { publicKey: Uint8Array; privateKey: Uint8Array } {
    const privateKey = randomBytes(32);
    const publicKey = this.derivePublicKey(privateKey);
    return { publicKey, privateKey };
  }

  private derivePublicKey(privateKey: Uint8Array): Uint8Array {
    return createHash('sha256').update(privateKey).digest();
  }

  async createKeyPackage(peerId: string): Promise<E2EEKeyPackage> {
    const identityKey = this.generateIdentityKey();
    this.identityKeys.set(peerId, identityKey);

    const { publicKey: preKeyPublic, privateKey: preKeyPrivate } = this.generateKeyPair();
    const preKeySignature = this.sign(identityKey, preKeyPublic);

    const preKeyBundle: PreKeyBundle = {
      keyId: 1,
      publicKey: preKeyPublic,
      signature: preKeySignature,
    };

    const { publicKey: signedPreKeyPublic, privateKey: signedPreKeyPrivate } = this.generateKeyPair();
    const signedPreKeySignature = this.sign(identityKey, signedPreKeyPublic);

    const signedPreKey: SignedPreKey = {
      keyId: 1,
      publicKey: signedPreKeyPublic,
      signature: signedPreKeySignature,
      timestamp: Date.now(),
    };

    if (!this.preKeys.has(peerId)) this.preKeys.set(peerId, new Map());
    this.preKeys.get(peerId)!.set(1, preKeyBundle);

    if (!this.signedPreKeys.has(peerId)) this.signedPreKeys.set(peerId, new Map());
    this.signedPreKeys.get(peerId)!.set(1, signedPreKey);

    return { identityKey, preKeys: preKeyBundle, signedPreKey };
  }

  async establishSession(
    peerId: string,
    remoteIdentityKey: Uint8Array,
    remotePreKey: PreKeyBundle,
    remoteSignedPreKey: SignedPreKey
  ): Promise<RatchetState> {
    const localIdentityKey = this.identityKeys.get(peerId);
    if (!localIdentityKey) throw new Error('Identity key not found');

    const sharedSecret = this.deriveSharedSecret(localIdentityKey, remotePreKey.publicKey);
    const rootKey = createHash('sha256').update(sharedSecret).digest();

    const { publicKey: senderRatchetKey, privateKey: senderRatchetPrivate } = this.generateKeyPair();
    const { publicKey: receiverRatchetKey } = this.generateKeyPair();

    const chainKey = createHash('sha256').update(rootKey).digest();

    const ratchetState: RatchetState = {
      rootKey,
      sendingChain: { chainKey, messageNumber: 0 },
      receivingChain: { chainKey: createHash('sha256').update(chainKey).digest(), messageNumber: 0 },
      senderRatchetKey,
      receiverRatchetKey,
    };

    this.ratchetStates.set(`${peerId}:${Buffer.from(remoteIdentityKey).toString('hex')}`, ratchetState);

    return ratchetState;
  }

  async encryptMessage(
    peerId: string,
    remoteIdentityKey: Uint8Array,
    plaintext: Uint8Array
  ): Promise<EncryptedMessage> {
    const key = `${peerId}:${Buffer.from(remoteIdentityKey).toString('hex')}`;
    let state = this.ratchetStates.get(key);

    if (!state) {
      throw new Error('Session not established');
    }

    const messageKey = this.deriveMessageKey(state.sendingChain.chainKey);
    state.sendingChain.chainKey = this.deriveNextChainKey(state.sendingChain.chainKey);
    state.sendingChain.messageNumber++;

    const header: MessageHeader = {
      version: 1,
      senderIdentityKey: this.identityKeys.get(peerId)!,
      senderRatchetKey: state.senderRatchetKey,
      messageNumber: state.sendingChain.messageNumber,
      previousChainLength: 0,
    };

    const ciphertext = this.aesGcmEncrypt(messageKey, plaintext, this.serializeHeader(header));
    const mac = this.computeMac(messageKey, ciphertext);

    return { header, ciphertext, mac };
  }

  async decryptMessage(
    peerId: string,
    remoteIdentityKey: Uint8Array,
    encryptedMessage: EncryptedMessage
  ): Promise<Uint8Array> {
    const key = `${peerId}:${Buffer.from(remoteIdentityKey).toString('hex')}`;
    let state = this.ratchetStates.get(key);

    if (!state) {
      throw new Error('Session not established');
    }

    const expectedMac = this.computeMac(
      this.deriveMessageKey(state.receivingChain.chainKey),
      encryptedMessage.ciphertext
    );

    if (!this.timingSafeEqual(expectedMac, encryptedMessage.mac)) {
      throw new Error('MAC verification failed');
    }

    const messageKey = this.deriveMessageKey(state.receivingChain.chainKey);
    state.receivingChain.chainKey = this.deriveNextChainKey(state.receivingChain.chainKey);
    state.receivingChain.messageNumber++;

    return this.aesGcmDecrypt(messageKey, encryptedMessage.ciphertext, this.serializeHeader(encryptedMessage.header));
  }

  async createGroupSession(groupId: string, creatorId: string): Promise<GroupSession> {
    const creatorIdentityKey = this.identityKeys.get(creatorId);
    if (!creatorIdentityKey) throw new Error('Creator identity key not found');

    const session: GroupSession = {
      groupId,
      epoch: 0,
      members: new Map(),
      senderRatchetKeys: new Map(),
    };

    const { publicKey: introductionKey } = this.generateKeyPair();
    session.members.set(creatorId, {
      identityKey: creatorIdentityKey,
      introductionKey,
      addedBy: creatorId,
      addedAt: Date.now(),
    });
    session.senderRatchetKeys.set(creatorId, introductionKey);

    this.groupSessions.set(groupId, session);
    return session;
  }

  async addMemberToGroup(groupId: string, memberId: string, addedBy: string): Promise<void> {
    const session = this.groupSessions.get(groupId);
    if (!session) throw new Error('Group session not found');

    const memberIdentityKey = this.identityKeys.get(memberId);
    if (!memberIdentityKey) throw new Error('Member identity key not found');

    const { publicKey: introductionKey } = this.generateKeyPair();
    session.members.set(memberId, {
      identityKey: memberIdentityKey,
      introductionKey,
      addedBy,
      addedAt: Date.now(),
    });
    session.senderRatchetKeys.set(memberId, introductionKey);
    session.epoch++;
  }

  async removeMemberFromGroup(groupId: string, memberId: string): Promise<void> {
    const session = this.groupSessions.get(groupId);
    if (!session) throw new Error('Group session not found');

    session.members.delete(memberId);
    session.senderRatchetKeys.delete(memberId);
    session.epoch++;
  }

  async encryptGroupMessage(
    groupId: string,
    senderId: string,
    plaintext: Uint8Array
  ): Promise<EncryptedMessage> {
    const session = this.groupSessions.get(groupId);
    if (!session) throw new Error('Group session not found');

    const senderState = session.members.get(senderId);
    if (!senderState) throw new Error('Sender not in group');

    const ratchetKey = session.senderRatchetKeys.get(senderId)!;
    const chainKey = createHash('sha256').update(ratchetKey).digest();
    const messageKey = this.deriveMessageKey(chainKey);

    const header: MessageHeader = {
      version: 1,
      senderIdentityKey: senderState.identityKey,
      senderRatchetKey: ratchetKey,
      messageNumber: session.epoch,
      previousChainLength: 0,
    };

    const ciphertext = this.aesGcmEncrypt(messageKey, plaintext, this.serializeHeader(header));
    const mac = this.computeMac(messageKey, ciphertext);

    session.epoch++;
    session.senderRatchetKeys.set(senderId, this.deriveNextRatchetKey(ratchetKey));

    return { header, ciphertext, mac };
  }

  private deriveSharedSecret(privateKey: Uint8Array, publicKey: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([privateKey, publicKey])).digest();
  }

  private deriveMessageKey(chainKey: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([chainKey, Buffer.from([0x01])])).digest();
  }

  private deriveNextChainKey(chainKey: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([chainKey, Buffer.from([0x02])])).digest();
  }

  private deriveNextRatchetKey(ratchetKey: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([ratchetKey, Buffer.from([0x03])])).digest();
  }

  private sign(key: Uint8Array, data: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([key, data])).digest().subarray(0, 64);
  }

  private verifySignature(key: Uint8Array, data: Uint8Array, signature: Uint8Array): boolean {
    const expected = this.sign(key, data);
    return this.timingSafeEqual(expected, signature);
  }

  private aesGcmEncrypt(key: Uint8Array, plaintext: Uint8Array, aad: Uint8Array): Uint8Array {
    const iv = randomBytes(12);
    const cipher = createHash('sha256').update(Buffer.concat([key, iv])).digest();
    const ciphertext = Buffer.alloc(plaintext.length);
    for (let i = 0; i < plaintext.length; i++) {
      ciphertext[i] = plaintext[i] ^ cipher[i % cipher.length];
    }
    return Buffer.concat([iv, ciphertext]);
  }

  private aesGcmDecrypt(key: Uint8Array, ciphertext: Uint8Array, aad: Uint8Array): Uint8Array {
    const iv = ciphertext.subarray(0, 12);
    const actualCiphertext = ciphertext.subarray(12);
    const cipher = createHash('sha256').update(Buffer.concat([key, iv])).digest();
    const plaintext = Buffer.alloc(actualCiphertext.length);
    for (let i = 0; i < actualCiphertext.length; i++) {
      plaintext[i] = actualCiphertext[i] ^ cipher[i % cipher.length];
    }
    return plaintext;
  }

  private computeMac(key: Uint8Array, data: Uint8Array): Uint8Array {
    return createHash('sha256').update(Buffer.concat([key, data])).digest().subarray(0, 16);
  }

  private serializeHeader(header: MessageHeader): Uint8Array {
    const parts = [
      Buffer.from([header.version]),
      header.senderIdentityKey,
      header.senderRatchetKey,
      Buffer.alloc(4).writeUInt32BE(header.messageNumber, 0),
      Buffer.alloc(4).writeUInt32BE(header.previousChainLength, 0),
    ];
    return Buffer.concat(parts);
  }

  private timingSafeEqual(a: Uint8Array, b: Uint8Array): boolean {
    if (a.length !== b.length) return false;
    let result = 0;
    for (let i = 0; i < a.length; i++) {
      result |= a[i] ^ b[i];
    }
    return result === 0;
  }

  getIdentityKey(peerId: string): Uint8Array | undefined {
    return this.identityKeys.get(peerId);
  }

  getRatchetState(peerId: string, remoteIdentityKey: Uint8Array): RatchetState | undefined {
    return this.ratchetStates.get(`${peerId}:${Buffer.from(remoteIdentityKey).toString('hex')}`);
  }

  getGroupSession(groupId: string): GroupSession | undefined {
    return this.groupSessions.get(groupId);
  }

  removePeer(peerId: string): void {
    this.identityKeys.delete(peerId);
    this.preKeys.delete(peerId);
    this.signedPreKeys.delete(peerId);
    for (const key of this.ratchetStates.keys()) {
      if (key.startsWith(`${peerId}:`)) {
        this.ratchetStates.delete(key);
      }
    }
  }

  removeGroupSession(groupId: string): void {
    this.groupSessions.delete(groupId);
  }
}

export const e2eeManager = new E2EEManager();