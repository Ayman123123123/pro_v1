package com.red.sovereign.components.settings.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import com.red.sovereign.components.settings.app.subscription.InAppDonations
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository
import com.red.sovereign.conversationlist.model.UnreadPaymentsLiveData
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.util.TextSecurePreferences
import com.red.sovereign.util.livedata.Store

class AppSettingsViewModel : ViewModel() {

  private val store = Store(
    AppSettingsState(
      isPrimaryDevice = REDStore.account.isPrimaryDevice,
      unreadPaymentsCount = 0,
      hasExpiredGiftBadge = REDStore.inAppPayments.getExpiredGiftBadge() != null,
      allowUserToGoToDonationManagementScreen = REDStore.inAppPayments.isLikelyASustainer() || InAppDonations.hasAtLeastOnePaymentMethodAvailable(),
      userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !REDStore.account.isRegistered,
      clientDeprecated = REDStore.misc.isClientDeprecated
    )
  )

  private val unreadPaymentsLiveData = UnreadPaymentsLiveData()
  private val disposables = CompositeDisposable()

  val state: LiveData<AppSettingsState> = store.stateLiveData
  val self: LiveData<BioRecipientState> = Recipient.self().live().liveData.map { BioRecipientState(it) }

  init {
    store.update(unreadPaymentsLiveData) { payments, state -> state.copy(unreadPaymentsCount = payments.map { it.unreadCount }.orElse(0)) }

    disposables += RecurringInAppPaymentRepository.getActiveSubscription(InAppPaymentSubscriberRecord.Type.DONATION).subscribeBy(
      onSuccess = { activeSubscription ->
        store.update { state ->
          state.copy(allowUserToGoToDonationManagementScreen = REDStore.account.isRegistered && (activeSubscription.isActive || InAppDonations.hasAtLeastOnePaymentMethodAvailable()))
        }
      },
      onError = {}
    )
  }

  override fun onCleared() {
    disposables.clear()
  }

  fun refreshDeprecatedOrUnregistered() {
    store.update {
      it.copy(
        clientDeprecated = REDStore.misc.isClientDeprecated,
        userUnregistered = TextSecurePreferences.isUnauthorizedReceived(AppDependencies.application) || !REDStore.account.isRegistered
      )
    }
  }

  fun refresh() {
    store.update {
      it.copy(
        hasExpiredGiftBadge = REDStore.inAppPayments.getExpiredGiftBadge() != null,
        backupFailureState = getBackupFailureState()
      )
    }
  }

  private fun getBackupFailureState(): BackupFailureState {
    return when {
      !REDStore.account.isRegistered || !REDStore.backup.areBackupsEnabled -> BackupFailureState.NONE
      REDStore.backup.isNotEnoughRemoteStorageSpace -> BackupFailureState.OUT_OF_STORAGE_SPACE
      REDStore.backup.hasBackupCreationError -> BackupFailureState.COULD_NOT_COMPLETE_BACKUP
      REDStore.backup.subscriptionStateMismatchDetected -> BackupFailureState.SUBSCRIPTION_STATE_MISMATCH
      REDStore.backup.hasBackupAlreadyRedeemedError -> BackupFailureState.ALREADY_REDEEMED
      else -> BackupFailureState.NONE
    }
  }
}
