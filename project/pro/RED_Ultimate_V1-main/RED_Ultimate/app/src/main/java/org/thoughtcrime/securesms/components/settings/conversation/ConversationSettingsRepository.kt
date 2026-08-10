package com.red.sovereign.components.settings.conversation

import android.content.Context
import android.database.Cursor
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.rx3.asObservable
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import com.red.sovereign.contacts.sync.ContactDiscovery
import com.red.sovereign.database.CallTable
import com.red.sovereign.database.MediaTable
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.GroupRecord
import com.red.sovereign.database.model.IdentityRecord
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.database.model.StoryViewState
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.GroupProtoUtil
import com.red.sovereign.groups.GroupsInCommonRepository
import com.red.sovereign.groups.LiveGroup
import com.red.sovereign.groups.ui.GroupChangeFailureReason
import com.red.sovereign.groups.ui.GroupChangeResult
import com.red.sovereign.groups.v2.GroupAddMembersResult
import com.red.sovereign.groups.v2.GroupManagementRepository
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.recipients.RecipientUtil
import com.red.sovereign.util.RemoteConfig
import java.io.IOException

private val TAG = Log.tag(ConversationSettingsRepository::class.java)

class ConversationSettingsRepository(
  private val context: Context,
  private val groupManagementRepository: GroupManagementRepository = GroupManagementRepository(context)
) {

  fun getCallEvents(callRowIds: LongArray): Single<List<Pair<CallTable.Call, MessageRecord>>> {
    return if (callRowIds.isEmpty()) {
      Single.just(emptyList())
    } else {
      Single.fromCallable {
        val callMap = REDDatabase.calls.getCallsByRowIds(callRowIds.toList())
        val messageIds = callMap.values.mapNotNull { it.messageId }
        REDDatabase.messages.getMessages(messageIds).iterator().asSequence()
          .filter { callMap.containsKey(it.id) }
          .map { callMap[it.id]!! to it }
          .sortedByDescending { it.first.timestamp }
          .toList()
      }
    }
  }

  @WorkerThread
  fun getThreadMedia(threadId: Long, limit: Int): Cursor? {
    return if (threadId > 0) {
      REDDatabase.media.getGalleryMediaForThread(threadId, MediaTable.Sorting.Newest, limit)
    } else {
      null
    }
  }

  fun getStoryViewState(groupId: GroupId): Observable<StoryViewState> {
    return Observable.fromCallable {
      REDDatabase.recipients.getByGroupId(groupId)
    }.flatMap {
      StoryViewState.getForRecipientId(it.get())
    }.observeOn(Schedulers.io())
  }

  fun getThreadId(recipientId: RecipientId, consumer: (Long) -> Unit) {
    REDExecutors.BOUNDED.execute {
      consumer(REDDatabase.threads.getThreadIdIfExistsFor(recipientId))
    }
  }

  fun getThreadId(groupId: GroupId, consumer: (Long) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val recipientId = Recipient.externalGroupExact(groupId).id
      consumer(REDDatabase.threads.getThreadIdIfExistsFor(recipientId))
    }
  }

  fun isInternalRecipientDetailsEnabled(): Boolean = REDStore.internal.recipientDetails

  fun hasGroups(consumer: (Boolean) -> Unit) {
    REDExecutors.BOUNDED.execute { consumer(REDDatabase.groups.getActiveGroupCount() > 0) }
  }

  fun getIdentity(recipientId: RecipientId, consumer: (IdentityRecord?) -> Unit) {
    REDExecutors.BOUNDED.execute {
      if (REDStore.account.aci != null && REDStore.account.pni != null) {
        consumer(AppDependencies.protocolStore.aci().identities().getIdentityRecord(recipientId).orElse(null))
      } else {
        consumer(null)
      }
    }
  }

  fun getGroupsInCommon(recipientId: RecipientId): Observable<List<Recipient>> {
    return GroupsInCommonRepository.getGroupsInCommon(context, recipientId)
      .asObservable()
  }

  fun getGroupMembership(recipientId: RecipientId, consumer: (List<RecipientId>) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val groupDatabase = REDDatabase.groups
      val groupRecords = groupDatabase.getPushGroupsContainingMember(recipientId)
      val groupRecipients = ArrayList<RecipientId>(groupRecords.size)
      for (groupRecord in groupRecords) {
        groupRecipients.add(groupRecord.recipientId)
      }
      consumer(groupRecipients)
    }
  }

  fun refreshRecipient(recipientId: RecipientId) {
    REDExecutors.UNBOUNDED.execute {
      try {
        ContactDiscovery.refresh(context, Recipient.resolved(recipientId), false)
      } catch (e: IOException) {
        Log.w(TAG, "Failed to refresh user after adding to contacts.")
      }
    }
  }

  fun setMuteUntil(recipientId: RecipientId, until: Long) {
    REDExecutors.BOUNDED.execute {
      REDDatabase.recipients.setMuted(recipientId, until)
    }
  }

  fun getGroupCapacity(groupId: GroupId, consumer: (GroupCapacityResult) -> Unit) {
    REDExecutors.BOUNDED.execute {
      val groupRecord: GroupRecord = REDDatabase.groups.getGroup(groupId).get()
      consumer(
        if (groupRecord.hasV2GroupProperties) {
          val decryptedGroup: DecryptedGroup = groupRecord.requireV2GroupProperties().decryptedGroup
          val pendingMembers: List<RecipientId> = decryptedGroup.pendingMembers
            .map { m -> m.serviceIdBytes }
            .map { s -> GroupProtoUtil.serviceIdBinaryToRecipientId(s) }

          val members = mutableListOf<RecipientId>()

          members.addAll(groupRecord.members)
          members.addAll(pendingMembers)

          GroupCapacityResult(Recipient.self().id, members, RemoteConfig.groupLimits, groupRecord.isAnnouncementGroup)
        } else {
          GroupCapacityResult(Recipient.self().id, groupRecord.members, RemoteConfig.groupLimits, false)
        }
      )
    }
  }

  fun addMembers(groupId: GroupId, selected: List<RecipientId>, consumer: (GroupAddMembersResult) -> Unit) {
    groupManagementRepository.addMembers(groupId, selected, consumer)
  }

  fun setMuteUntil(groupId: GroupId, until: Long) {
    REDExecutors.BOUNDED.execute {
      val recipientId = Recipient.externalGroupExact(groupId).id
      REDDatabase.recipients.setMuted(recipientId, until)
    }
  }

  @WorkerThread
  fun block(recipientId: RecipientId): GroupChangeResult {
    return try {
      val recipient = Recipient.resolved(recipientId)
      if (recipient.isGroup) {
        RecipientUtil.block(context, recipient)
      } else {
        RecipientUtil.blockNonGroup(context, recipient)
      }
      GroupChangeResult.SUCCESS
    } catch (e: Exception) {
      Log.w(TAG, "Failed to block recipient.", e)
      GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
    }
  }

  fun unblock(recipientId: RecipientId) {
    REDExecutors.BOUNDED.execute {
      val recipient = Recipient.resolved(recipientId)
      RecipientUtil.unblock(recipient)
    }
  }

  @WorkerThread
  fun block(groupId: GroupId): GroupChangeResult {
    return try {
      val recipient = Recipient.externalGroupExact(groupId)
      RecipientUtil.block(context, recipient)
      GroupChangeResult.SUCCESS
    } catch (e: Exception) {
      Log.w(TAG, "Failed to block group.", e)
      GroupChangeResult.failure(GroupChangeFailureReason.fromException(e))
    }
  }

  fun unblock(groupId: GroupId) {
    REDExecutors.BOUNDED.execute {
      val recipient = Recipient.externalGroupExact(groupId)
      RecipientUtil.unblock(recipient)
    }
  }

  @WorkerThread
  fun isMessageRequestAccepted(recipient: Recipient): Boolean {
    return RecipientUtil.isMessageRequestAccepted(recipient)
  }

  fun getMembershipCountDescription(liveGroup: LiveGroup): LiveData<String> {
    return liveGroup.getMembershipCountDescription(context.resources)
  }

  @WorkerThread
  fun isArchived(recipientId: RecipientId): Boolean {
    return REDDatabase.threads.isArchived(recipientId)
  }

  @WorkerThread
  fun setArchived(threadId: Long, archived: Boolean) {
    REDDatabase.threads.setArchived(setOf(threadId), archived)
  }

  @WorkerThread
  fun deleteChat(threadId: Long) {
    REDDatabase.threads.deleteConversation(threadId)
  }
}
