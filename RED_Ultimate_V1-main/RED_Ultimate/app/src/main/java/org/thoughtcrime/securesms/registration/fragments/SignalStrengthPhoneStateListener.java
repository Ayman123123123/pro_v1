/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.fragments;

import android.content.Context;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.REDStrength;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.signal.core.util.logging.Log;
import com.red.sovereign.dependencies.AppDependencies;
import org.signal.core.util.Debouncer;

// SIGNAL_INHERITED: TODO [nicholas]: move to v2 package and make package-private. convert to Kotlin
public final class REDStrengthPhoneStateListener extends PhoneStateListener
                                             implements DefaultLifecycleObserver
{
  private static final String TAG = Log.tag(REDStrengthPhoneStateListener.class);

  private final Callback  callback;
  private final Debouncer  debouncer    = new Debouncer(1000);
  private volatile boolean hasLowRED = true;

  @SuppressWarnings("deprecation")
  public REDStrengthPhoneStateListener(@NonNull LifecycleOwner lifecycleOwner, @NonNull Callback callback) {
    this.callback = callback;

    lifecycleOwner.getLifecycle().addObserver(this);
  }

  @Override
  public void onREDStrengthsChanged(REDStrength signalStrength) {
    if (signalStrength == null) return;

    if (isLowLevel(signalStrength)) {
      hasLowRED = true;
      Log.w(TAG, "No cell signal detected");
      debouncer.publish(callback::onNoCellREDPresent);
    } else {
      if (hasLowRED) {
        hasLowRED = false;
        Log.i(TAG, "Cell signal detected");
      }
      debouncer.clear();
      callback.onCellREDPresent();
    }
  }

  private boolean isLowLevel(@NonNull REDStrength signalStrength) {
    return signalStrength.getLevel() == 0;
  }

  public interface Callback {
    void onNoCellREDPresent();

    void onCellREDPresent();
  }

  @Override
  public void onResume(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) AppDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
    Log.i(TAG, "Listening to cell phone signal strength changes");
  }

  @Override
  public void onPause(@NonNull LifecycleOwner owner) {
    TelephonyManager telephonyManager = (TelephonyManager) AppDependencies.getApplication().getSystemService(Context.TELEPHONY_SERVICE);
    telephonyManager.listen(this, PhoneStateListener.LISTEN_NONE);
    Log.i(TAG, "Stopped listening to cell phone signal strength changes");
  }
}
