package com.red.sovereign.components.settings.app.subscription

import androidx.annotation.CheckResult
import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.donations.PaymentSourceType
import org.signal.network.NetworkResult
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.badges.Badges
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.requireSubscriberType
import com.red.sovereign.components.settings.app.subscription.InAppPaymentsRepository.toPaymentSourceType
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository.cancelActiveSubscriptionIfNecessarySync
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository.cancelActiveSubscriptionSync
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository.getActiveSubscriptionSync
import com.red.sovereign.components.settings.app.subscription.RecurringInAppPaymentRepository.rotateSubscriberIdSync
import com.red.sovereign.database.InAppPaymentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.InAppPaymentSubscriberRecord
import com.red.sovereign.database.model.databaseprotos.InAppPaymentData
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.InAppPaymentKeepAliveJob
import com.red.sovereign.jobs.InAppPaymentRecurringContextJob
import com.red.sovereign.jobs.MultiDeviceSubscriptionSyncRequestJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.subscription.LevelUpdate
import com.red.sovereign.subscription.LevelUpdateOperation
import com.red.sovereign.subscription.Subscription
import org.whispersystems.signalservice.api.storage.IAPSubscriptionId
import org.whispersystems.signalservice.api.subscriptions.ActiveSubscription
import org.whispersystems.signalservice.api.subscriptions.IdempotencyKey
import org.whispersystems.signalservice.api.subscriptions.SubscriberId
import org.whispersystems.signalservice.internal.EmptyResponse
import org.whispersystems.signalservice.internal.ServiceResponse
import org.whispersystems.signalservice.internal.push.SubscriptionsConfiguration
import java.math.BigDecimal
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared methods for operating on recurring subscriptions, shared between donations and backups.
 */
object RecurringInAppPaymentRepository {

  private val TAG = Log.tag(RecurringInAppPaymentRepository::class.java)

  private val donationsService = AppDependencies.donationsService

  /**
   * Passthrough Rx wrapper for [getActiveSubscriptionSync] dispatching on io thread-pool.
   */
  @CheckResult
  fun getActiveSubscription(type: InAppPaymentSubscriberRecord.Type): Single<ActiveSubscription> {
    return Single.fromCallable {
      getActiveSubscriptionSync(type).successOrThrow()
    }.subscribeOn(Schedulers.io())
  }

  /** A fake paid subscription to return when the backup tier override is set. */
  private val MOCK_PAID_SUBSCRIPTION = ActiveSubscription(
    ActiveSubscription.Subscription(
      SubscriptionsConfiguration.BACKUPS_LEVEL,
      "USD",
      BigDecimal(42),
      2147472000,
      true,
      2147472000,
      false,
      "active",
      "USA",
      "credit-card",
      false
    ),
    null
  )

  /**
   * Gets the active subscription if it exists for the given [InAppPaymentSubscriberRecord.Type]
   */
  @WorkerThread
  fun getActiveSubscriptionSync(type: InAppPaymentSubscriberRecord.Type): NetworkResult<ActiveSubscription> {
    if (type == InAppPaymentSubscriberRecord.Type.BACKUP && REDStore.backup.backupTierInternalOverride == MessageBackupTier.PAID) {
      Log.d(TAG, "Returning mock paid subscription.")
      return NetworkResult.Success(MOCK_PAID_SUBSCRIPTION)
    }

    val response = InAppPaymentsRepository.getSubscriber(type)?.let {
      donationsService.getSubscription(it.subscriberId)
    } ?: return NetworkResult.Success(ActiveSubscription.EMPTY)

    response.result.ifPresent { result ->
      val lastEndOfPeriod = REDDatabase.inAppPayments.getByLatestEndOfPeriod(type.inAppPaymentType)?.endOfPeriodSeconds ?: 0L
      if (result.isActive && result.activeSubscription.endOfCurrentPeriod > lastEndOfPeriod) {
        InAppPaymentKeepAliveJob.enqueueAndTrackTime(System.currentTimeMillis().milliseconds)
      }
    }

    return response.toNetworkResult()
  }

  /**
   * Gets a list of subscriptions available via the donations configuration.
   */
  @CheckResult
  fun getSubscriptions(): Single<List<Subscription>> {
    return Single
      .fromCallable { donationsService.getDonationsConfiguration(Locale.getDefault()) }
      .subscribeOn(Schedulers.io())
      .timeout(InAppPaymentsRepository.DONATIONS_CONFIGURATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
      .flatMap { it.flattenResult() }
      .map { config ->
        config.getSubscriptionLevels().map { (level, levelConfig) ->
          Subscription(
            id = level.toString(),
            level = level,
            badge = Badges.fromServiceBadge(levelConfig.badge),
            prices = config.getSubscriptionAmounts(level)
          )
        }
      }
  }

  /**
   * Syncs the user account record, dispatches on the io thread-pool
   */
  @CheckResult
  fun syncAccountRecord(): Completable {
    return Completable.fromAction {
      REDDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Since PayPal and Stripe can't interoperate, we need to be able to rotate the subscriber ID
   * in case of failures.
   */
  @WorkerThread
  fun rotateSubscriberIdSync(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Rotating SubscriberId due to alternate payment processor...", true)
    if (InAppPaymentsRepository.getSubscriber(subscriberType) != null) {
      cancelActiveSubscriptionSync(subscriberType)
      updateLocalSubscriptionStateAndScheduleDataSync(subscriberType)
    }

    ensureSubscriberIdSync(subscriberType, isRotation = true)
  }

  /**
   * Passthrough Rx wrapper for [rotateSubscriberIdSync] dispatching on io thread-pool.
   */
  @CheckResult
  fun rotateSubscriberId(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable.fromAction {
      rotateSubscriberIdSync(subscriberType)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Ensures that the given [InAppPaymentSubscriberRecord.Type] has a [SubscriberId] that has been sent to the RED Service.
   * Will also record and synchronize this data with storage sync.
   */
  @WorkerThread
  fun ensureSubscriberIdSync(subscriberType: InAppPaymentSubscriberRecord.Type, isRotation: Boolean = false, iapSubscriptionId: IAPSubscriptionId? = null) {
    Log.d(TAG, "Ensuring SubscriberId for type $subscriberType exists on RED service {isRotation?$isRotation}...", true)

    val subscriberId = if (isRotation) {
      SubscriberId.generate()
    } else {
      InAppPaymentsRepository.getSubscriber(subscriberType)?.subscriberId ?: SubscriberId.generate()
    }

    donationsService.createSubscriber(subscriberId).resultOrThrow

    Log.d(TAG, "Successfully set SubscriberId exists on RED service.", true)

    InAppPaymentsRepository.setSubscriber(
      InAppPaymentSubscriberRecord(
        subscriberId = subscriberId,
        currency = if (subscriberType == InAppPaymentSubscriberRecord.Type.DONATION) {
          REDStore.inAppPayments.getRecurringDonationCurrency()
        } else {
          null
        },
        type = subscriberType,
        requiresCancel = false,
        paymentMethodType = if (subscriberType == InAppPaymentSubscriberRecord.Type.BACKUP) {
          InAppPaymentData.PaymentMethodType.GOOGLE_PLAY_BILLING
        } else {
          InAppPaymentData.PaymentMethodType.UNKNOWN
        },
        iapSubscriptionId = iapSubscriptionId
      )
    )

    REDDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  /**
   * Cancels the active subscription via the RED service.
   */
  @WorkerThread
  fun cancelActiveSubscriptionSync(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Canceling active subscription...", true)
    val localSubscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)

    val serviceResponse: ServiceResponse<EmptyResponse> = donationsService.cancelSubscription(localSubscriber.subscriberId)
    serviceResponse.resultOrThrow

    Log.d(TAG, "Cancelled active subscription.", true)
    REDStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
    MultiDeviceSubscriptionSyncRequestJob.enqueue()
    InAppPaymentsRepository.scheduleSyncForAccountRecordChange()
  }

  /**
   * Passthrough Rx wrapper for [cancelActiveSubscriptionSync] dispatching on io thread-pool.
   */
  @CheckResult
  fun cancelActiveSubscription(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable
      .fromAction { cancelActiveSubscriptionSync(subscriberType) }
      .subscribeOn(Schedulers.io())
  }

  /**
   * If the subscriber of the given type has been marked as "requires cancel", this method will perform the cancellation and
   * sync the appropriate data.
   */
  @WorkerThread
  fun cancelActiveSubscriptionIfNecessarySync(subscriberType: InAppPaymentSubscriberRecord.Type) {
    val shouldCancel = InAppPaymentsRepository.getShouldCancelSubscriptionBeforeNextSubscribeAttempt(subscriberType)
    if (shouldCancel) {
      cancelActiveSubscriptionSync(subscriberType)
      REDStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
      MultiDeviceSubscriptionSyncRequestJob.enqueue()
    }
  }

  /**
   * Passthrough Rx wrapper for [cancelActiveSubscriptionIfNecessarySync] dispatching on io thread-pool.
   */
  @CheckResult
  fun cancelActiveSubscriptionIfNecessary(subscriberType: InAppPaymentSubscriberRecord.Type): Completable {
    return Completable.fromAction {
      cancelActiveSubscriptionIfNecessarySync(subscriberType)
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Passthrough Rx wrapper for [InAppPaymentsRepository.getLatestPaymentMethodType] dispatching on io thread-pool.
   */
  @CheckResult
  fun getPaymentSourceTypeOfLatestSubscription(subscriberType: InAppPaymentSubscriberRecord.Type): Single<PaymentSourceType> {
    return Single.fromCallable {
      InAppPaymentsRepository.getLatestPaymentMethodType(subscriberType).toPaymentSourceType()
    }.subscribeOn(Schedulers.io())
  }

  /**
   * Sets the subscription level as per the data in the InAppPayment.
   *
   * This method mutates the [InAppPaymentTable.InAppPayment] and thus returns a new instance.
   */
  @CheckResult
  @WorkerThread
  fun setSubscriptionLevelSync(inAppPayment: InAppPaymentTable.InAppPayment): InAppPaymentTable.InAppPayment {
    val subscriptionLevel = inAppPayment.data.level.toString()
    val subscriberType = inAppPayment.type.requireSubscriberType()
    val subscriber = InAppPaymentsRepository.requireSubscriber(subscriberType)

    getOrCreateLevelUpdateOperation(TAG, subscriptionLevel).use { operation ->
      REDDatabase.inAppPayments.update(
        inAppPayment = inAppPayment.copy(
          subscriberId = subscriber.subscriberId,
          data = inAppPayment.data.newBuilder().redemption(
            redemption = InAppPaymentData.RedemptionState(
              stage = InAppPaymentData.RedemptionState.Stage.INIT
            )
          ).build()
        )
      )

      Log.d(TAG, "Attempting to set user subscription level to $subscriptionLevel", true)

      val response = AppDependencies.donationsService.updateSubscriptionLevel(
        subscriber.subscriberId,
        subscriptionLevel,
        subscriber.currency!!.currencyCode,
        operation.idempotencyKey.serialize(),
        subscriberType.lock
      )

      if (response.status == 200 || response.status == 204) {
        Log.d(TAG, "Successfully set user subscription to level $subscriptionLevel with response code ${response.status}", true)
        REDStore.inAppPayments.updateLocalStateForLocalSubscribe(subscriberType)
        MultiDeviceSubscriptionSyncRequestJob.enqueue()
        syncAccountRecord().subscribe()
      } else {
        if (response.applicationError.isPresent) {
          Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel with response code ${response.status}", response.applicationError.get(), true)
          REDStore.inAppPayments.clearLevelOperations()
        } else {
          Log.w(TAG, "Failed to set user subscription to level $subscriptionLevel", response.executionError.orElse(null), true)
        }

        response.resultOrThrow
        error("Should never get here.")
      }
    }

    Log.d(TAG, "Enqueuing request response job chain.", true)
    val freshPayment = REDDatabase.inAppPayments.getById(inAppPayment.id)!!
    InAppPaymentRecurringContextJob.createJobChain(freshPayment).enqueue()

    return freshPayment
  }

  /**
   * Get or create a [LevelUpdateOperation]
   *
   * This allows us to ensure the same idempotency key is used across multiple attempts for the same level.
   */
  fun getOrCreateLevelUpdateOperation(tag: String, subscriptionLevel: String): LevelUpdateOperation {
    Log.d(tag, "Retrieving level update operation for $subscriptionLevel")
    val levelUpdateOperation = REDStore.inAppPayments.getLevelOperation(subscriptionLevel)
    return if (levelUpdateOperation == null) {
      val newOperation = LevelUpdateOperation(
        idempotencyKey = IdempotencyKey.generate(),
        level = subscriptionLevel
      )

      REDStore.inAppPayments.setLevelOperation(newOperation)
      LevelUpdate.updateProcessingState(true)
      Log.d(tag, "Created a new operation for $subscriptionLevel")
      newOperation
    } else {
      LevelUpdate.updateProcessingState(true)
      Log.d(tag, "Reusing operation for $subscriptionLevel")
      levelUpdateOperation
    }
  }

  /**
   * Update local state information and schedule a storage sync for the change. This method
   * assumes you've already properly called the DELETE method for the stored ID on the server.
   */
  @WorkerThread
  private fun updateLocalSubscriptionStateAndScheduleDataSync(subscriberType: InAppPaymentSubscriberRecord.Type) {
    Log.d(TAG, "Marking subscription cancelled...", true)
    REDStore.inAppPayments.updateLocalStateForManualCancellation(subscriberType)
    MultiDeviceSubscriptionSyncRequestJob.enqueue()
    REDDatabase.recipients.markNeedsSync(Recipient.self().id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }
}
