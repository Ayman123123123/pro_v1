package com.red.sovereign.components.settings.app.chats.folders

import com.red.sovereign.database.REDDatabase
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper

/**
 * Repository for chat folders that handles creation, deletion, listing, etc.,
 */
object ChatFoldersRepository {

  fun getCurrentFolders(): List<ChatFolderRecord> {
    return REDDatabase.chatFolders.getCurrentChatFolders()
  }

  fun getUnreadCountAndEmptyAndMutedStatusForFolders(folders: List<ChatFolderRecord>): HashMap<Long, Triple<Int, Boolean, Boolean>> {
    return REDDatabase.chatFolders.getUnreadCountAndEmptyAndMutedStatusForFolders(folders)
  }

  fun createFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> REDDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> REDDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    REDDatabase.chatFolders.createFolder(updatedFolder)
    StorageSyncHelper.scheduleSyncForDataChange()
  }

  fun updateFolder(folder: ChatFolderRecord, includedRecipients: Set<Recipient>, excludedRecipients: Set<Recipient>) {
    val includedChats = includedRecipients.map { recipient -> REDDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val excludedChats = excludedRecipients.map { recipient -> REDDatabase.threads.getOrCreateThreadIdFor(recipient) }
    val updatedFolder = folder.copy(
      includedChats = includedChats,
      excludedChats = excludedChats
    )

    REDDatabase.chatFolders.updateFolder(updatedFolder)
    scheduleSync(updatedFolder.id)
  }

  fun deleteFolder(folder: ChatFolderRecord) {
    REDDatabase.chatFolders.deleteChatFolder(folder)
    scheduleSync(folder.id)
  }

  fun updatePositions(folders: List<ChatFolderRecord>) {
    REDDatabase.chatFolders.updatePositions(folders)
    folders.forEach { scheduleSync(it.id) }
  }

  fun getFolder(id: Long): ChatFolderRecord {
    return REDDatabase.chatFolders.getChatFolder(id)!!
  }

  fun getFolderCount(): Int {
    return REDDatabase.chatFolders.getFolderCount()
  }

  private fun scheduleSync(id: Long) {
    REDDatabase.chatFolders.markNeedsSync(id)
    StorageSyncHelper.scheduleSyncForDataChange()
  }
}
