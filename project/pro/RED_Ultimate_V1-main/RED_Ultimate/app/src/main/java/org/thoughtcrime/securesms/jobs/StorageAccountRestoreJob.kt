package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import org.signal.network.exceptions.PushNetworkException
import org.signal.network.service.StorageServiceService
import org.signal.network.service.StorageServiceService.ManifestResult
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.net.REDNetwork
import com.red.sovereign.profiles.manage.UsernameRepository.reclaimUsernameIfNecessary
import com.red.sovereign.recipients.Recipient.Companion.self
import com.red.sovereign.storage.StorageSyncHelper
import org.whispersystems.signalservice.api.storage.REDAccountRecord
import org.whispersystems.signalservice.api.storage.REDStorageManifest
import org.whispersystems.signalservice.api.storage.REDStorageRecord
import java.util.concurrent.TimeUnit

/**
 * Restored the AccountRecord present in the storage service, if any. This will overwrite any local
 * data that is stored in AccountRecord, so this should only be done immediately after registration.
 */
class StorageAccountRestoreJob private constructor(parameters: Parameters) : BaseJob(parameters) {
  companion object {
    const val KEY: String = "StorageAccountRestoreJob"

    val LIFESPAN: Long = TimeUnit.SECONDS.toMillis(20)

    private val TAG = Log.tag(StorageAccountRestoreJob::class.java)
  }

  constructor() : this(
    Parameters.Builder()
      .setGlobalPriority(Parameters.PRIORITY_HIGH)
      .setQueue(StorageSyncJob.QUEUE_KEY)
      .addConstraint(NetworkConstraint.KEY)
      .setMaxInstancesForFactory(1)
      .setMaxAttempts(1)
      .setLifespan(LIFESPAN)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  @Throws(Exception::class)
  override fun onRun() {
    val storageServiceKey = REDStore.storageService.storageKeyForInitialDataRestore?.let {
      Log.i(TAG, "Using temporary storage key.")
      it
    } ?: run {
      Log.i(TAG, "Using normal storage key.")
      REDStore.storageService.storageKey
    }

    val repository = StorageServiceService(REDNetwork.storageService)

    Log.i(TAG, "Retrieving manifest...")
    val manifest: REDStorageManifest? = when (val result = repository.getStorageManifest(storageServiceKey)) {
      is ManifestResult.Success -> result.manifest
      is ManifestResult.DecryptionError -> null
      is ManifestResult.NotFoundError -> null
      is ManifestResult.NetworkError -> throw result.exception
      is ManifestResult.StatusCodeError -> throw result.exception
    }

    if (manifest == null) {
      Log.w(TAG, "Manifest did not exist or was undecryptable (bad key). Not restoring. Force-pushing.")
      AppDependencies.jobManager.add(StorageForcePushJob())
      return
    }

    Log.i(TAG, "Resetting the local manifest to an empty state so that it will sync later.")
    REDStore.storageService.manifest = REDStorageManifest.EMPTY

    val accountId = manifest.accountStorageId

    if (!accountId.isPresent) {
      Log.w(TAG, "Manifest had no account record! Not restoring.")
      return
    }

    Log.i(TAG, "Retrieving account record...")
    val records: List<REDStorageRecord> = when (val result = repository.readStorageRecords(storageServiceKey, manifest.recordIkm, listOf(accountId.get()))) {
      is StorageServiceService.StorageRecordResult.Success -> result.records
      is StorageServiceService.StorageRecordResult.DecryptionError -> {
        Log.w(TAG, "Account record was undecryptable. Not restoring. Force-pushing.")
        AppDependencies.jobManager.add(StorageForcePushJob())
        return
      }
      is StorageServiceService.StorageRecordResult.NetworkError -> throw result.exception
      is StorageServiceService.StorageRecordResult.StatusCodeError -> throw result.exception
    }

    val record = if (records.isNotEmpty()) records[0] else null

    if (record == null) {
      Log.w(TAG, "Could not find account record, even though we had an ID! Not restoring.")
      return
    }

    if (record.proto.account == null) {
      Log.w(TAG, "The storage record didn't actually have an account on it! Not restoring.")
      return
    }

    val accountRecord = REDAccountRecord(record.id, record.proto.account!!)

    Log.i(TAG, "Applying changes locally...")
    REDDatabase.rawDatabase.beginTransaction()
    try {
      StorageSyncHelper.applyAccountStorageSyncUpdates(context, self().fresh(), accountRecord, false)
      REDDatabase.rawDatabase.setTransactionSuccessful()
    } finally {
      REDDatabase.rawDatabase.endTransaction()
    }

    // We will try to reclaim the username here, as early as possible, but the registration flow also enqueues a username restore job,
    // so failing here isn't a huge deal
    if (REDStore.account.username != null) {
      Log.i(TAG, "Attempting to reclaim username...")
      val result = reclaimUsernameIfNecessary()
      Log.i(TAG, "Username reclaim result: " + result.name)
    } else {
      Log.i(TAG, "No username to reclaim.")
    }

    if (accountRecord.proto.avatarUrlPath.isNotEmpty()) {
      Log.i(TAG, "Fetching avatar...")
      val state = AppDependencies.jobManager.runSynchronously(RetrieveProfileAvatarJob.forAccountRestore(self(), accountRecord.proto.avatarUrlPath, true), LIFESPAN / 2)

      if (state.isPresent) {
        Log.i(TAG, "Avatar retrieved successfully. ${state.get()}")
      } else {
        Log.w(TAG, "Avatar retrieval did not complete in time (or otherwise failed).")
      }
    } else {
      Log.i(TAG, "No avatar present. Not fetching.")
    }

    Log.i(TAG, "Refreshing attributes...")
    val state = AppDependencies.jobManager.runSynchronously(RefreshAttributesJob.forAccountRestore(), LIFESPAN / 2)

    if (state.isPresent) {
      Log.i(TAG, "Attributes refreshed successfully. ${state.get()}")
    } else {
      Log.w(TAG, "Attribute refresh did not complete in time (or otherwise failed).")
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is PushNetworkException
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<StorageAccountRestoreJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): StorageAccountRestoreJob {
      return StorageAccountRestoreJob(parameters)
    }
  }
}
