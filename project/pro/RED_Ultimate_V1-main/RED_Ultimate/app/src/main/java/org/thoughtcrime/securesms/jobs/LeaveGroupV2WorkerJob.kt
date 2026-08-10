package com.red.sovereign.jobs

import org.signal.core.util.logging.Log
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupChangeBusyException
import com.red.sovereign.groups.GroupChangeFailedException
import com.red.sovereign.groups.GroupId
import com.red.sovereign.groups.GroupManager
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.JsonJobData
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.recipients.Recipient
import java.io.IOException

/**
 * Leave a group. See [LeaveGroupV2Job] for more details on how this job should be enqueued.
 */
class LeaveGroupV2WorkerJob(parameters: Parameters, private val groupId: GroupId.V2) : BaseJob(parameters) {

  constructor(groupId: GroupId.V2) : this(
    parameters = Parameters.Builder()
      .setQueue(PushProcessMessageJob.getQueueName(Recipient.externalGroupExact(groupId).id))
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(Parameters.UNLIMITED)
      .setMaxInstancesForQueue(2)
      .build(),
    groupId = groupId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putString(KEY_GROUP_ID, groupId.toString())
      .serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onRun() {
    Log.i(TAG, "Attempting to leave group $groupId")

    val groupRecipient = Recipient.externalGroupExact(groupId)

    GroupManager.leaveGroup(context, groupId, false)

    val threadId = REDDatabase.threads.getThreadIdIfExistsFor(groupRecipient.id)
    if (threadId != -1L) {
      REDDatabase.recipients.setProfileSharing(groupRecipient.id, enabled = false)
      REDDatabase.threads.setEntireThreadRead(threadId)
      REDDatabase.threads.update(threadId, unarchive = false, allowDeletion = false)
      AppDependencies.messageNotifier.updateNotification(context)
    }
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return e is GroupChangeBusyException || e is GroupChangeFailedException || e is IOException
  }

  override fun onFailure() = Unit

  class Factory : Job.Factory<LeaveGroupV2WorkerJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): LeaveGroupV2WorkerJob {
      val data = JsonJobData.deserialize(serializedData)
      return LeaveGroupV2WorkerJob(parameters, GroupId.parseOrThrow(data.getString(KEY_GROUP_ID)).requireV2())
    }
  }

  companion object {
    const val KEY = "LeaveGroupWorkerJob"

    private val TAG = Log.tag(LeaveGroupV2WorkerJob::class.java)

    private const val KEY_GROUP_ID = "group_id"
  }
}
