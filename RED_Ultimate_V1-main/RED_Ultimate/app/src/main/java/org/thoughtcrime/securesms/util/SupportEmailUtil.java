package com.red.sovereign.util;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.signal.core.util.ResourceUtil;
import org.signal.core.util.Util;
import com.red.sovereign.BuildConfig;
import com.red.sovereign.R;
import com.red.sovereign.backup.v2.MessageBackupTier;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SupportEmailUtil {

  private SupportEmailUtil() { }

  public static @NonNull String getSupportEmailAddress(@NonNull Context context) {
    return context.getString(R.string.SupportEmailUtil_support_email);
  }

  /**
   * Generates a support email body with system info near the top.
   */
  public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                         @StringRes int filter,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    return generateSupportEmailBody(context, filter, null, prefix, suffix);
  }

  /**
   * Generates a support email body with system info near the top.
   */
  public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                         @StringRes int filter,
                                                         @Nullable String filterSuffix,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    filterSuffix = Util.emptyIfNull(filterSuffix);

    return generateSupportEmailBody(context, ResourceUtil.getEnglishResources(context).getString(filter) + filterSuffix, prefix, suffix);
  }

  /**
   * Generates a support email body with system info near the top, using the given already-resolved filter text.
   */
  public static @NonNull String generateSupportEmailBody(@NonNull Context context,
                                                         @NonNull String filter,
                                                         @Nullable String prefix,
                                                         @Nullable String suffix)
  {
    prefix = Util.emptyIfNull(prefix);
    suffix = Util.emptyIfNull(suffix);

    return String.format("%s\n%s\n%s", prefix, buildSystemInfo(context, filter), suffix);
  }

  private static @NonNull String buildSystemInfo(@NonNull Context context, @NonNull String filter) {
    return "--- " + context.getString(R.string.HelpFragment__support_info) + " ---" +
           "\n" +
           context.getString(R.string.SupportEmailUtil_filter) + " " + filter +
           "\n" +
           context.getString(R.string.SupportEmailUtil_device_info) + " " + getDeviceInfo() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_android_version) + " " + getAndroidVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_version) + " " + getREDVersion() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_signal_package) + " " + getREDPackage(context) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_registration_lock) + " " + getRegistrationLockEnabled() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_locale) + " " + Locale.getDefault().toString() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_challenge_received) + " " + getChallengeReceived() +
           "\n" +
           context.getString(R.string.SupportEmailUtil_registered) + " " + getRegistered(context) +
           "\n" +
           context.getString(R.string.SupportEmailUtil_backups) + " " + getBackupTier();
  }

  private static CharSequence getDeviceInfo() {
    return String.format("%s %s (%s)", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
  }

  private static CharSequence getAndroidVersion() {
    return String.format("%s (%s, %s)", Build.VERSION.RELEASE, Build.VERSION.INCREMENTAL, Build.DISPLAY);
  }

  private static CharSequence getREDVersion() {
    return BuildConfig.VERSION_NAME;
  }

  private static CharSequence getREDPackage(@NonNull Context context) {
    return String.format("%s (%s)", BuildConfig.APPLICATION_ID, AppSignatureUtil.getAppSignature(context));
  }

  private static CharSequence getRegistrationLockEnabled() {
    return String.valueOf(REDStore.svr().isRegistrationLockEnabled());
  }

  private static String getChallengeReceived() {
    long    captchaLastViewedAt = REDStore.misc().getCaptchaLastViewedAt();
    boolean receivedRecently    = captchaLastViewedAt > 0 && (System.currentTimeMillis() - captchaLastViewedAt) <= TimeUnit.DAYS.toMillis(3);

    return receivedRecently ? "yes" : "no";
  }

  private static String getRegistered(Context context) {
    boolean registered = REDStore.account().isRegistered() && !TextSecurePreferences.isUnauthorizedReceived(context);
    return registered ? "yes" : "no";
  }

  private static String getBackupTier() {
    MessageBackupTier tier = REDStore.backup().getBackupTier();

    if (tier == null) {
      return "D1";
    }

    switch (tier) {
      case FREE: return "F1";
      case PAID: return "P1";
      default:   return "D1";
    }
  }
}
