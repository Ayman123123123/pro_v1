/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.migrations

import android.app.Application
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.JobManager
import com.red.sovereign.jobs.BackupMessagesJob
import com.red.sovereign.testutil.MockAppDependenciesRule
import com.red.sovereign.testutil.MockREDStoreRule
import com.red.sovereign.testutil.SystemOutLogger
import kotlin.time.Duration.Companion.days

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LocalArchiveReconciliationMigrationJobTest {

  @get:Rule
  val mockREDStore = MockREDStoreRule()

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var jobManager: JobManager
  private lateinit var attachments: AttachmentTable

  @Before
  fun setUp() {
    Log.initialize(SystemOutLogger())
    jobManager = AppDependencies.jobManager

    attachments = mockk(relaxed = true)
    mockkObject(REDDatabase.Companion)
    every { REDDatabase.attachments } returns attachments
  }

  @After
  fun tearDown() {
    unmockkObject(REDDatabase.Companion)
  }

  @Test
  fun givenUserDoesNotBackUpMedia_whenIRunMigration_thenIResetLocalBackupMediaAndDoNotEnqueueReconciliation() {
    every { mockREDStore.backup.backsUpMedia } returns false

    LocalArchiveReconciliationMigrationJob().run()

    verify(exactly = 1) { attachments.resetArchiveTransferStateForLocalBackupMedia() }
    verify(exactly = 0) { jobManager.startChain(any<BackupMessagesJob>()) }
  }

  @Test
  fun givenMediaBackupUserNotInBadState_whenIRunMigration_thenIDoNotEnqueueReconciliation() {
    every { mockREDStore.backup.backsUpMedia } returns true
    every { attachments.hasArchiveFinishedLocalBackupMedia() } returns false

    LocalArchiveReconciliationMigrationJob().run()

    verify(exactly = 0) { jobManager.startChain(any<BackupMessagesJob>()) }
  }

  @Test
  fun givenMediaBackupUserInBadState_whenIRunMigration_thenIEnqueueReconciliation() {
    every { mockREDStore.backup.backsUpMedia } returns true
    every { attachments.hasArchiveFinishedLocalBackupMedia() } returns true

    LocalArchiveReconciliationMigrationJob().run()

    verify(exactly = 1) { mockREDStore.backup.localRestoreReconcilePending = true }
    verify(exactly = 1) { jobManager.startChain(any<BackupMessagesJob>()) }
  }

  /**
   * Guards against reintroducing a registration-age skip. A user who restored from a local backup and only later enabled remote backups may have registered
   * long ago yet never had a reconciliation heal them, so being in the bad state must expedite reconciliation regardless of how long ago they registered.
   */
  @Test
  fun givenMediaBackupUserInBadStateWhoRegisteredLongAgo_whenIRunMigration_thenIStillEnqueueReconciliation() {
    every { mockREDStore.backup.backsUpMedia } returns true
    every { mockREDStore.account.registeredAtTimestamp } returns System.currentTimeMillis() - 365.days.inWholeMilliseconds
    every { attachments.hasArchiveFinishedLocalBackupMedia() } returns true

    LocalArchiveReconciliationMigrationJob().run()

    verify(exactly = 1) { jobManager.startChain(any<BackupMessagesJob>()) }
  }
}
