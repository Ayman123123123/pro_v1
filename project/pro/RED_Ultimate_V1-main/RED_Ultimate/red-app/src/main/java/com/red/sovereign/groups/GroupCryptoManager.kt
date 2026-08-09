package com.red.sovereign.groups

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.DeviceKeyManager
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.SecureStore
import com.red.sovereign.crypto.EncryptedEnvelope
import com.red.sovereign.crypto.IdentityDirectoryApi
import com.red.sovereign.crypto.PersistentSignalProtocolStore
import com.red.sovereign.crypto.SignalSessionManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.GroupCipher
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

class GroupCryptoManager(context: Context) {
    private val tokens = TokenStore(context)
    private val keys = DeviceKeyManager(context)
    private val protocolStore = PersistentSignalProtocolStore(context, keys)
    private val pairwise = SignalSessionManager(context)
    private val directory = IdentityDirectoryApi(AuthorizedApiClient(tokens))
    private val metadata = SecureStore(context, "younes_group_sender_keys")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun prepare(group: Group, plaintext: ByteArray): ApiResult<PreparedGroupSend> {
        require(plaintext.isNotEmpty() && plaintext.size <= 256 * 1024)
        val ownRedId = tokens.redId ?: return ApiResult.Error(401, "LOCAL_IDENTITY_UNAVAILABLE")
        val local = SignalProtocolAddress(ownRedId, keys.protocolDeviceId())
        val membershipHash = membershipHash(group)
        var distributionId = metadata.get("distribution:${group.id}")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val requiresDistribution = distributionId == null || metadata.get("membership:${group.id}") != membershipHash
        if (requiresDistribution) distributionId = UUID.randomUUID()
        distributionId ?: return ApiResult.Error(null, "GROUP_DISTRIBUTION_ID_FAILED")

        val distributions = mutableListOf<GroupPairwisePayload>()
        if (requiresDistribution) {
            val distribution = GroupSessionBuilder(protocolStore).create(local, distributionId)
            val wire = json.encodeToString(SenderKeyWire(group.id, distributionId.toString(), Base64.getEncoder().encodeToString(distribution.serialize())))
                .toByteArray(Charsets.UTF_8)
            for (member in group.members.filter { it.redId != ownRedId }) {
                when (val encrypted = pairwise.encrypt(member.redId, wire)) {
                    is ApiResult.Error -> return encrypted
                    is ApiResult.Success -> distributions += encrypted.value.map { GroupPairwisePayload(member.redId, it) }
                }
            }
            metadata.put("distribution:${group.id}", distributionId.toString())
            metadata.put("membership:${group.id}", membershipHash)
        }

        val message = GroupCipher(protocolStore, local).encrypt(distributionId, plaintext)
        val recipients = mutableListOf<GroupRecipient>()
        for (member in group.members.filter { it.redId != ownRedId }) {
            when (val found = directory.get(member.redId)) {
                is ApiResult.Error -> return found
                is ApiResult.Success -> recipients += found.value.devices.map { GroupRecipient(member.redId, it.protocolDeviceId) }
            }
        }
        return ApiResult.Success(200, PreparedGroupSend(distributions, recipients, EncryptedEnvelope(0, message.type, message.serialize())))
    }

    fun processDistribution(senderRedId: String, senderDeviceId: Int, plaintext: ByteArray): String {
        val wire = json.decodeFromString<SenderKeyWire>(plaintext.toString(Charsets.UTF_8))
        val distributionId = UUID.fromString(wire.distributionId)
        val message = SenderKeyDistributionMessage(Base64.getDecoder().decode(wire.serialized))
        GroupSessionBuilder(protocolStore).process(SignalProtocolAddress(senderRedId, senderDeviceId), message)
        return wire.groupId
    }

    fun decrypt(senderRedId: String, senderDeviceId: Int, ciphertext: ByteArray): ByteArray =
        GroupCipher(protocolStore, SignalProtocolAddress(senderRedId, senderDeviceId)).decrypt(ciphertext)

    fun rotate(groupId: String) {
        metadata.remove("distribution:$groupId", "membership:$groupId")
    }

    private fun membershipHash(group: Group): String {
        val canonical = group.members.sortedWith(compareBy(GroupMember::redId, GroupMember::userId))
            .joinToString("|") { "${it.redId}:${it.userId}:${it.role}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

@Serializable data class SenderKeyWire(val groupId: String, val distributionId: String, val serialized: String)
data class GroupPairwisePayload(val receiverRedId: String, val encrypted: EncryptedEnvelope)
data class GroupRecipient(val redId: String, val protocolDeviceId: Int)
data class PreparedGroupSend(
    val distributions: List<GroupPairwisePayload>,
    val recipients: List<GroupRecipient>,
    val groupCiphertext: EncryptedEnvelope
)
