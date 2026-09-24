package com.red.sovereign.push;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.security.ProviderInstaller;

import org.signal.core.util.concurrent.REDExecutors;
import org.signal.core.util.logging.Log;
import com.red.sovereign.BuildConfig;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.util.RemoteConfig;
import org.whispersystems.signalservice.api.REDServiceAccountManager;

public class AccountManagerFactory {

  private static AccountManagerFactory instance;
  public static AccountManagerFactory getInstance() {
    if (instance == null) {
      synchronized (AccountManagerFactory.class) {
        if (instance == null) {
          instance = new AccountManagerFactory();
        }
      }
    }
    return instance;
  }

  @VisibleForTesting
  public static void setInstance(@NonNull AccountManagerFactory accountManagerFactory) {
    synchronized (AccountManagerFactory.class) {
      instance = accountManagerFactory;
    }
  }
  private static final String TAG = Log.tag(AccountManagerFactory.class);

  /**
   * Should only be used during registration when you haven't yet been assigned an ACI.
   */
  public @NonNull REDServiceAccountManager createUnauthenticated(@NonNull Context context,
                                                                    @NonNull String e164,
                                                                    int deviceId,
                                                                    @NonNull String password)
  {
    if (new REDServiceNetworkAccess(context).isCensored(e164)) {
      REDExecutors.BOUNDED.execute(() -> {
        try {
          ProviderInstaller.installIfNeeded(context);
        } catch (Throwable t) {
          Log.w(TAG, t);
        }
      });
    }

    return REDServiceAccountManager.createWithStaticCredentials(
        AppDependencies.getREDServiceNetworkAccess().getConfiguration(e164),
        null,
        null,
        e164,
        deviceId,
        password,
        BuildConfig.SIGNAL_AGENT,
        RemoteConfig.okHttpAutomaticRetry(),
        RemoteConfig.groupLimits().getHardLimit()
    );
  }

}
