/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.jobs

import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.core.models.backup.MediaName
import org.signal.core.models.database.AttachmentId
import org.signal.core.util.Base64.decodeBase64OrThrow
import org.signal.network.NetworkResult
import com.red.sovereign.attachments.Attachment
import com.red.sovereign.attachments.PointerAttachment
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.backup.v2.MessageBackupTier
import com.red.sovereign.database.AttachmentTable
import com.red.sovereign.database.BackupMediaSnapshotTable.MediaEntry
import com.red.sovereign.database.MessageType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.testing.REDActivityRule
import com.red.sovereign.util.MediaUtil
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse
import org.whispersystems.signalservice.api.archive.ArchiveGetMediaItemsResponse.StoredMediaObject
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId
import java.io.ByteArrayInputStream
import java.util.Optional
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class ArchiveAttachmentReconciliationJobTest {

  @get:Rule
  val harness = REDActivityRule()

  @Before
  fun setUp() {
    REDStore.backup.backupTier = MessageBackupTier.PAID
    REDStore.backup.hasBackupBeenUploaded = true
    REDStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis()
    REDStore.backup.localRestoreReconcilePending = false

    mockkObject(BackupRepository)
    mockkObject(ArchiveCommitAttachmentDeletesJob)
    every { ArchiveCommitAttachmentDeletesJob.deleteMediaObjectsFromCdn(any(), any(), any(), any()) } returns null
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  /**
   * The core of the reconcile-first restore flow: a local restore resets everything to [AttachmentTable.ArchiveTransferState.NONE], so media that genuinely is
   * on the CDN must be promoted back to FINISHED during reconciliation -- otherwise the backfill would needlessly re-upload it. This only happens while
   * [localRestoreReconcilePending] is set, so it never runs in the common periodic reconciliation.
   */
  @Test
  fun givenLocalRestorePendingAndAttachmentOnCdn_whenIReconcile_thenIExpectItMarkedFinished() {
    REDStore.backup.localRestoreReconcilePending = true

    val attachmentId = seedFinalizedAttachment("remote-key-1".toByteArray(), byteArrayOf(1, 2, 3, 4, 5))
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  /**
   * Guards the reconcile-first promotion above: outside the local-restore flow (the common periodic reconciliation), NONE media that happens to be on the CDN is
   * left alone, so we don't do the expensive mark-finished scan in the common case.
   */
  @Test
  fun givenNoLocalRestorePendingAndNoneAttachmentOnCdn_whenIReconcile_thenItStaysNone() {
    val attachmentId = seedFinalizedAttachment("remote-key-common".toByteArray(), byteArrayOf(2, 3, 4, 5, 6))
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  @Test
  fun givenFinishedAttachmentMissingFromCdn_whenIReconcile_thenIExpectItResetToNone() {
    val attachmentId = seedFinalizedAttachment("remote-key-2".toByteArray(), byteArrayOf(6, 7, 8, 9, 10))
    REDDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  /**
   * The eventual safety net: an ordinary (non-forced) periodic reconciliation, run after the sync interval has elapsed, heals the bad state on its own -- media
   * in the snapshot but missing from the CDN is reset to [AttachmentTable.ArchiveTransferState.NONE] and a re-upload is enqueued -- with no help from the
   * migration or the reconcile-first flow.
   */
  @Test
  fun givenFinishedMediaMissingFromCdn_whenAnOrdinaryPeriodicReconciliationRuns_thenItHealsToNoneAndReUploads() {
    REDStore.backup.lastAttachmentReconciliationTime = System.currentTimeMillis() - 60.days.inWholeMilliseconds
    mockkObject(BackupMessagesJob)
    every { BackupMessagesJob.enqueue() } just Runs

    val attachmentId = seedFinalizedAttachment("remote-key-periodic".toByteArray(), byteArrayOf(1, 2, 3, 4, 5))
    REDDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = false).run()

    val healed = REDDatabase.attachments.getAttachment(attachmentId)!!
    assertThat(healed.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
    assertThat(healed.archiveCdn).isNull()
    verify(exactly = 1) { BackupMessagesJob.enqueue() }
  }

  /**
   * Reconciliation must only repair genuinely-broken state. Media that is actually present on the CDN stays [AttachmentTable.ArchiveTransferState.FINISHED], so
   * we never needlessly reset (and therefore re-upload) media that was correctly archived.
   */
  @Test
  fun givenFinishedMediaStillOnCdn_whenIReconcile_thenItStaysFinished() {
    val attachmentId = seedFinalizedAttachment("remote-key-on-cdn".toByteArray(), byteArrayOf(6, 7, 8, 9, 10))
    REDDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  /**
   * The other healing direction: media that is on the CDN but locally marked [AttachmentTable.ArchiveTransferState.NONE] and absent from the current snapshot is
   * treated as a delete-candidate. Before deleting, reconciliation confirms it's still referenced locally and recovers it to
   * [AttachmentTable.ArchiveTransferState.FINISHED] rather than deleting it from the CDN.
   */
  @Test
  fun givenNoneMediaOnCdnButNotInSnapshot_whenIReconcile_thenItIsRecoveredToFinished() {
    val attachmentId = seedFinalizedAttachment("remote-key-flow2".toByteArray(), byteArrayOf(11, 12, 13, 14, 15))
    REDDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.NONE)
    fakeCdnContains(attachmentId, cdn = 3)

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.FINISHED)
  }

  @Test
  fun givenFirstEverReconciliation_whenIForceIt_thenItStillRunsAndRepairs() {
    REDStore.backup.lastAttachmentReconciliationTime = -1

    val attachmentId = seedFinalizedAttachment("remote-key-3".toByteArray(), byteArrayOf(11, 12, 13, 14, 15))
    REDDatabase.attachments.setArchiveTransferState(attachmentId, AttachmentTable.ArchiveTransferState.FINISHED)
    commitSnapshotFor(attachmentId, cdn = 3)
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDDatabase.attachments.getAttachment(attachmentId)!!.archiveTransferState).isEqualTo(AttachmentTable.ArchiveTransferState.NONE)
  }

  @Test
  fun givenLocalRestoreReconcilePending_whenReconcileCompletes_thenIExpectFlagCleared() {
    REDStore.backup.localRestoreReconcilePending = true
    fakeCdnEmpty()

    ArchiveAttachmentReconciliationJob(forced = true).run()

    assertThat(REDStore.backup.localRestoreReconcilePending).isFalse()
  }

  private fun seedFinalizedAttachment(remoteKey: ByteArray, data: ByteArray): AttachmentId {
    val attachment = createAttachmentPointer(remoteKey, data.size)
    val messageResult = REDDatabase.messages.insertMessageInbox(createIncomingMessage(serverTime = 0.days, attachment = attachment)).get()
    val attachmentId = messageResult.insertedAttachments!![attachment]!!
    REDDatabase.attachments.setTransferState(messageResult.messageId, attachmentId, AttachmentTable.TRANSFER_PROGRESS_STARTED)
    REDDatabase.attachments.finalizeAttachmentAfterDownload(messageResult.messageId, attachmentId, ByteArrayInputStream(data))
    return attachmentId
  }

  private fun commitSnapshotFor(attachmentId: AttachmentId, cdn: Int) {
    val attachment = REDDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(REDStore.backup.mediaRootBackupKey).encode()

    REDDatabase.backupMediaSnapshots.writePendingMediaEntries(
      listOf(MediaEntry(mediaId = mediaId, cdn = cdn, plaintextHash = plaintextHash, remoteKey = remoteKey, isThumbnail = false))
    )
    REDDatabase.backupMediaSnapshots.commitPendingRows()
  }

  private fun fakeCdnContains(attachmentId: AttachmentId, cdn: Int) {
    val attachment = REDDatabase.attachments.getAttachment(attachmentId)!!
    val plaintextHash = attachment.dataHash!!.decodeBase64OrThrow()
    val remoteKey = attachment.remoteKey!!.decodeBase64OrThrow()
    val mediaId = MediaName.fromPlaintextHashAndRemoteKey(plaintextHash, remoteKey).toMediaId(REDStore.backup.mediaRootBackupKey).encode()

    every { BackupRepository.listRemoteMediaObjects(any(), any()) } returns NetworkResult.Success(
      ArchiveGetMediaItemsResponse(
        storedMediaObjects = listOf(StoredMediaObject(cdn = cdn, mediaId = mediaId, objectLength = attachment.size)),
        backupDir = null,
        mediaDir = null,
        cursor = null
      )
    )
  }

  private fun fakeCdnEmpty() {
    every { BackupRepository.listRemoteMediaObjects(any(), any()) } returns NetworkResult.Success(
      ArchiveGetMediaItemsResponse(storedMediaObjects = emptyList(), backupDir = null, mediaDir = null, cursor = null)
    )
  }

  private fun createIncomingMessage(serverTime: Duration, attachment: Attachment): IncomingMessage {
    return IncomingMessage(
      type = MessageType.NORMAL,
      from = harness.others[0],
      body = null,
      sentTimeMillis = serverTime.inWholeMilliseconds,
      serverTimeMillis = serverTime.inWholeMilliseconds,
      receivedTimeMillis = serverTime.inWholeMilliseconds,
      attachments = listOf(attachment)
    )
  }

  private fun createAttachmentPointer(key: ByteArray, size: Int): Attachment {
    return PointerAttachment.forPointer(
      pointer = Optional.of(
        REDServiceAttachmentPointer(
          cdnNumber = 3,
          remoteId = REDServiceAttachmentRemoteId.V4("asdf"),
          contentType = MediaUtil.IMAGE_JPEG,
          key = key,
          size = Optional.of(size),
          preview = Optional.empty(),
          width = 2,
          height = 2,
          digest = Optional.of(byteArrayOf()),
          incrementalDigest = Optional.empty(),
          incrementalMacChunkSize = 0,
          fileName = Optional.of("file.jpg"),
          voiceNote = false,
          isBorderless = false,
          isGif = false,
          caption = Optional.empty(),
          blurHash = Optional.empty(),
          uploadTimestamp = 0,
          uuid = null
        )
      )
    ).get()
  }
}
