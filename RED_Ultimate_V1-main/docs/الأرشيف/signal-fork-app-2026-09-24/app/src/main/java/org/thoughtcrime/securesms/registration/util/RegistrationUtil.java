/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.util;

import org.signal.core.util.logging.Log;
import com.red.sovereign.backup.v2.BackupRepository;
import com.red.sovereign.backup.v2.MessageBackupTier;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.ArchiveBackupIdReservationJob;
import com.red.sovereign.jobs.DirectoryRefreshJob;
import com.red.sovereign.jobs.EmojiSearchIndexDownloadJob;
import com.red.sovereign.jobs.PostRegistrationBackupRedemptionJob;
import com.red.sovereign.jobs.RefreshAttributesJob;
import com.red.sovereign.jobs.StorageSyncJob;
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode;
import com.red.sovereign.keyvalue.RestoreDecisionStateUtil;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.RemoteConfig;

public final class RegistrationUtil {

  private static final String TAG = Log.tag(RegistrationUtil.class);

  private RegistrationUtil() {}

  /**
   * There's several events where a registration may or may not be considered complete based on what
   * path a user has taken. This will only truly mark registration as complete if all of the
   * requirements are met.
   */
  public static void maybeMarkRegistrationComplete() {
    if (!REDStore.registration().isRegistrationComplete() &&
        REDStore.account().isRegistered() &&
        !Recipient.self().getProfileName().isEmpty() &&
        (REDStore.svr().hasPin() || REDStore.svr().hasOptedOut() || REDStore.account().isLinkedDevice()) &&
        RestoreDecisionStateUtil.isTerminal(REDStore.registration().getRestoreDecisionState()))
    {
      Log.i(TAG, "Marking registration completed.", new Throwable());
      REDStore.registration().markRegistrationComplete();
      REDStore.registration().setLocalRegistrationMetadata(null);
      REDStore.registration().setRestoreMethodToken(null);

      if (REDStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode() == PhoneNumberDiscoverabilityMode.UNDECIDED) {
        Log.w(TAG, "Phone number discoverability mode is still UNDECIDED. Setting to DISCOVERABLE.");
        REDStore.phoneNumberPrivacy().setPhoneNumberDiscoverabilityMode(PhoneNumberDiscoverabilityMode.DISCOVERABLE);
      }

      AppDependencies.getJobManager().startChain(new RefreshAttributesJob())
                     .then(StorageSyncJob.forRemoteChange())
                     .then(new DirectoryRefreshJob(false))
                     .enqueue();

      REDStore.emoji().clearSearchIndexMetadata();
      EmojiSearchIndexDownloadJob.scheduleImmediately();


      BackupRepository.INSTANCE.resetInitializedStateAndAuthCredentials();
      AppDependencies.getJobManager().add(new ArchiveBackupIdReservationJob());
      AppDependencies.getJobManager().add(new PostRegistrationBackupRedemptionJob());

    } else if (!REDStore.registration().isRegistrationComplete()) {
      Log.i(TAG, "Registration is not yet complete.", new Throwable());
    }
  }
}
