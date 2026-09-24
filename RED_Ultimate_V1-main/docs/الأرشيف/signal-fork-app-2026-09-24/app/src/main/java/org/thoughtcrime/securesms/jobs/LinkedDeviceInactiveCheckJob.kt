/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import org.signal.core.util.crypto.DeviceName
import org.signal.core.util.crypto.DeviceNameCipher
import org.signal.core.util.logging.Log
import org.signal.core.util.roundedString
import org.signal.libsignal.net.RequestResult
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.CoroutineJob
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.protos.LeastActiveLinkedDevice
import org.whispersystems.signalservice.api.push.REDServiceAddress
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Designed as a routine check to keep an eye on how active our linked devices are.
 */
class LinkedDeviceInactiveCheckJob private constructor(
  parameters: Parameters = Parameters.Builder()
    .setQueue("LinkedDeviceInactiveCheckJob")
    .setMaxInstancesForFactory(2)
    .setLifespan(30.days.inWholeMilliseconds)
    .setMaxAttempts(Parameters.UNLIMITED)
    .addConstraint(NetworkConstraint.KEY)
    .build()
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(LinkedDeviceInactiveCheckJob::class.java)
    const val KEY = "LinkedDeviceInactiveCheckJob"

    @JvmStatic
    fun enqueue() {
      AppDependencies.jobManager.add(LinkedDeviceInactiveCheckJob())
    }

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!REDStore.account.isRegistered) {
        Log.i(TAG, "Not registered, skipping enqueue.")
        return
      }

      val timeSinceLastCheck = System.currentTimeMillis() - REDStore.misc.linkedDeviceLastActiveCheckTime
      if (timeSinceLastCheck > 1.days.inWholeMilliseconds || timeSinceLastCheck < 0) {
        AppDependencies.jobManager.add(LinkedDeviceInactiveCheckJob())
      }
    }
  }

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    if (!REDStore.account.isRegistered) {
      Log.i(TAG, "Not registered, skipping.")
      return Result.success()
    }

    if (REDStore.account.isLinkedDevice) {
      Log.i(TAG, "Not primary, skipping")
      return Result.success()
    }

    val devices = when (val result = AppDependencies.linkDeviceApi.getDevices()) {
      is RequestResult.Success -> result.result.filter { it.id != REDServiceAddress.DEFAULT_DEVICE_ID }
      is RequestResult.RetryableNetworkError -> return Result.retry(defaultBackoff())
      is RequestResult.ApplicationError -> throw result.cause
      is RequestResult.NonSuccess -> error("Code branch is unreachable")
    }

    if (devices.isEmpty()) {
      Log.i(TAG, "No linked devices found.")

      REDStore.account.isMultiDevice = false
      REDStore.misc.leastActiveLinkedDevice = null
      REDStore.misc.linkedDeviceLastActiveCheckTime = System.currentTimeMillis()

      return Result.success()
    }

    val leastActiveDevice: LeastActiveLinkedDevice? = devices
      .filter { it.encryptedName.isNotEmpty() }
      .minByOrNull { it.lastSeen }
      ?.let {
        val nameProto = DeviceName.ADAPTER.decode(it.encryptedName)
        val decryptedBytes = DeviceNameCipher.decryptDeviceName(nameProto, AppDependencies.protocolStore.aci().identityKeyPair) ?: return@let null
        val name = String(decryptedBytes)

        LeastActiveLinkedDevice(
          name = name,
          lastActiveTimestamp = it.lastSeen.toEpochMilli()
        )
      }

    if (leastActiveDevice == null) {
      Log.w(TAG, "Failed to decrypt linked device name.")
      REDStore.account.isMultiDevice = true
      REDStore.misc.leastActiveLinkedDevice = null
      REDStore.misc.linkedDeviceLastActiveCheckTime = System.currentTimeMillis()
      return Result.success()
    }

    val timeSinceActive = System.currentTimeMillis() - leastActiveDevice.lastActiveTimestamp
    Log.i(TAG, "Least active linked device was last active ${timeSinceActive.milliseconds.toDouble(DurationUnit.DAYS).roundedString(2)} days ago ($timeSinceActive ms).")

    REDStore.account.isMultiDevice = true
    REDStore.misc.leastActiveLinkedDevice = leastActiveDevice
    REDStore.misc.linkedDeviceLastActiveCheckTime = System.currentTimeMillis()

    return Result.success()
  }

  override fun onFailure() {
  }

  class Factory : Job.Factory<LinkedDeviceInactiveCheckJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LinkedDeviceInactiveCheckJob {
      return LinkedDeviceInactiveCheckJob(parameters)
    }
  }
}
