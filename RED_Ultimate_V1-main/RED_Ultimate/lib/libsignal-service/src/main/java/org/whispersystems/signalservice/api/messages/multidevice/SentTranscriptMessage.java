/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;


import org.whispersystems.signalservice.api.messages.REDServiceDataMessage;
import org.whispersystems.signalservice.api.messages.REDServiceEditMessage;
import org.whispersystems.signalservice.api.messages.REDServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.REDServiceStoryMessageRecipient;
import org.signal.core.models.ServiceId;
import org.whispersystems.signalservice.api.push.REDServiceAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SentTranscriptMessage {

  private final Optional<REDServiceAddress>          destination;
  private final long                                    timestamp;
  private final long                                    expirationStartTimestamp;
  private final Optional<REDServiceDataMessage>      message;
  private final Map<ServiceId, Boolean>                 unidentifiedStatusBySid;
  private final Set<ServiceId>                          recipients;
  private final boolean                                 isRecipientUpdate;
  private final Optional<REDServiceStoryMessage>     storyMessage;
  private final Set<REDServiceStoryMessageRecipient> storyMessageRecipients;
  private final Optional<REDServiceEditMessage>      editMessage;

  public SentTranscriptMessage(Optional<REDServiceAddress> destination,
                               long timestamp,
                               Optional<REDServiceDataMessage> message,
                               long expirationStartTimestamp,
                               Map<ServiceId, Boolean> unidentifiedStatus,
                               boolean isRecipientUpdate,
                               Optional<REDServiceStoryMessage> storyMessage,
                               Set<REDServiceStoryMessageRecipient> storyMessageRecipients,
                               Optional<REDServiceEditMessage> editMessage)
  {
    this.destination              = destination;
    this.timestamp                = timestamp;
    this.message                  = message;
    this.expirationStartTimestamp = expirationStartTimestamp;
    this.unidentifiedStatusBySid  = new HashMap<>(unidentifiedStatus);
    this.recipients               = unidentifiedStatus.keySet();
    this.isRecipientUpdate        = isRecipientUpdate;
    this.storyMessage             = storyMessage;
    this.storyMessageRecipients   = storyMessageRecipients;
    this.editMessage              = editMessage;
  }

  public Optional<REDServiceAddress> getDestination() {
    return destination;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getExpirationStartTimestamp() {
    return expirationStartTimestamp;
  }

  public Optional<REDServiceDataMessage> getDataMessage() {
    return message;
  }

  public Optional<REDServiceEditMessage> getEditMessage() {
    return editMessage;
  }

  public Optional<REDServiceStoryMessage> getStoryMessage() {
    return storyMessage;
  }

  public Set<REDServiceStoryMessageRecipient> getStoryMessageRecipients() {
    return storyMessageRecipients;
  }

  public boolean isUnidentified(ServiceId serviceId) {
    return unidentifiedStatusBySid.getOrDefault(serviceId, false);
  }

  public Set<ServiceId> getRecipients() {
    return recipients;
  }

  public boolean isRecipientUpdate() {
    return isRecipientUpdate;
  }
}
