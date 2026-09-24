package com.red.sovereign.testing

import okio.ByteString.Companion.toByteString
import org.signal.core.models.ServiceId.ACI
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.storage.protos.groups.Member
import org.signal.storageservice.storage.protos.groups.local.DecryptedGroup
import org.signal.storageservice.storage.protos.groups.local.DecryptedMember
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import org.whispersystems.signalservice.internal.push.GroupContextV2
import kotlin.random.Random

/**
 * Helper methods for creating groups for message processing tests et al.
 */
object GroupTestingUtils {
  fun member(aci: ACI, revision: Int = 0, role: Member.Role = Member.Role.ADMINISTRATOR, labelEmoji: String = "", labelString: String = ""): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(aci.toByteString())
      .joinedAtRevision(revision)
      .role(role)
      .labelEmoji(labelEmoji)
      .labelString(labelString)
      .build()
  }

  fun insertGroup(revision: Int = 0, vararg members: DecryptedMember): TestGroupInfo {
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroupState = DecryptedGroup.Builder()
      .members(members.toList())
      .revision(revision)
      .title(MessageContentFuzzer.string())
      .build()

    val groupId = REDDatabase.groups.create(groupMasterKey, decryptedGroupState, null)!!
    val groupRecipientId = REDDatabase.recipients.getOrInsertFromGroupId(groupId)
    REDDatabase.recipients.setProfileSharing(groupRecipientId, true)

    return TestGroupInfo(groupId, groupMasterKey, groupRecipientId)
  }

  fun RecipientId.asMember(): DecryptedMember {
    return Recipient.resolved(this).asMember()
  }

  fun Recipient.asMember(): DecryptedMember {
    return member(aci = requireAci())
  }

  data class TestGroupInfo(val groupId: GroupId.V2, val masterKey: GroupMasterKey, val recipientId: RecipientId) {
    val groupV2Context: GroupContextV2
      get() = GroupContextV2(masterKey = masterKey.serialize().toByteString(), revision = 0)
  }
}
