package org.signal.benchmark

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.signal.benchmark.setup.Harness
import org.signal.benchmark.setup.TestMessages
import org.signal.benchmark.setup.TestUsers
import org.signal.core.util.logging.Log
import com.red.sovereign.BaseActivity
import com.red.sovereign.backup.v2.BackupRepository
import com.red.sovereign.crypto.ProfileKeyUtil
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.TestDbUtils
import com.red.sovereign.database.model.databaseprotos.RestoreDecisionState
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.keyvalue.Skipped
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.profiles.ProfileName
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.registration.util.RegistrationUtil
import com.red.sovereign.util.TextSecurePreferences

class BenchmarkSetupActivity : BaseActivity() {

  companion object {
    private val TAG = Log.tag(BenchmarkSetupActivity::class)

    const val SEARCH_KEYWORD = "lighthouse"

    private val SEARCH_VOCABULARY = listOf(
      "hello", "world", "signal", "android", "kotlin", "database", "benchmark", "conversation",
      "morning", "evening", "weekend", "project", "meeting", "dinner", "coffee", "garden",
      "mountain", "river", "forest", "harbor", "market", "library", "concert", "holiday"
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    var setupComplete by mutableStateOf(false)

    setContent {
      if (setupComplete) {
        Text("done")
      } else {
        CircularProgressIndicator()
      }
    }

    lifecycleScope.launch(Dispatchers.IO) {
      when (intent.extras!!.getString("setup-type")) {
        "cold-start" -> setupColdStart()
        "conversation-open" -> setupConversationOpen()
        "conversation-list-search" -> setupConversationListSearch()
        "message-send" -> setupMessageSend()
        "group-message-send" -> setupGroupMessageSend()
        "group-delivery-receipt" -> setupGroupReceipt(includeMsl = true)
        "group-read-receipt" -> setupGroupReceipt(enableReadReceipts = true)
        "thread-delete" -> setupThreadDelete()
        "thread-delete-group" -> setupThreadDeleteGroup()
        "backup-restore" -> setupBackupRestore()
      }
      setupComplete = true
    }
  }

  private fun setupColdStart() {
    TestUsers.setupSelf()
    TestUsers.setupTestRecipients(50).forEach {
      val recipient: Recipient = Recipient.resolved(it)

      TestMessages.insertIncomingTextMessage(other = recipient, body = "Cool text message?!?!")
      TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 1)
      TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 2, body = "Album")
      TestMessages.insertIncomingImageMessage(other = recipient, body = "Test", attachmentCount = 1, failed = true)
      TestMessages.insertIncomingTextMessage(other = recipient, body = "RED message")
      TestMessages.insertIncomingTextMessage(other = recipient, body = "Test")

      REDDatabase.messages.setAllMessagesRead()

      REDDatabase.threads.update(REDDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }

  private fun setupConversationOpen() {
    TestUsers.setupSelf()
    TestUsers.setupTestRecipient().let {
      val recipient: Recipient = Recipient.resolved(it)
      val messagesToAdd = 1000
      val generator: TestMessages.TimestampGenerator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

      for (i in 0 until messagesToAdd) {
        TestMessages.insertIncomingTextMessage(other = recipient, body = "Test message $i", timestamp = generator.nextTimestamp())
        TestMessages.insertOutgoingTextMessage(other = recipient, body = "Test message $i", timestamp = generator.nextTimestamp())
      }

      REDDatabase.threads.update(REDDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }

  private fun setupConversationListSearch() {
    TestUsers.setupSelf()

    val recipientCount = 50
    val messagesPerRecipient = 2000
    val totalMessages = recipientCount * messagesPerRecipient
    val generator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (totalMessages * 2000L) - 60_000L)

    TestUsers.setupTestRecipients(recipientCount).forEachIndexed { recipientIndex, recipientId ->
      val recipient: Recipient = Recipient.resolved(recipientId)

      for (i in 0 until messagesPerRecipient) {
        val body = searchableMessageBody(recipientIndex, i)
        if (i % 2 == 0) {
          TestMessages.insertIncomingTextMessage(other = recipient, body = body, timestamp = generator.nextTimestamp())
        } else {
          TestMessages.insertOutgoingTextMessage(other = recipient, body = body, timestamp = generator.nextTimestamp())
        }
      }

      REDDatabase.messages.setAllMessagesRead()
      REDDatabase.threads.update(REDDatabase.threads.getOrCreateThreadIdFor(recipient = recipient), true)
    }
  }

  private fun searchableMessageBody(recipientIndex: Int, messageIndex: Int): String {
    val words = SEARCH_VOCABULARY
    val w1 = words[(recipientIndex + messageIndex) % words.size]
    val w2 = words[(recipientIndex * 7 + messageIndex * 3) % words.size]
    val w3 = words[(recipientIndex * 13 + messageIndex * 5) % words.size]
    return "$w1 $w2 $SEARCH_KEYWORD $w3 message $messageIndex"
  }

  private fun setupMessageSend() {
    TestUsers.setupSelf()
    TestUsers.setupTestClients(1)
  }

  private fun setupGroupMessageSend() {
    TestUsers.setupSelf()
    TestUsers.setupGroup()
  }

  private fun setupThreadDelete() {
    TestUsers.setupSelf()
    val recipientIds = TestUsers.setupTestRecipients(2)
    val recipient = Recipient.resolved(recipientIds[0])
    val reactionAuthor = recipientIds[1]
    val messagesToAdd = 20_000
    val generator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

    for (i in 0 until messagesToAdd) {
      val timestamp = generator.nextTimestamp()
      when {
        i % 20 == 0 -> TestMessages.insertIncomingVoiceMessage(other = recipient, timestamp = timestamp)
        i % 4 == 0 -> TestMessages.insertIncomingImageMessage(other = recipient, attachmentCount = 1, timestamp = timestamp)
        else -> TestMessages.insertIncomingTextMessage(other = recipient, body = "Message $i", timestamp = timestamp)
      }
    }

    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(recipient = recipient)
    TestDbUtils.insertReactionsForThread(threadId, reactionAuthor, moduloFilter = 5)

    REDDatabase.threads.update(threadId, true)
  }

  private fun setupThreadDeleteGroup() {
    TestUsers.setupSelf()
    val groupId = TestUsers.setupGroup()
    val groupRecipient = Recipient.externalGroupExact(groupId)
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(groupRecipient)

    val selfId = Recipient.self().id
    val memberRecipientIds = REDDatabase.groups.getGroup(groupId).get().members.filter { it != selfId }

    val messagesToAdd = 20_000
    val generator = TestMessages.TimestampGenerator(System.currentTimeMillis() - (messagesToAdd * 2000L) - 60_000L)

    for (i in 0 until messagesToAdd) {
      val timestamp = generator.nextTimestamp()
      when {
        i % 4 == 0 -> TestMessages.insertOutgoingImageMessage(other = groupRecipient, attachmentCount = 1, timestamp = timestamp)
        else -> {
          val message = OutgoingMessage(
            recipient = groupRecipient,
            body = "Message $i",
            timestamp = timestamp,
            isSecure = true
          )
          val insert = REDDatabase.messages.insertMessageOutbox(message, threadId, false, null)
          REDDatabase.messages.markAsSent(insert.messageId, true)
        }
      }
    }

    TestDbUtils.insertGroupReceiptsForThread(threadId, memberRecipientIds)
    TestDbUtils.insertReactionsForThread(threadId, memberRecipientIds[0], moduloFilter = 5)
    TestDbUtils.insertMentionsForThread(threadId, memberRecipientIds[0], moduloFilter = 10)

    REDDatabase.threads.update(threadId, true)
  }

  private fun setupBackupRestore() {
    TestUsers.setupSelf()

    val profileKey = ProfileKeyUtil.getSelfProfileKey()
    val selfData = BackupRepository.SelfData(
      aci = Harness.SELF_ACI,
      pni = REDStore.account.requirePni(),
      e164 = Harness.SELF_E164,
      profileKey = profileKey
    )

    val backupBytes = assets.open("backups/backup.binproto").use { it.readBytes() }
    Log.i(TAG, "Read ${backupBytes.size} bytes from backup asset")

    val result = BackupRepository.importPlaintextTest(
      length = backupBytes.size.toLong(),
      inputStreamFactory = { backupBytes.inputStream() },
      selfData = selfData
    )

    Log.i(TAG, "Backup import result: $result")

    REDStore.svr.optOut()
    REDStore.registration.restoreDecisionState = RestoreDecisionState.Skipped
    REDDatabase.recipients.setProfileKey(Recipient.self().id, profileKey)
    REDDatabase.recipients.setProfileName(Recipient.self().id, ProfileName.fromParts("Tester", "McTesterson"))
    RegistrationUtil.maybeMarkRegistrationComplete()
  }

  private fun setupGroupReceipt(includeMsl: Boolean = false, enableReadReceipts: Boolean = false) {
    TestUsers.setupSelf()
    val groupId = TestUsers.setupGroup()

    val groupRecipient = Recipient.externalGroupExact(groupId)
    val threadId = REDDatabase.threads.getOrCreateThreadIdFor(groupRecipient)

    val messageIds = mutableListOf<Long>()
    val timestamps = mutableListOf<Long>()
    val baseTimestamp = 2_000_000L

    for (i in 0 until 100) {
      val timestamp = baseTimestamp + i
      val message = OutgoingMessage(
        recipient = groupRecipient,
        body = "Outgoing message $i",
        timestamp = timestamp,
        isSecure = true
      )
      val insert = REDDatabase.messages.insertMessageOutbox(message, threadId, false, null)
      REDDatabase.messages.markAsSent(insert.messageId, true)
      messageIds += insert.messageId
      timestamps += timestamp
    }

    if (includeMsl) {
      val selfId = Recipient.self().id
      val memberRecipientIds = REDDatabase.groups.getGroup(groupId).get().members.filter { it != selfId }
      TestDbUtils.insertMessageSendLogEntries(messageIds, timestamps, memberRecipientIds)
    }

    if (enableReadReceipts) {
      TextSecurePreferences.setReadReceiptsEnabled(this, true)
    }
  }
}
