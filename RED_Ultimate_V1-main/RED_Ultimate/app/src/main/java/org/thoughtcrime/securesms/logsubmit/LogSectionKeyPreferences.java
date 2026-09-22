package com.red.sovereign.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import com.red.sovereign.keyvalue.KeepMessagesDuration;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.util.TextSecurePreferences;
import org.signal.core.util.Util;

final class LogSectionKeyPreferences implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "KEY PREFERENCES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    return new StringBuilder().append("Screen Lock              : ").append(REDStore.settings().getScreenLockEnabled()).append("\n")
                              .append("Screen Lock Timeout      : ").append(REDStore.settings().getScreenLockTimeout()).append("\n")
                              .append("Password Disabled        : ").append(REDStore.settings().getPassphraseDisabled()).append("\n")
                              .append("Prefer Contact Photos    : ").append(REDStore.settings().isPreferSystemContactPhotos()).append("\n")
                              .append("Call Data Mode           : ").append(REDStore.settings().getCallDataMode()).append("\n")
                              .append("Media Quality            : ").append(REDStore.settings().getSentMediaQuality()).append("\n")
                              .append("Client Deprecated        : ").append(REDStore.misc().isClientDeprecated()).append("\n")
                              .append("Push Registered          : ").append(REDStore.account().isRegistered()).append("\n")
                              .append("Unauthorized Received    : ").append(TextSecurePreferences.isUnauthorizedReceived(context)).append("\n")
                              .append("self.isRegistered()      : ").append(REDStore.account().getAci() == null ? "false"     : Recipient.self().isRegistered()).append("\n")
                              .append("Thread Trimming          : ").append(getThreadTrimmingString()).append("\n")
                              .append("Censorship Setting       : ").append(REDStore.settings().getCensorshipCircumventionEnabled()).append("\n")
                              .append("Network Reachable        : ").append(REDStore.misc().isServiceReachableWithoutCircumvention()).append(", last checked: ").append(REDStore.misc().getLastCensorshipServiceReachabilityCheckTime()).append("\n")
                              .append("Wifi Download            : ").append(Util.join(TextSecurePreferences.getWifiMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Roaming Download         : ").append(Util.join(TextSecurePreferences.getRoamingMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Mobile Download          : ").append(Util.join(TextSecurePreferences.getMobileMediaDownloadAllowed(context), ",")).append("\n")
                              .append("Phone Number Sharing     : ").append(REDStore.phoneNumberPrivacy().isPhoneNumberSharingEnabled()).append(" (").append(REDStore.phoneNumberPrivacy().getPhoneNumberSharingMode()).append(")\n")
                              .append("Phone Number Discoverable: ").append(REDStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode()).append("\n")
                              .append("Incognito keyboard       : ").append(TextSecurePreferences.isIncognitoKeyboardEnabled(context)).append("\n");
  }

  private static String getThreadTrimmingString() {
    if (REDStore.settings().isTrimByLengthEnabled()) {
      return "Enabled - Max length of " + REDStore.settings().getThreadTrimLength();
    } else if (REDStore.settings().getKeepMessagesDuration() != KeepMessagesDuration.FOREVER) {
      return "Enabled - Max age of " + REDStore.settings().getKeepMessagesDuration();
    } else {
      return "Disabled";
    }
  }
}
