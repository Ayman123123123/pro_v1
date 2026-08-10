package com.red.sovereign.megaphone;

import androidx.annotation.VisibleForTesting;

import org.signal.core.util.logging.Log;
import com.red.sovereign.keyvalue.REDStore;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

class PinsForAllSchedule implements MegaphoneSchedule {

  private static final String TAG = Log.tag(PinsForAllSchedule.class);

  @VisibleForTesting
  static final long DAYS_UNTIL_FULLSCREEN = 4L;

  private final MegaphoneSchedule schedule = new RecurringSchedule(TimeUnit.HOURS.toMillis(2));

  static boolean shouldDisplayFullScreen(long firstVisible, long currentTime) {
    if (firstVisible == 0L) {
      return false;
    }

    return currentTime - firstVisible >= TimeUnit.DAYS.toMillis(DAYS_UNTIL_FULLSCREEN);
  }

  @Override
  public boolean shouldDisplay(int seenCount, long lastSeen, long firstVisible, long currentTime) {
    if (!isEnabled()) {
      return false;
    }

    if (shouldDisplayFullScreen(firstVisible, currentTime)) {
      return true;
    } else {
      boolean shouldDisplay = schedule.shouldDisplay(seenCount, lastSeen, firstVisible, currentTime);
      Log.i(TAG, String.format(Locale.ENGLISH, "seenCount: %d,  lastSeen: %d,  firstVisible: %d,  currentTime: %d, result: %b", seenCount, lastSeen, firstVisible, currentTime, shouldDisplay));
      return shouldDisplay;
    }
  }

  private static boolean isEnabled() {
    if (REDStore.svr().hasOptedOut()) {
      return false;
    }

    if (REDStore.svr().hasPin()) {
      return false;
    }

    if (REDStore.account().isLinkedDevice()) {
      return false;
    }

    if (pinCreationFailedDuringRegistration()) {
      return true;
    }

    if (REDStore.registration().pinWasRequiredAtRegistration()) {
      return false;
    }

    return true;
  }

  private static boolean pinCreationFailedDuringRegistration() {
    return REDStore.registration().pinWasRequiredAtRegistration() &&
           !REDStore.svr().hasPin();
  }
}
