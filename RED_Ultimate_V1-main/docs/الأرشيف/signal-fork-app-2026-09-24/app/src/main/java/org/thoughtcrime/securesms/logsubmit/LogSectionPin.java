package com.red.sovereign.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.red.sovereign.keyvalue.REDStore;

public class LogSectionPin implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "PIN STATE";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Last Successful Reminder Entry: ").append(REDStore.pin().getLastSuccessfulEntryTime()).append("\n")
                              .append("Last Reminder Time: ").append(REDStore.pin().getLastReminderTime()).append("\n")
                              .append("Next Reminder Interval: ").append(REDStore.pin().getCurrentInterval()).append("\n")
                              .append("Reglock: ").append(REDStore.svr().isRegistrationLockEnabled()).append("\n")
                              .append("RED PIN: ").append(REDStore.svr().hasPin()).append("\n")
                              .append("Restored via AEP: ").append(REDStore.account().restoredAccountEntropyPool()).append("\n")
                              .append("Opted Out: ").append(REDStore.svr().hasOptedOut()).append("\n")
                              .append("Last Creation Failed: ").append(REDStore.svr().lastPinCreateFailed()).append("\n")
                              .append("Needs Account Restore: ").append(REDStore.storageService().getNeedsAccountRestore()).append("\n")
                              .append("PIN Required at Registration: ").append(REDStore.registration().pinWasRequiredAtRegistration()).append("\n")
                              .append("Registration Complete: ").append(REDStore.registration().isRegistrationComplete());

  }
}
