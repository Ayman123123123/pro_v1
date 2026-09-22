package com.red.sovereign.notifications;

import androidx.annotation.NonNull;

import com.red.sovereign.recipients.Recipient;

public enum ReplyMethod {

  GroupMessage,
  SecureMessage;

  public static @NonNull ReplyMethod forRecipient(Recipient recipient) {
    if (recipient.isGroup()) {
      return ReplyMethod.GroupMessage;
    } else {
      return ReplyMethod.SecureMessage;
    }
  }
}
