/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.database

import android.app.Application
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.UuidUtil
import org.signal.core.util.deleteAll
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderId
import com.red.sovereign.components.settings.app.chats.folders.ChatFolderRecord
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.testutil.RecipientTestRule
import org.whispersystems.signalservice.api.storage.REDChatFolderRecord
import org.whispersystems.signalservice.api.storage.StorageId
import org.whispersystems.signalservice.internal.storage.protos.ChatFolderRecord as RemoteChatFolderRecord
import org.whispersystems.signalservice.internal.storage.protos.Recipient as RemoteRecipient

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class ChatFolderTablesTest {

  @get:Rule
  val recipients = RecipientTestRule()

  private lateinit var alice: RecipientId
  private lateinit var bob: RecipientId
  private lateinit var charlie: RecipientId

  private lateinit var folder1: ChatFolderRecord
  private lateinit var folder2: ChatFolderRecord
  private lateinit var folder3: ChatFolderRecord
  private lateinit var folder4: ChatFolderRecord

  private var aliceThread: Long = 0
  private var bobThread: Long = 0
  private var charlieThread: Long = 0

  @Before
  fun setUp() {
    alice = recipients.createRecipient("Alice One")
    bob = recipients.createRecipient("Bob Two")
    charlie = recipients.createRecipient("Charlie Three")

    aliceThread = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(alice))
    bobThread = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(bob))
    charlieThread = REDDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(charlie))

    folder1 = ChatFolderRecord(
      id = 2,
      name = "folder1",
      position = 0,
      includedChats = listOf(aliceThread, bobThread),
      excludedChats = listOf(charlieThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.CUSTOM,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(1, 2, 3))
    )

    folder2 = ChatFolderRecord(
      name = "folder2",
      position = 2,
      includedChats = listOf(bobThread),
      showUnread = true,
      showMutedChats = true,
      showIndividualChats = true,
      folderType = ChatFolderRecord.FolderType.INDIVIDUAL,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(2, 3, 4))
    )

    folder3 = ChatFolderRecord(
      name = "folder3",
      position = 3,
      includedChats = listOf(bobThread),
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      folderType = ChatFolderRecord.FolderType.GROUP,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(3, 4, 5))
    )

    folder4 = ChatFolderRecord(
      name = "folder4",
      position = 4,
      excludedChats = listOf(aliceThread, charlieThread),
      showUnread = true,
      showMutedChats = true,
      showGroupChats = true,
      folderType = ChatFolderRecord.FolderType.UNREAD,
      chatFolderId = ChatFolderId.generate(),
      storageServiceId = StorageId.forChatFolder(byteArrayOf(4, 5, 6))
    )

    REDDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderTable.TABLE_NAME)
    REDDatabase.chatFolders.writableDatabase.deleteAll(ChatFolderTables.ChatFolderMembershipTable.TABLE_NAME)
  }

  @Test
  fun givenChatFolder_whenIGetFolder_thenIExpectFolderWithChats() {
    REDDatabase.chatFolders.createFolder(folder1)
    val actualFolders = REDDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolder_whenIUpdateFolder_thenIExpectUpdatedFolderWithChats() {
    REDDatabase.chatFolders.createFolder(folder2)
    val folder = REDDatabase.chatFolders.getCurrentChatFolders().first()
    val updatedFolder = folder.copy(
      name = "updatedFolder2",
      position = 1,
      includedChats = listOf(aliceThread, charlieThread),
      excludedChats = listOf(bobThread)
    )
    REDDatabase.chatFolders.updateFolder(updatedFolder)

    val actualFolder = REDDatabase.chatFolders.getCurrentChatFolders().first()

    assertEquals(updatedFolder, actualFolder)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetFolders_thenIExpectAListWithoutThatFolder() {
    REDDatabase.chatFolders.createFolder(folder1)
    REDDatabase.chatFolders.createFolder(folder2)
    val folders = REDDatabase.chatFolders.getCurrentChatFolders()
    REDDatabase.chatFolders.deleteChatFolder(folders.last())

    val actualFolders = REDDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenChatFolders_whenIUpdateTheirStorageSyncIds_thenIExpectAnUpdatedList() {
    val existingMap = REDDatabase.chatFolders.getStorageSyncIdsMap()
    existingMap.forEach { (id, _) ->
      REDDatabase.chatFolders.applyStorageIdUpdate(id, StorageId.forChatFolder(StorageSyncHelper.generateKey()))
    }
    val updatedMap = REDDatabase.chatFolders.getStorageSyncIdsMap()

    existingMap.forEach { (id, storageId) ->
      assertNotEquals(storageId, updatedMap[id])
    }
  }

  @Test
  fun givenARemoteFolder_whenIInsertLocally_thenIExpectAListWithThatFolder() {
    val remoteRecord =
      REDChatFolderRecord(
        folder1.storageServiceId!!,
        RemoteChatFolderRecord(
          identifier = UuidUtil.toByteArray(folder1.chatFolderId.uuid).toByteString(),
          name = folder1.name,
          position = folder1.position,
          showOnlyUnread = folder1.showUnread,
          showMutedChats = folder1.showMutedChats,
          includeAllIndividualChats = folder1.showIndividualChats,
          includeAllGroupChats = folder1.showGroupChats,
          folderType = RemoteChatFolderRecord.FolderType.CUSTOM,
          deletedAtTimestampMs = folder1.deletedTimestampMs,
          includedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(alice).serviceId.get().toString())),
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(bob).serviceId.get().toString()))
          ),
          excludedRecipients = listOf(
            RemoteRecipient(RemoteRecipient.Contact(Recipient.resolved(charlie).serviceId.get().toString()))
          )
        )
      )

    REDDatabase.chatFolders.insertChatFolderFromStorageSync(remoteRecord)
    val actualFolders = REDDatabase.chatFolders.getCurrentChatFolders()

    assertEquals(listOf(folder1), actualFolders)
  }

  @Test
  fun givenADeletedChatFolder_whenIGetPositions_thenIExpectPositionsToStillBeConsecutive() {
    REDDatabase.chatFolders.createFolder(folder1)
    REDDatabase.chatFolders.createFolder(folder2)
    REDDatabase.chatFolders.createFolder(folder3)

    val folders = REDDatabase.chatFolders.getCurrentChatFolders()
    REDDatabase.chatFolders.deleteChatFolder(folders[1])

    val actualFolders = REDDatabase.chatFolders.getCurrentChatFolders()
    actualFolders.forEachIndexed { index, folder ->
      assertEquals(folder.position, index)
    }
  }

  @Test
  fun givenAnEmptyFolder_whenIGetItsEmptyStatus_thenIExpectTrue() {
    REDDatabase.chatFolders.createFolder(folder4)
    val actualFolders = REDDatabase.chatFolders.getCurrentChatFolders()
    val unreadCountAndEmptyAndMutedStatus = REDDatabase.chatFolders.getUnreadCountAndEmptyAndMutedStatusForFolders(actualFolders)
    val actualFolderIsEmpty = unreadCountAndEmptyAndMutedStatus[actualFolders.first().id]!!.second

    assertTrue(actualFolderIsEmpty)
  }
}
