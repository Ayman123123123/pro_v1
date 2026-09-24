package com.red.sovereign.groups.ui;

import androidx.annotation.NonNull;

import com.red.sovereign.recipients.Recipient;

public interface RecipientLongClickListener {
  boolean onLongClick(@NonNull Recipient recipient);
}
