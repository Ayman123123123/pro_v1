package com.red.sovereign.megaphone;

import com.red.sovereign.keyvalue.REDStore;

final class REDPinReminderSchedule implements MegaphoneSchedule {

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (REDStore.svr().hasOptedOut()) {
      return false;
    }

    if (!REDStore.svr().hasPin()) {
      return false;
    }

    if (REDStore.account().isLinkedDevice()) {
      return false;
    }

    if (!REDStore.pin().arePinRemindersEnabled()) {
      return false;
    }

    if (!REDStore.account().isRegistered()) {
      return false;
    }

    if (REDStore.account().isLinkedDevice()) {
      return false;
    }

    long lastReminderTime = REDStore.pin().getLastReminderTime();
    long interval         = REDStore.pin().getCurrentInterval();

    return currentTime - lastReminderTime >= interval;
  }
}
