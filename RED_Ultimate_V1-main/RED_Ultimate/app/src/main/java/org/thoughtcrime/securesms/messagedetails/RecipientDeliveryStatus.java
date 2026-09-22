package com.red.sovereign.messagedetails;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.red.sovereign.database.documents.IdentityKeyMismatch;
import com.red.sovereign.database.documents.NetworkFailure;
import com.red.sovereign.database.model.MessageRecord;
import com.red.sovereign.recipients.Recipient;

public final class RecipientDeliveryStatus {

  enum Status {
    UNKNOWN, PENDING, SENT, DELIVERED, READ, VIEWED, SKIPPED,
  }

  private final MessageRecord       messageRecord;
  private final Recipient           recipient;
  private final Status              deliveryStatus;
  private final boolean             isUnidentified;
  private final long                timestamp;
  private final NetworkFailure      networkFailure;
  private final IdentityKeyMismatch keyMismatchFailure;

  RecipientDeliveryStatus(@NonNull MessageRecord messageRecord, @NonNull Recipient recipient, @NonNull Status deliveryStatus, boolean isUnidentified, long timestamp, @Nullable NetworkFailure networkFailure, @Nullable IdentityKeyMismatch keyMismatchFailure) {
    this.messageRecord      = messageRecord;
    this.recipient          = recipient;
    this.deliveryStatus     = deliveryStatus;
    this.isUnidentified     = isUnidentified;
    this.timestamp          = timestamp;
    this.networkFailure     = networkFailure;
    this.keyMismatchFailure = keyMismatchFailure;
  }

  public @NonNull MessageRecord getMessageRecord() {
    return messageRecord;
  }

  public @NonNull Status getDeliveryStatus() {
    return deliveryStatus;
  }

  public boolean isUnidentified() {
    return isUnidentified;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public @NonNull Recipient getRecipient() {
    return recipient;
  }

  public @Nullable NetworkFailure getNetworkFailure() {
    return networkFailure;
  }

  public @Nullable IdentityKeyMismatch getKeyMismatchFailure() {
    return keyMismatchFailure;
  }
}
