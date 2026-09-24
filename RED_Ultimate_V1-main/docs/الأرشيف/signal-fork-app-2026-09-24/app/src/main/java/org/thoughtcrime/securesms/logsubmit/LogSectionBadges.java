package com.red.sovereign.logsubmit;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.donations.InAppPaymentType;
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository;
import com.red.sovereign.database.InAppPaymentTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord;
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.recipients.Recipient;

final class LogSectionBadges implements LogSection {

  @Override
  public @NonNull String getTitle() {
    return "BADGES";
  }

  @Override
  public @NonNull CharSequence getContent(@NonNull Context context) {
    if (!REDStore.account().isRegistered()) {
      return "Unregistered";
    }

    if (REDStore.account().getE164() == null || REDStore.account().getAci() == null) {
      return "Self not yet available!";
    }

    InAppPaymentTable.InAppPayment latestRecurringDonation = REDDatabase.inAppPayments().getLatestInAppPaymentByType(InAppPaymentType.RECURRING_DONATION);

    if (latestRecurringDonation != null) {
      InAppPaymentSubscriberRecord donationSubscriber = InAppPaymentsRepository.getSubscriber(InAppPaymentSubscriberRecord.Type.DONATION);
      boolean shouldCancel = donationSubscriber != null
          ? donationSubscriber.getRequiresCancel()
          : REDStore.inAppPayments().getShouldCancelSubscriptionBeforeNextSubscribeAttempt();

      return new StringBuilder().append("Badge Count                       : ").append(Recipient.self().getBadges().size()).append("\n")
                                .append("ExpiredBadge                      : ").append(REDStore.inAppPayments().getExpiredBadge() != null).append("\n")
                                .append("LastKeepAliveLaunchTime           : ").append(REDStore.inAppPayments().getLastKeepAliveLaunchTime()).append("\n")
                                .append("LastEndOfPeriod                   : ").append(REDStore.inAppPayments().getLastEndOfPeriod()).append("\n")
                                .append("InAppPayment.State                : ").append(latestRecurringDonation.getState()).append("\n")
                                .append("InAppPayment.EndOfPeriod          : ").append(latestRecurringDonation.getEndOfPeriodSeconds()).append("\n")
                                .append("InAppPaymentData.PaymentMethodType: ").append(getPaymentMethod(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.RedemptionState  : ").append(getRedemptionStage(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.Error            : ").append(getError(latestRecurringDonation.getData())).append("\n")
                                .append("InAppPaymentData.Cancellation     : ").append(getCancellation(latestRecurringDonation.getData())).append("\n")
                                .append("DisplayBadgesOnProfile            : ").append(REDStore.inAppPayments().getDisplayBadgesOnProfile()).append("\n")
                                .append("ShouldCancelBeforeNextAttempt     : ").append(shouldCancel).append("\n")
                                .append("IsUserManuallyCancelledDonation   : ").append(REDStore.inAppPayments().isDonationSubscriptionManuallyCancelled()).append("\n");

    } else {
      return new StringBuilder().append("Badge Count                             : ").append(Recipient.self().getBadges().size()).append("\n")
                                .append("ExpiredBadge                            : ").append(REDStore.inAppPayments().getExpiredBadge() != null).append("\n")
                                .append("LastKeepAliveLaunchTime                 : ").append(REDStore.inAppPayments().getLastKeepAliveLaunchTime()).append("\n")
                                .append("LastEndOfPeriod                         : ").append(REDStore.inAppPayments().getLastEndOfPeriod()).append("\n")
                                .append("IsUserManuallyCancelledDonation         : ").append(REDStore.inAppPayments().isDonationSubscriptionManuallyCancelled()).append("\n")
                                .append("DisplayBadgesOnProfile                  : ").append(REDStore.inAppPayments().getDisplayBadgesOnProfile()).append("\n")
                                .append("SubscriptionRedemptionFailed            : ").append(REDStore.inAppPayments().getSubscriptionRedemptionFailed()).append("\n")
                                .append("ShouldCancelBeforeNextAttempt           : ").append(REDStore.inAppPayments().getShouldCancelSubscriptionBeforeNextSubscribeAttempt()).append("\n");
    }
  }

  private @NonNull String getPaymentMethod(@NonNull InAppPaymentData inAppPaymentData) {
    return inAppPaymentData.paymentMethodType.toString();
  }

  private @NonNull String getRedemptionStage(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.redemption == null) {
      return "null";
    } else {
      return inAppPaymentData.redemption.stage.name();
    }
  }

  private @NonNull String getError(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.error == null) {
      return "none";
    } else {
      return inAppPaymentData.error.toString();
    }
  }

  private @NonNull String getCancellation(@NonNull InAppPaymentData inAppPaymentData) {
    if (inAppPaymentData.cancellation == null) {
      return "none";
    } else {
      return inAppPaymentData.cancellation.reason.name();
    }
  }
}
