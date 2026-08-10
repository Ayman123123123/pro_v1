/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.app.subscription.completed

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.concurrent.LifecycleDisposable
import com.red.sovereign.backup.v2.ui.BackupAlert
import com.red.sovereign.backup.v2.ui.BackupAlertBottomSheet
import com.red.sovereign.badges.Badges
import com.red.sovereign.badges.self.expired.MonthlyDonationCanceledBottomSheetDialogFragment
import com.red.sovereign.components.settings.app.subscription.DonationPendingBottomSheet
import com.red.sovereign.components.settings.app.subscription.DonationPendingBottomSheetArgs
import com.red.sovereign.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragment
import com.red.sovereign.components.settings.app.subscription.thanks.ThanksForYourSupportBottomSheetDialogFragmentArgs
import com.red.sovereign.database.DatabaseObserver.InAppPaymentObserver
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.databaseprotos.DonationErrorValue
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore

/**
 * Handles displaying bottom sheets for in-app payments. The current policy is to "fire and forget".
 */
class InAppPaymentsBottomSheetDelegate(
  private val fragmentManager: FragmentManager,
  private val lifecycleOwner: LifecycleOwner
) : DefaultLifecycleObserver {

  companion object {

    private val inAppPaymentProcessingErrors = listOf(
      InAppPaymentData.Error.Type.PAYMENT_PROCESSING,
      InAppPaymentData.Error.Type.STRIPE_FAILURE,
      InAppPaymentData.Error.Type.STRIPE_CODED_ERROR,
      InAppPaymentData.Error.Type.STRIPE_DECLINED_ERROR,
      InAppPaymentData.Error.Type.PAYPAL_CODED_ERROR,
      InAppPaymentData.Error.Type.PAYPAL_DECLINED_ERROR
    )
  }

  private val lifecycleDisposable = LifecycleDisposable().apply {
    bindTo(lifecycleOwner)
  }

  private val badgeRepository = TerminalDonationRepository()

  /**
   * Fires while resumed whenever an in-app payment changes, so terminal payments (e.g. a redirect/iDEAL
   * payment that finishes redeeming after we've already resumed) surface their sheet live rather than
   * waiting for the next resume.
   */
  private val inAppPaymentObserver = InAppPaymentObserver {
    handleInAppPaymentDonationSheets()
    handleInAppPaymentBackupsSheets()
  }

  override fun onResume(owner: LifecycleOwner) {
    handleLegacyTerminalDonationSheets()
    handleLegacyVerifiedMonthlyDonationSheets()
    handleInAppPaymentDonationSheets()
    handleInAppPaymentBackupsSheets()

    AppDependencies.databaseObserver.registerInAppPaymentObserver(inAppPaymentObserver)
  }

  override fun onPause(owner: LifecycleOwner) {
    AppDependencies.databaseObserver.unregisterObserver(inAppPaymentObserver)
  }

  /**
   * Handles terminal donations consumed from the InAppPayments values. These are only ever set by the legacy jobs,
   * and will be completely removed close to when the jobs are removed. (We might want an additional 90 days?)
   */
  private fun handleLegacyTerminalDonationSheets() {
    val donations = REDStore.inAppPayments.consumeTerminalDonations()
    for (donation in donations) {
      if (donation.isLongRunningPaymentMethod && (donation.error == null || donation.error.type != DonationErrorValue.Type.REDEMPTION)) {
        TerminalDonationBottomSheet.show(fragmentManager, donation)
      } else if (donation.error != null) {
        lifecycleDisposable += badgeRepository.getBadge(donation).observeOn(AndroidSchedulers.mainThread()).subscribe { badge ->
          ThanksForYourSupportBottomSheetDialogFragment.create(badge).show(fragmentManager, ThanksForYourSupportBottomSheetDialogFragment.SHEET_TAG)
        }
      }
    }
  }

  /**
   * Handles the 'verified' sheet that appears after a user externally verifies a payment and returns to the application.
   * These are only ever set by the legacy jobs, and will be completely removed close to when the jobs are removed. (We might
   * want an additional 90 days?)
   */
  private fun handleLegacyVerifiedMonthlyDonationSheets() {
    REDStore.inAppPayments.consumeVerifiedSubscription3DSData()?.also {
      DonationPendingBottomSheet().apply {
        arguments = DonationPendingBottomSheetArgs.Builder(it.inAppPayment.id).build().toBundle()
      }.show(fragmentManager, null)
    }
  }

  /**
   * Handles the new in-app payment sheets for donations.
   */
  private fun handleInAppPaymentDonationSheets() {
    lifecycleDisposable += Single.fromCallable {
      REDDatabase.inAppPayments.consumeDonationPaymentsToNotifyUser()
    }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeBy { inAppPayments ->
      for (payment in inAppPayments) {
        if (payment.data.error == null && payment.state == InAppPaymentTable.State.END) {
          ThanksForYourSupportBottomSheetDialogFragment()
            .apply { arguments = ThanksForYourSupportBottomSheetDialogFragmentArgs.Builder(Badges.fromDatabaseBadge(payment.data.badge!!)).build().toBundle() }
            .show(fragmentManager, null)
        } else if (payment.data.error != null && payment.state == InAppPaymentTable.State.PENDING) {
          DonationPendingBottomSheet().apply {
            arguments = DonationPendingBottomSheetArgs.Builder(payment.id).build().toBundle()
          }.show(fragmentManager, null)
        } else if (isUnexpectedCancellation(payment.state, payment.data) && REDStore.inAppPayments.showMonthlyDonationCanceledDialog) {
          MonthlyDonationCanceledBottomSheetDialogFragment.show(fragmentManager)
        }
      }
    }
  }

  /**
   * Handles the new in-app payment sheets for backups.
   */
  private fun handleInAppPaymentBackupsSheets() {
    lifecycleDisposable += Single.fromCallable {
      REDDatabase.inAppPayments.consumeBackupPaymentsToNotifyUser()
    }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeBy { inAppPayments ->
      for (payment in inAppPayments) {
        if (isPaymentProcessingError(payment.state, payment.data)) {
          BackupAlertBottomSheet.create(BackupAlert.FailedToRenew).show(fragmentManager, null)
        } else if (isUnexpectedCancellation(payment.state, payment.data)) {
          BackupAlertBottomSheet.create(BackupAlert.MediaBackupsAreOff(payment.endOfPeriodSeconds)).show(fragmentManager, null)
        }
      }
    }
  }

  private fun isUnexpectedCancellation(inAppPaymentState: InAppPaymentTable.State, inAppPaymentData: InAppPaymentData): Boolean {
    return inAppPaymentState == InAppPaymentTable.State.END && inAppPaymentData.error != null && inAppPaymentData.cancellation != null && inAppPaymentData.cancellation.reason != InAppPaymentData.Cancellation.Reason.MANUAL
  }

  private fun isPaymentProcessingError(inAppPaymentState: InAppPaymentTable.State, inAppPaymentData: InAppPaymentData): Boolean {
    return inAppPaymentState == InAppPaymentTable.State.END && inAppPaymentData.error != null && (inAppPaymentData.error.type in inAppPaymentProcessingErrors)
  }
}
