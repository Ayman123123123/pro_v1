package com.red.sovereign.groups.v2

import android.content.Context
import androidx.core.util.Consumer
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.Result
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.contacts.sync.ContactDiscovery
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupChangeBusyException
import com.red.sovereign.groups.GroupChangeException
import com.red.sovereign.groups.GroupChangeFailedException
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.GroupManager
import com.red.sovereign.groups.ui.GroupChangeFailureReason
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import java.io.IOException

private val TAG: String = Log.tag(GroupManagementRepository::class.java)

/**
 * Single source repository for managing GV2 groups.
 */
class GroupManagementRepository @JvmOverloads constructor(private val context: Context = AppDependencies.application) {

  fun addMembers(groupRecipient: Recipient, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    addMembers(null, groupRecipient, selected, consumer)
  }

  fun addMembers(groupId: GroupId, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    addMembers(groupId, null, selected, consumer)
  }

  private fun addMembers(potentialGroupId: GroupId?, potentialGroupRecipient: Recipient?, selected: List<RecipientId>, consumer: Consumer<GroupAddMembersResult>) {
    REDExecutors.UNBOUNDED.execute {
      val groupId: GroupId.Push = potentialGroupId?.requirePush() ?: potentialGroupRecipient!!.requireGroupId().requirePush()

      val recipients = selected.map(Recipient::resolved)
        .filterNot { it.hasServiceId && it.isRegistered }
        .toList()

      try {
        ContactDiscovery.refresh(context, recipients, false)
        recipients.forEach { Recipient.live(it.id).refresh() }
      } catch (e: IOException) {
        consumer.accept(GroupAddMembersResult.Failure(GroupChangeFailureReason.NETWORK))
      }

      consumer.accept(
        try {
          val toAdd = selected.filter { Recipient.resolved(it).isRegistered }
          if (toAdd.isNotEmpty()) {
            val groupActionResult = GroupManager.addMembers(context, groupId, toAdd)
            GroupAddMembersResult.Success(groupActionResult.addedMemberCount, Recipient.resolvedList(groupActionResult.invitedMembers))
          } else {
            GroupAddMembersResult.Failure(GroupChangeFailureReason.NOT_GV2_CAPABLE)
          }
        } catch (e: Exception) {
          Log.d(TAG, "Failure to add member", e)
          GroupAddMembersResult.Failure(GroupChangeFailureReason.fromException(e))
        }
      )
    }
  }

  fun blockJoinRequests(groupId: GroupId.V2, recipient: Recipient): Single<GroupBlockJoinRequestResult> {
    return Single.fromCallable {
      try {
        GroupManager.ban(context, groupId, recipient.id)
        GroupBlockJoinRequestResult.Success
      } catch (e: GroupChangeException) {
        Log.w(TAG, e)
        GroupBlockJoinRequestResult.Failure(GroupChangeFailureReason.fromException(e))
      } catch (e: IOException) {
        Log.w(TAG, e)
        GroupBlockJoinRequestResult.Failure(GroupChangeFailureReason.fromException(e))
      }
    }.subscribeOn(Schedulers.io())
  }

  fun cancelJoinRequest(groupId: GroupId.V2): Single<Result<Unit, GroupChangeFailureReason>> {
    return Single.create { emitter ->
      try {
        GroupManager.cancelJoinRequest(context, groupId)
        emitter.onSuccess(Result.success(Unit))
      } catch (gcfe: GroupChangeFailedException) {
        Log.i(TAG, "Unable to cancel request", gcfe)
        emitter.onSuccess(Result.failure(GroupChangeFailureReason.fromException(gcfe)))
      } catch (ioe: IOException) {
        Log.i(TAG, "Unable to cancel request", ioe)
        emitter.onSuccess(Result.failure(GroupChangeFailureReason.fromException(ioe)))
      } catch (gcbe: GroupChangeBusyException) {
        Log.i(TAG, "Unable to cancel request", gcbe)
        emitter.onSuccess(Result.failure(GroupChangeFailureReason.fromException(gcbe)))
      }
    }.subscribeOn(Schedulers.io())
  }

  fun removeUnmigratedV1Members(groupId: GroupId.V2): Completable {
    return Completable.fromCallable {
      REDDatabase.groups.removeUnmigratedV1Members(groupId)
    }.subscribeOn(Schedulers.io())
  }

  fun isJustSelf(groupId: GroupId): Single<Boolean> {
    return Single.fromCallable {
      REDDatabase.groups.requireGroup(groupId).members == listOf(Recipient.self().id)
    }
  }
}
