package com.red.sovereign.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.red.sovereign.AppCapabilities;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.RecipientRecord;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.RemoteConfig;
import org.whispersystems.signalservice.api.account.AccountAttributes;

public final class LogSectionCapabilities implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "CAPABILITIES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    if (!REDStore.account().isRegistered()) {
      return "Unregistered";
    }

    if (REDStore.account().getE164() == null || REDStore.account().getAci() == null) {
      return "Self not yet available!";
    }

    Recipient self = Recipient.self();

    AccountAttributes.Capabilities localCapabilities  = AppCapabilities.getCapabilities(false);
    RecipientRecord.Capabilities   globalCapabilities = REDDatabase.recipients().getCapabilities(self.getId());

    StringBuilder builder = new StringBuilder().append("-- Local").append("\n")
                                               .append("VersionedExpirationTimer: ").append(localCapabilities.getVersionedExpirationTimer()).append("\n")
                                               .append("\n")
                                               .append("-- Global").append("\n")
                                               .append("None").append("\n");

    // Left as an example for when we want to add new ones
//    if (globalCapabilities != null) {
//      builder.append("StorageServiceEncryptionV2: ").append(globalCapabilities.getStorageServiceEncryptionV2()).append("\n");
//      builder.append("\n");
//    } else {
//      builder.append("Self not found!");
//    }

    return builder;
  }
}
