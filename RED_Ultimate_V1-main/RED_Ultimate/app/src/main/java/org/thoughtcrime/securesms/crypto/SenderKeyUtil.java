package com.red.sovereign.crypto;

import androidx.annotation.NonNull;

import org.signal.libsignal.protocol.REDProtocolAddress;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.keyvalue.REDStore;
import org.whispersystems.signalservice.api.REDSessionLock;
import org.whispersystems.signalservice.api.push.DistributionId;

public final class SenderKeyUtil {
  private SenderKeyUtil() {}

  /**
   * Clears the state for a sender key session we created. It will naturally get re-created when it is next needed, rotating the key.
   */
  public static void rotateOurKey(@NonNull DistributionId distributionId) {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAllFor(REDStore.account().requireAci().toString(), distributionId);
      REDDatabase.senderKeyShared().deleteAllFor(distributionId);
    }
  }

  /**
   * Gets when the sender key session was created, or -1 if it doesn't exist.
   */
  public static long getCreateTimeForOurKey(@NonNull DistributionId distributionId) {
    REDProtocolAddress address = new REDProtocolAddress(REDStore.account().requireAci().toString(), REDStore.account().getDeviceId());
    return REDDatabase.senderKeys().getCreatedTime(address, distributionId);
  }

  /**
   * Deletes all stored state around session keys. Should only really be used when the user is re-registering.
   */
  public static void clearAllState() {
    try (REDSessionLock.Lock unused = ReentrantSessionLock.INSTANCE.acquire()) {
      AppDependencies.getProtocolStore().aci().senderKeys().deleteAll();
      REDDatabase.senderKeyShared().deleteAll();
    }
  }
}
