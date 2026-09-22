/*
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.crypto;

import org.signal.core.models.ServiceId;
import org.signal.core.models.ServiceId.ACI;
import org.signal.core.util.UuidUtil;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.metadata.InvalidMetadataMessageException;
import org.signal.libsignal.metadata.InvalidMetadataVersionException;
import org.signal.libsignal.metadata.ProtocolDuplicateMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyException;
import org.signal.libsignal.metadata.ProtocolInvalidKeyIdException;
import org.signal.libsignal.metadata.ProtocolInvalidMessageException;
import org.signal.libsignal.metadata.ProtocolInvalidVersionException;
import org.signal.libsignal.metadata.ProtocolLegacyMessageException;
import org.signal.libsignal.metadata.ProtocolNoSessionException;
import org.signal.libsignal.metadata.ProtocolUntrustedIdentityException;
import org.signal.libsignal.metadata.SealedSessionCipher;
import org.signal.libsignal.metadata.SealedSessionCipher.DecryptionResult;
import org.signal.libsignal.metadata.SelfSendException;
import org.signal.libsignal.metadata.certificate.CertificateValidator;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.signal.libsignal.metadata.protocol.UnidentifiedSenderMessageContent;
import org.signal.libsignal.protocol.DuplicateMessageException;
import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.InvalidKeyIdException;
import org.signal.libsignal.protocol.InvalidMessageException;
import org.signal.libsignal.protocol.InvalidRegistrationIdException;
import org.signal.libsignal.protocol.InvalidVersionException;
import org.signal.libsignal.protocol.LegacyMessageException;
import org.signal.libsignal.protocol.NoSessionException;
import org.signal.libsignal.protocol.SessionCipher;
import org.signal.libsignal.protocol.REDProtocolAddress;
import org.signal.libsignal.protocol.UntrustedIdentityException;
import org.signal.libsignal.protocol.groups.GroupCipher;
import org.signal.libsignal.protocol.message.CiphertextMessage;
import org.signal.libsignal.protocol.message.PlaintextContent;
import org.signal.libsignal.protocol.message.PreKeyREDMessage;
import org.signal.libsignal.protocol.message.REDMessage;
import org.signal.libsignal.protocol.state.SessionRecord;
import org.whispersystems.signalservice.api.InvalidMessageStructureException;
import org.whispersystems.signalservice.api.REDServiceAccountDataStore;
import org.whispersystems.signalservice.api.REDSessionLock;
import org.whispersystems.signalservice.api.messages.REDServiceMetadata;
import org.whispersystems.signalservice.api.push.DistributionId;
import org.whispersystems.signalservice.api.push.REDServiceAddress;
import org.whispersystems.signalservice.internal.push.Content;
import org.whispersystems.signalservice.internal.push.Envelope;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.PushTransportDetails;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

/**
 * This is used to encrypt + decrypt received envelopes.
 */
public class REDServiceCipher {

  @SuppressWarnings("unused")
  private static final String TAG = REDServiceCipher.class.getSimpleName();

  private final REDServiceAccountDataStore signalProtocolStore;
  private final REDSessionLock             sessionLock;
  private final REDServiceAddress localAddress;
  private final int                  localDeviceId;
  private final CertificateValidator certificateValidator;

  public REDServiceCipher(REDServiceAddress localAddress,
                             int localDeviceId,
                             REDServiceAccountDataStore signalProtocolStore,
                             REDSessionLock sessionLock,
                             CertificateValidator certificateValidator)
  {
    this.signalProtocolStore  = signalProtocolStore;
    this.sessionLock          = sessionLock;
    this.localAddress         = localAddress;
    this.localDeviceId        = localDeviceId;
    this.certificateValidator = certificateValidator;
  }

  public byte[] encryptForGroup(DistributionId distributionId,
                                List<REDProtocolAddress> destinations,
                                Map<REDProtocolAddress, SessionRecord> sessionMap,
                                SenderCertificate senderCertificate,
                                byte[] unpaddedMessage,
                                ContentHint contentHint,
                                Optional<byte[]> groupId)
      throws NoSessionException, UntrustedIdentityException, InvalidKeyException, InvalidRegistrationIdException
  {
    PushTransportDetails             transport            = new PushTransportDetails();
    REDProtocolAddress            localProtocolAddress = new REDProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    REDGroupCipher                groupCipher          = new REDGroupCipher(sessionLock, new GroupCipher(signalProtocolStore, localProtocolAddress));
    REDSealedSessionCipher        sessionCipher        = new REDSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber().orElse(null), localDeviceId));
    CiphertextMessage                message              = groupCipher.encrypt(distributionId.asUuid(), transport.getPaddedMessageBody(unpaddedMessage));
    UnidentifiedSenderMessageContent messageContent       = new UnidentifiedSenderMessageContent(message,
                                                                                                 senderCertificate,
                                                                                                 contentHint.getType(),
                                                                                                 groupId);

    return sessionCipher.multiRecipientEncrypt(destinations, sessionMap, messageContent);
  }

  public OutgoingPushMessage encrypt(REDProtocolAddress destination,
                                     @Nullable SealedSenderAccess sealedSenderAccess,
                                     EnvelopeContent content)
      throws UntrustedIdentityException, InvalidKeyException, NoSessionException
  {
    REDProtocolAddress localProtocolAddress = new REDProtocolAddress(localAddress.getIdentifier(), localDeviceId);
    REDSessionCipher   sessionCipher        = new REDSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, localProtocolAddress, destination));
    if (sealedSenderAccess != null) {
      REDSealedSessionCipher sealedSessionCipher = new REDSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber()
                                                                                                                                                                                                    .orElse(null), localDeviceId));

      return content.processSealedSender(sessionCipher, sealedSessionCipher, destination, sealedSenderAccess.getSenderCertificate());
    } else {
      return content.processUnsealedSender(sessionCipher, destination);
    }
  }

  public REDServiceCipherResult decrypt(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
             ProtocolInvalidKeyIdException, ProtocolLegacyMessageException,
             ProtocolUntrustedIdentityException, ProtocolNoSessionException,
             ProtocolInvalidVersionException, ProtocolInvalidMessageException,
             ProtocolInvalidKeyException, ProtocolDuplicateMessageException,
             SelfSendException, InvalidMessageStructureException
  {
    try {
      if (envelope.content != null) {
        Plaintext plaintext = decryptInternal(envelope, serverDeliveredTimestamp);
        Content   content   = Content.ADAPTER.decode(plaintext.getData());

        return new REDServiceCipherResult(
            content,
            new EnvelopeMetadata(
                plaintext.metadata.getSender().getServiceId(),
                plaintext.metadata.getSender().getNumber().orElse(null),
                plaintext.metadata.getSenderDevice(),
                plaintext.metadata.isNeedsReceipt(),
                plaintext.metadata.getGroupId().orElse(null),
                localAddress.getServiceId(),
                plaintext.getCiphertextMessageType()
            )
        );
      } else {
        return null;
      }
    } catch (IOException | IllegalArgumentException e) {
      throw new InvalidMetadataMessageException(e);
    }
  }

  private Plaintext decryptInternal(Envelope envelope, long serverDeliveredTimestamp)
      throws InvalidMetadataMessageException, InvalidMetadataVersionException,
      ProtocolDuplicateMessageException, ProtocolUntrustedIdentityException,
      ProtocolLegacyMessageException, ProtocolInvalidKeyException,
      ProtocolInvalidVersionException, ProtocolInvalidMessageException,
      ProtocolInvalidKeyIdException, ProtocolNoSessionException,
      SelfSendException, InvalidMessageStructureException
  {
    ServiceId sourceServiceId = ServiceId.parseOrNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary);
    try {
      ServiceId destinationServiceId = ServiceId.parseOrNull(envelope.destinationServiceId, envelope.destinationServiceIdBinary);
      String    destinationStr       = (destinationServiceId != null) ? destinationServiceId.toString() : "";
      String    serverGuid           = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary);

      byte[]                paddedMessage;
      REDServiceMetadata metadata;
      int                   ciphertextMessageType;

      if (sourceServiceId == null && envelope.type != Envelope.Type.UNIDENTIFIED_SENDER) {
        throw new InvalidMessageStructureException("Non-UD envelope is missing a UUID!");
      }

      REDProtocolAddress localProtocolAddress = new REDProtocolAddress(localAddress.getIdentifier(), localDeviceId);

      if (envelope.type == Envelope.Type.PREKEY_MESSAGE) {
        REDProtocolAddress sourceAddress = new REDProtocolAddress(sourceServiceId.toString(), envelope.sourceDeviceId);
        REDSessionCipher   sessionCipher = new REDSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, localProtocolAddress, sourceAddress));

        paddedMessage         = sessionCipher.decrypt(new PreKeyREDMessage(envelope.content.toByteArray()));
        metadata              = new REDServiceMetadata(getSourceAddress(envelope), envelope.sourceDeviceId, envelope.clientTimestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);
        ciphertextMessageType = CiphertextMessage.PREKEY_TYPE;

        signalProtocolStore.clearSenderKeySharedWith(Collections.singleton(sourceAddress));
      } else if (envelope.type == Envelope.Type.DOUBLE_RATCHET) {
        REDProtocolAddress sourceAddress = new REDProtocolAddress(sourceServiceId.toString(), envelope.sourceDeviceId);
        REDSessionCipher   sessionCipher = new REDSessionCipher(sessionLock, new SessionCipher(signalProtocolStore, localProtocolAddress,  sourceAddress));

        paddedMessage         = sessionCipher.decrypt(new REDMessage(envelope.content.toByteArray()));
        metadata              = new REDServiceMetadata(getSourceAddress(envelope), envelope.sourceDeviceId, envelope.clientTimestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);
        ciphertextMessageType = CiphertextMessage.WHISPER_TYPE;
      } else if (envelope.type == Envelope.Type.PLAINTEXT_CONTENT) {
        paddedMessage         = new PlaintextContent(envelope.content.toByteArray()).getBody();
        metadata              = new REDServiceMetadata(getSourceAddress(envelope), envelope.sourceDeviceId, envelope.clientTimestamp, envelope.serverTimestamp, serverDeliveredTimestamp, false, serverGuid, Optional.empty(), destinationStr);
        ciphertextMessageType = CiphertextMessage.PLAINTEXT_CONTENT_TYPE;
      } else if (envelope.type == Envelope.Type.UNIDENTIFIED_SENDER) {
        REDSealedSessionCipher sealedSessionCipher = new REDSealedSessionCipher(sessionLock, new SealedSessionCipher(signalProtocolStore, localAddress.getServiceId().getRawUuid(), localAddress.getNumber().orElse(null), localDeviceId));
        DecryptionResult          result              = sealedSessionCipher.decrypt(certificateValidator, envelope.content.toByteArray(), envelope.serverTimestamp);
        REDServiceAddress      resultAddress       = new REDServiceAddress(ACI.parseOrThrow(result.getSenderUuid()), result.getSenderE164());
        Optional<byte[]>          groupId             = result.getGroupId();
        boolean                   needsReceipt        = true;

        if (sourceServiceId != null) {
          Log.w(TAG, "[" + envelope.clientTimestamp + "] Received a UD-encrypted message sent over an identified channel. Marking as needsReceipt=false");
          needsReceipt = false;
        }

        ciphertextMessageType = result.getCiphertextMessageType();

        if (ciphertextMessageType == CiphertextMessage.PREKEY_TYPE) {
          signalProtocolStore.clearSenderKeySharedWith(Collections.singleton(new REDProtocolAddress(result.getSenderUuid(), result.getDeviceId())));
        }

        paddedMessage = result.getPaddedMessage();
        metadata      = new REDServiceMetadata(resultAddress, result.getDeviceId(), envelope.clientTimestamp, envelope.serverTimestamp, serverDeliveredTimestamp, needsReceipt, serverGuid, groupId, destinationStr);
      } else {
        throw new InvalidMetadataMessageException("Unknown type: " + envelope.type);
      }

      PushTransportDetails transportDetails = new PushTransportDetails();
      byte[]               data             = transportDetails.getStrippedPaddingMessageBody(paddedMessage);

      return new Plaintext(metadata, data, ciphertextMessageType);
    } catch (DuplicateMessageException e) {
      throw new ProtocolDuplicateMessageException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (LegacyMessageException e) {
      throw new ProtocolLegacyMessageException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (InvalidMessageException e) {
      throw new ProtocolInvalidMessageException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (InvalidKeyIdException e) {
      throw new ProtocolInvalidKeyIdException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (InvalidKeyException e) {
      throw new ProtocolInvalidKeyException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (UntrustedIdentityException e) {
      throw new ProtocolUntrustedIdentityException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (InvalidVersionException e) {
      throw new ProtocolInvalidVersionException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    } catch (NoSessionException e) {
      throw new ProtocolNoSessionException(e, sourceServiceId.toString(), envelope.sourceDeviceId);
    }
  }

  private static REDServiceAddress getSourceAddress(Envelope envelope) {
    return new REDServiceAddress(ServiceId.parseOrNull(envelope.sourceServiceId, envelope.sourceServiceIdBinary));
  }

  private static class Plaintext {
    private final REDServiceMetadata metadata;
    private final byte[]                data;
    private final int                   ciphertextMessageType;

    private Plaintext(REDServiceMetadata metadata, byte[] data, int ciphertextMessageType) {
      this.metadata              = metadata;
      this.data                  = data;
      this.ciphertextMessageType = ciphertextMessageType;
    }

    public REDServiceMetadata getMetadata() {
      return metadata;
    }

    public byte[] getData() {
      return data;
    }

    public int getCiphertextMessageType() {
      return ciphertextMessageType;
    }
  }
}
