package com.red.sovereign.database.model;

import com.red.sovereign.database.MessageTable;
import com.red.sovereign.database.MessageTypes;

final class StatusUtil {
  private StatusUtil() {}

  static boolean isDelivered(long deliveryStatus, boolean hasDeliveryReceipt) {
    return (deliveryStatus >= MessageTable.Status.STATUS_COMPLETE &&
            deliveryStatus < MessageTable.Status.STATUS_PENDING) || hasDeliveryReceipt;
  }

  static boolean isPending(long type) {
    return MessageTypes.isPendingMessageType(type) &&
           !MessageTypes.isIdentityVerified(type) &&
           !MessageTypes.isIdentityDefault(type);
  }

  static boolean isFailed(long type, long deliveryStatus) {
    return MessageTypes.isFailedMessageType(type) ||
           MessageTypes.isPendingSecureSmsFallbackType(type) ||
           MessageTypes.isPendingInsecureSmsFallbackType(type) ||
           deliveryStatus >= MessageTable.Status.STATUS_FAILED;
  }

  static boolean isVerificationStatusChange(long type) {
    return MessageTypes.isIdentityDefault(type) || MessageTypes.isIdentityVerified(type);
  }
}
