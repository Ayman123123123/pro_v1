/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.backup.v2.processor

import org.signal.archive.proto.Frame
import org.signal.archive.proto.ReleaseNotes
import org.signal.archive.stream.BackupFrameEmitter
import org.signal.core.models.ServiceId
import org.signal.core.util.logging.Log
import org.signal.core.util.update
import com.red.sovereign.backup.v2.ArchiveRecipient
import com.red.sovereign.backup.v2.ExportOddities
import com.red.sovereign.backup.v2.ExportSkips
import com.red.sovereign.backup.v2.ExportState
import com.red.sovereign.backup.v2.ImportState
import com.red.sovereign.backup.v2.database.getAllForBackup
import com.red.sovereign.backup.v2.database.getCallLinksForBackup
import com.red.sovereign.backup.v2.database.getContactsForBackup
import com.red.sovereign.backup.v2.database.getGroupsForBackup
import com.red.sovereign.backup.v2.database.restoreReleaseNotes
import com.red.sovereign.backup.v2.importer.CallLinkArchiveImporter
import com.red.sovereign.backup.v2.importer.ContactArchiveImporter
import com.red.sovereign.backup.v2.importer.DistributionListArchiveImporter
import com.red.sovereign.backup.v2.importer.GroupArchiveImporter
import com.red.sovereign.backup.v2.util.toLocal
import com.red.sovereign.database.RecipientTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

/**
 * Handles importing/exporting [ArchiveRecipient] frames for an archive.
 */
object RecipientArchiveProcessor {

  val TAG = Log.tag(RecipientArchiveProcessor::class.java)

  fun export(db: REDDatabase, signalStore: REDStore, exportState: ExportState, selfAci: ServiceId.ACI, emitter: BackupFrameEmitter) {
    val releaseChannelId = signalStore.releaseChannelValues.releaseChannelRecipientId
    if (releaseChannelId != null) {
      exportState.recipientIds.add(releaseChannelId.toLong())
      exportState.contactRecipientIds.add(releaseChannelId.toLong())
      exportState.releaseNoteRecipientId = releaseChannelId.toLong()
      emitter.emit(
        Frame(
          recipient = ArchiveRecipient(
            id = releaseChannelId.toLong(),
            releaseNotes = ReleaseNotes()
          )
        )
      )
    } else {
      Log.w(TAG, ExportOddities.releaseChannelRecipientMissing())
    }

    db.recipientTable.getContactsForBackup(exportState.selfRecipientId.toLong()).use { reader ->
      for (recipient in reader) {
        if (recipient != null) {
          val successfullyAdded = exportState.recipientIds.add(recipient.id)

          if (!successfullyAdded) {
            Log.w(TAG, ExportSkips.duplicateRecipientId(recipient.id))
            continue
          }

          exportState.contactRecipientIds.add(recipient.id)
          recipient.contact?.aci?.let {
            exportState.recipientIdToAci[recipient.id] = it
            exportState.aciToRecipientId[ServiceId.ACI.parseOrThrow(it).toString()] = recipient.id
          }
          recipient.contact?.e164?.let {
            exportState.recipientIdToE164[recipient.id] = it
          }

          emitter.emit(Frame(recipient = recipient))
        }
      }
    }

    exportState.recipientIds.add(exportState.selfRecipientId.toLong())
    exportState.contactRecipientIds.add(exportState.selfRecipientId.toLong())
    exportState.recipientIdToAci[exportState.selfRecipientId.toLong()] = selfAci.toByteString()
    exportState.aciToRecipientId[selfAci.toString()] = exportState.selfRecipientId.toLong()

    db.recipientTable.getGroupsForBackup(selfAci).use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        exportState.groupRecipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.distributionListTables.getAllForBackup(exportState.selfRecipientId, exportState).use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }

    db.callLinkTable.getCallLinksForBackup().use { reader ->
      for (recipient in reader) {
        exportState.recipientIds.add(recipient.id)
        emitter.emit(Frame(recipient = recipient))
      }
    }
  }

  fun import(recipient: ArchiveRecipient, importState: ImportState) {
    val recipientContact = recipient.contact
    val recipientGroup = recipient.group
    val recipientDistributionList = recipient.distributionList
    val recipientCallLink = recipient.callLink
    val recipientSelf = recipient.self
    val newId: RecipientId? = when {
      recipientContact != null -> ContactArchiveImporter.import(recipientContact)
      recipientGroup != null -> GroupArchiveImporter.import(recipientGroup)
      recipientDistributionList != null -> DistributionListArchiveImporter.import(recipientDistributionList, importState)
      recipient.releaseNotes != null -> REDDatabase.recipients.restoreReleaseNotes()
      recipientCallLink != null -> CallLinkArchiveImporter.import(recipientCallLink)
      recipientSelf != null -> {
        REDDatabase.writableDatabase
          .update(RecipientTable.TABLE_NAME)
          .values(RecipientTable.AVATAR_COLOR to recipientSelf.avatarColor?.toLocal()?.serialize())
          .where("${RecipientTable.ID} = ?", Recipient.self().id)
          .run()
        Recipient.self().id
      }
      else -> {
        Log.w(TAG, "Unrecognized recipient type!")
        null
      }
    }
    if (newId != null) {
      importState.remoteToLocalRecipientId[recipient.id] = newId
    }
  }
}
