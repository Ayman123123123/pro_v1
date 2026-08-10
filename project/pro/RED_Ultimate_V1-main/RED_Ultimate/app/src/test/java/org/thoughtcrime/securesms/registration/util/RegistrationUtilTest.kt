/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.registration.util

import android.app.Application
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log.initialize
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.keyvalue.PhoneNumberPrivacyValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.Skipped
import com.red.sovereign.keyvalue.Start
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.testutil.LogRecorder
import com.red.sovereign.testutil.MockAppDependenciesRule
import com.red.sovereign.testutil.MockREDStoreRule
import com.red.sovereign.util.RemoteConfig

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE)
class RegistrationUtilTest {
  @get:Rule
  val signalStore = MockREDStoreRule(relaxed = setOf(PhoneNumberPrivacyValues::class))

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var logRecorder: LogRecorder

  @Before
  fun setup() {
    mockkObject(Recipient)
    mockkStatic(RemoteConfig::class)

    logRecorder = LogRecorder()
    initialize(logRecorder)

    every { REDStore.backup.backupTier } returns null
    every { REDStore.backup.backupsInitialized = any() } answers { }
    every { REDStore.backup.cachedMediaCdnPath = any() } answers { }
    every { REDStore.backup.mediaCredentials } returns mockk {
      every { clearAll() } answers {}
    }
    every { REDStore.backup.messageCredentials } returns mockk {
      every { clearAll() } answers {}
    }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun maybeMarkRegistrationComplete_allValidWithRestoreOption() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasPin() } returns true
    every { signalStore.registration.restoreDecisionState } returns RestoreDecisionState.Skipped

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify { signalStore.registration.markRegistrationComplete() }
  }

  @Test
  fun maybeMarkRegistrationComplete_missingData() {
    every { signalStore.registration.isRegistrationComplete } returns false
    every { signalStore.account.isRegistered } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { signalStore.account.isRegistered } returns true
    every { Recipient.self() } returns Recipient(profileName = ProfileName.EMPTY)

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { Recipient.self() } returns Recipient(profileName = ProfileName.fromParts("Dark", "Helmet"))
    every { signalStore.svr.hasPin() } returns false
    every { signalStore.svr.hasOptedOut() } returns false
    every { signalStore.account.isLinkedDevice } returns false

    RegistrationUtil.maybeMarkRegistrationComplete()

    every { signalStore.svr.hasPin() } returns true
    every { signalStore.registration.restoreDecisionState } returns RestoreDecisionState.Start

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify(exactly = 0) { signalStore.registration.markRegistrationComplete() }

    val regUtilLogs = logRecorder.information.filter { it.tag == "RegistrationUtil" }
    assertThat(regUtilLogs).hasSize(4)
    assertThat(regUtilLogs)
      .extracting { it.message }
      .each { it.isEqualTo("Registration is not yet complete.") }
  }

  @Test
  fun maybeMarkRegistrationComplete_alreadyMarked() {
    every { signalStore.registration.isRegistrationComplete } returns true

    RegistrationUtil.maybeMarkRegistrationComplete()

    verify(exactly = 0) { signalStore.registration.markRegistrationComplete() }

    val regUtilLogs = logRecorder.information.filter { it.tag == "RegistrationUtil" }
    assertThat(regUtilLogs).isEmpty()
  }
}
