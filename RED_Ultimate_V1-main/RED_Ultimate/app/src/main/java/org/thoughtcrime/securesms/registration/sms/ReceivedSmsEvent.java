/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.sms;

import androidx.annotation.NonNull;

public final class ReceivedSmsEvent {

  public static final int CODE_LENGTH = 6;

  private final @NonNull String code;

  public ReceivedSmsEvent(@NonNull String code) {
    this.code = code;
  }

  public @NonNull String getCode() {
    return code;
  }
}
