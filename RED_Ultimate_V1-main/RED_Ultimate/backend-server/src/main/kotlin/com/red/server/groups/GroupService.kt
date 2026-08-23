package com.red.server.groups

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.media.MediaService
import com.red.server.social.UuidV7
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class GroupService(
    private val mongo: MongoTemplate,
    private val users: UserAccountRepository,
    private val media: MediaService,
    private val events: ApplicationEventPublisher
) {
    private val random = SecureRandom()

    /** 🔔 بث فوري لكل الأعضاء المتصلين بأن حالة المجموعة تغيّرت (عضوية/أدوار/إعدادات). */
    private fun notifyMembershipChanged(groupId: String, extraRedIds: List<String> = emptyList()) {
        val redIds = runCatching { response(group(groupId)).members.map { it.redId } }.getOrDefault(emptyList()) + extraRedIds
        val audience = (redIds + extraRedIds).filter(String::isNotBlank).distinct()
        if (audience.isNotEmpty()) events.publishEvent(GroupMembershipChangedEvent(groupId, audience))
    }
    fun create(ownerId: UUID, request: CreateGroupRequest): GroupResponse {
        val owner = users.findById(ownerId).orElseThrow { NoSuchElementException("User not found") }
        val name = request.name.trim(); require(name.length in 2..100) { "Group name must be 2-100 characters" }
        val description = request.description?.trim()?.takeIf(String::isNotEmpty); require(description == null || description.length <= 500)
        val privacy = runCatching { GroupPrivacy.valueOf(request.privacy.trim().uppercase()) }.getOrElse { GroupPrivacy.PRIVATE }
        val group = mongo.save(GroupDocument(UuidV7.next(), name, description, owner.redId, privacy = privacy))
        mongo.save(GroupMember("${group.id}:${owner.id}", group.id, owner.id.toString(), owner.redId, owner.username, GroupRole.OWNER))
        return response(group)
    }

    fun list(userId: UUID): List<GroupResponse> {
        val ids = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), GroupMember::class.java).map(GroupMember::groupId)
        if (ids.isEmpty()) return emptyList()
        return mongo.find(Query(Criteria.where("id").`in`(ids)).with(Sort.by(Sort.Direction.DESC, "updatedAt")), GroupDocument::class.java).map(::response)
    }

    /** Role of an actual member. Non-members return null — never pretend they are MEMBER. */
    fun roleFor(userId: UUID, groupId: String): GroupRole? {
        val m = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java)
        return m?.role
    }

    /** قائمة الهويات المسموح دعوتها إلى جلسة مرتبطة بالمجموعة؛ تستخدم بعد تحقق المضيف. */
    fun memberRedIds(groupId: String): Set<String> = mongo.find(
        Query(Criteria.where("groupId").`is`(groupId)), GroupMember::class.java
    ).map { it.redId }.filter(String::isNotBlank).toSet()

    fun details(userId: UUID, groupId: String): GroupResponse {
        // لا تكشف وجود المجموعة لغير الأعضاء — رسالة موحدة
        val member = membership(groupId, userId)
        val doc = try { group(groupId) } catch (_: NoSuchElementException) { throw NoSuchElementException("Group not found or access denied") }
        return response(doc)
    }

    fun add(actorId: UUID, groupId: String, request: AddGroupMemberRequest): GroupResponse {
        val actor = membership(groupId, actorId)
        val groupDoc = group(groupId)
        // احترام إعدادات المجموعة: إضافة الأعضاء قد تكون محصورة بالمشرفين
        if (groupDoc.settings.onlyAdminsCanAddMembers) {
            require(actor.role == GroupRole.OWNER || actor.role == GroupRole.ADMIN) { "Insufficient group permission" }
        } else {
            require(actor.role == GroupRole.OWNER || actor.role == GroupRole.ADMIN || actor.role == GroupRole.MODERATOR || actor.role == GroupRole.MEMBER) { "Must be member to add" }
        }
        require(request.role != GroupRole.OWNER) { "Ownership transfer requires a dedicated operation" }
        // 🔐 تصعيد الصلاحيات: المالك وحده من يمنح دور ADMIN/MODERATOR عند الإضافة
        require(request.role != GroupRole.ADMIN && request.role != GroupRole.MODERATOR || actor.role == GroupRole.OWNER) { "Only the owner can add admins/moderators" }
        val effectiveRole = request.role ?: GroupRole.MEMBER
        val target = users.findByRedId(request.redId.trim().uppercase()) ?: throw IllegalArgumentException("Invalid request")
        val id = "$groupId:${target.id}"
        insertMember(GroupMember(id, groupId, target.id.toString(), target.redId, target.username, effectiveRole))
        touch(groupId)
        // 🔐 E2EE: Membership changed — clients must rotate Sender Key distribution
        // The next message from any member will generate a fresh distributionId (see GroupCryptoManager.membershipHash)
        // 🔔 إشعار فوري: العضو الجديد + بقية الأعضاء يحدّثون قوائمهم ومفاتيحهم لحظياً
        notifyMembershipChanged(groupId, extraRedIds = listOf(target.redId))
        return response(group(groupId))
    }

    fun role(actorId: UUID, groupId: String, targetUserId: UUID, request: UpdateGroupRoleRequest): GroupResponse {
        val actor = membership(groupId, actorId); require(actor.role == GroupRole.OWNER) { "Only owner can change roles" }
        require(request.role != GroupRole.OWNER) { "Ownership transfer is not supported yet" }
        val target = membership(groupId, targetUserId); require(target.role != GroupRole.OWNER)
        target.role = request.role; mongo.save(target); touch(groupId)
        notifyMembershipChanged(groupId)
        return response(group(groupId))
    }

    fun remove(actorId: UUID, groupId: String, targetUserId: UUID): GroupResponse {
        val actor = membership(groupId, actorId); val target = membership(groupId, targetUserId)
        require(target.role != GroupRole.OWNER) { "Owner cannot be removed" }
        require(actor.role == GroupRole.OWNER || (actor.role == GroupRole.ADMIN && target.role == GroupRole.MEMBER)) { "Insufficient group permission" }
        mongo.remove(Query(Criteria.where("id").`is`(target.id)), GroupMember::class.java)
        // 🧹 طلب انضمام معلق لعضو مُطرد لا يجب أن يبقى قابلاً للموافقة لاحقاً
        mongo.remove(Query(Criteria.where("id").`is`(target.id)), GroupJoinRequestDocument::class.java)
        touch(groupId)
        notifyMembershipChanged(groupId, extraRedIds = listOf(target.redId))
        // 🔐 E2EE: Member removed — remaining members must rotate Sender Key on next send
        return response(group(groupId))
    }

    fun transferOwnership(ownerId: UUID, groupId: String, targetUserId: UUID): GroupResponse {
        val currentOwner = membership(groupId, ownerId)
        require(currentOwner.role == GroupRole.OWNER) { "Only owner can transfer ownership" }
        val target = membership(groupId, targetUserId)
        require(target.id != currentOwner.id) { "Target must be another member" }
        currentOwner.role = GroupRole.ADMIN
        target.role = GroupRole.OWNER
        mongo.save(currentOwner); mongo.save(target)
        val group = group(groupId)
        val targetAccount = users.findById(targetUserId).orElseThrow { NoSuchElementException("Target account not found") }
        val updated = group.copy(ownerRedId = targetAccount.redId, updatedAt = Instant.now())
        mongo.save(updated)
        notifyMembershipChanged(groupId)
        return response(updated)
    }

    fun delete(ownerId: UUID, groupId: String) {
        require(membership(groupId, ownerId).role == GroupRole.OWNER) { "Only owner can delete group" }
        // 🔔 نبّه الأعضاء قبل الحذف كي يزيلوا المجموعة من واجهاتهم فوراً
        notifyMembershipChanged(groupId)
        group(groupId).avatarMediaKey?.let { runCatching { media.delete(it) } }
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupMember::class.java)
        // 🧹 تنظيف البيانات اليتيمة: الدعوات وطلبات الانضمام والرسائل والمثبتات وإعدادات الاختفاء
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupInviteDocument::class.java)
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupJoinRequestDocument::class.java)
        runCatching { mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), com.red.server.database.GroupMessageDocument::class.java) }
        runCatching { mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), com.red.server.database.PinnedMessageDocument::class.java) }
        runCatching { mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), com.red.server.database.DisappearingSettingsDocument::class.java) }
        runCatching { mongo.remove(Query(Criteria.where("id").`is`(groupId)), com.red.server.database.DisappearingSettingsDocument::class.java) }
        mongo.remove(Query(Criteria.where("id").`is`(groupId)), GroupDocument::class.java)
    }

    fun leave(userId: UUID, groupId: String) {
        val member = membership(groupId, userId); require(member.role != GroupRole.OWNER) { "Owner must transfer or delete the group" }
        mongo.remove(Query(Criteria.where("id").`is`(member.id)), GroupMember::class.java)
        mongo.remove(Query(Criteria.where("id").`is`(member.id)), GroupJoinRequestDocument::class.java)
        touch(groupId)
        notifyMembershipChanged(groupId, extraRedIds = listOf(member.redId))
        // 🔐 E2EE: Leave triggers rotation
    }

    fun updateAvatar(actorId: UUID, groupId: String, request: UpdateGroupAvatarRequest): GroupResponse {
        requireManager(groupId, actorId)
        require(request.mediaKey.startsWith("users/$actorId/")) { "Group avatar must belong to the manager" }
        require(media.exists(request.mediaKey)) { "Avatar media not found" }
        require(media.metadata(request.mediaKey).mimeType.startsWith("image/")) { "Group avatar must be an image" }
        val current = group(groupId)
        current.avatarMediaKey?.let { if (it != request.mediaKey) runCatching { media.delete(it) } }
        val updated = current.copy(avatarMediaKey = request.mediaKey, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    fun createInvite(actorId: UUID, groupId: String, request: CreateGroupInviteRequest): GroupInviteResponse {
        val g = group(groupId)
        if (g.settings.onlyAdminsCanInvite) requireManager(groupId, actorId) else require(membership(groupId, actorId) != null) { "Must be member to invite" }
        require(request.expiresHours in 1..168) { "Invite expiry must be 1-168 hours" }
        require(request.maxUses in 1..100) { "Invite max uses must be 1-100" }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val invite = mongo.save(GroupInviteDocument(
            id = UuidV7.next(), groupId = groupId, creatorId = actorId.toString(), tokenHash = hashToken(token),
            requireApproval = request.requireApproval, maxUses = request.maxUses,
            expiresAt = Instant.now().plus(request.expiresHours, ChronoUnit.HOURS)
        ))
        return GroupInviteResponse(invite.id, token, invite.expiresAt, invite.maxUses, invite.requireApproval)
    }

    fun requestJoin(userId: UUID, request: JoinGroupRequest): GroupJoinRequestResponse {
        val invite = mongo.findOne(Query(Criteria.where("tokenHash").`is`(hashToken(request.token))), GroupInviteDocument::class.java)
            ?: throw NoSuchElementException("Invite not found")
        require(invite.revokedAt == null && invite.expiresAt.isAfter(Instant.now())) { "Invite is expired or exhausted" }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val memberId = "${invite.groupId}:$userId"
        require(!mongo.exists(Query(Criteria.where("id").`is`(memberId)), GroupMember::class.java)) { "User is already a member" }

        // إعادة إرسال طلب معلق لا تعد انضمامًا جديدًا ولا يجب أن تستهلك استخدامًا
        // إضافيًا للدعوة؛ وإلا يستطيع العميل استنزاف حد الدعوة بلا انضمامات فعلية.
        if (invite.requireApproval) {
            val existingPending = mongo.findById(memberId, GroupJoinRequestDocument::class.java)
                ?.takeIf { it.status == "PENDING" }
            if (existingPending != null) return existingPending.response()
        }

        // ⚛️ حجز استخدام واحد ذرياً — سباقات الاسترداد المتزامنة لا يمكنها تجاوز maxUses
        mongo.findAndModify(
            Query(Criteria.where("id").`is`(invite.id).and("uses").lt(invite.maxUses)),
            Update().inc("uses", 1),
            GroupInviteDocument::class.java
        ) ?: throw IllegalStateException("Invite is expired or exhausted")

        if (!invite.requireApproval) {
            insertMember(GroupMember(memberId, invite.groupId, user.id.toString(), user.redId, user.username, GroupRole.MEMBER))
            touch(invite.groupId)
            // 🔔 العضو الجديد عبر رابط دعوة يحتاج المجموعة فوراً في قائمته
            notifyMembershipChanged(invite.groupId, extraRedIds = listOf(user.redId))
            return GroupJoinRequestResponse("joined:$memberId", invite.groupId, user.redId, user.username, "APPROVED", Instant.now())
        }

        val pending = runCatching { mongo.save(GroupJoinRequestDocument(memberId, invite.groupId, user.id.toString(), user.redId, user.username)) }
            .getOrElse { e -> throw if (e is DuplicateKeyException) IllegalStateException("Join request already exists") else e }
        return pending.response()
    }

    fun joinRequests(actorId: UUID, groupId: String): List<GroupJoinRequestResponse> {
        requireManager(groupId, actorId)
        return mongo.find(Query(Criteria.where("groupId").`is`(groupId).and("status").`is`("PENDING")).with(Sort.by("createdAt")), GroupJoinRequestDocument::class.java).map { it.response() }
    }

    fun resolveJoinRequest(actorId: UUID, groupId: String, requestId: String, approve: Boolean): GroupResponse {
        requireManager(groupId, actorId)
        val pending = mongo.findById(requestId, GroupJoinRequestDocument::class.java)
            ?: throw NoSuchElementException("Join request not found")
        require(pending.groupId == groupId && pending.status == "PENDING") { "Join request is not pending" }
        pending.status = if (approve) "APPROVED" else "REJECTED"; pending.resolvedAt = Instant.now(); pending.resolvedBy = actorId.toString(); mongo.save(pending)
        if (approve) {
            insertMember(GroupMember("$groupId:${pending.userId}", groupId, pending.userId, pending.redId, pending.username, GroupRole.MEMBER))
            touch(groupId)
            // 🔔 الموافقة على طلب انضمام = عضو جديد يجب أن يرى المجموعة لحظياً
            notifyMembershipChanged(groupId, extraRedIds = listOf(pending.redId))
        }
        return response(group(groupId))
    }

    fun revokeInvite(actorId: UUID, groupId: String, inviteId: String) {
        requireManager(groupId, actorId)
        val invite = mongo.findById(inviteId, GroupInviteDocument::class.java) ?: throw NoSuchElementException("Invite not found")
        require(invite.groupId == groupId); invite.revokedAt = Instant.now(); mongo.save(invite)
    }

    fun listInvites(actorId: UUID, groupId: String): List<GroupInviteResponse> {
        requireManager(groupId, actorId)
        return mongo.find(Query(Criteria.where("groupId").`is`(groupId)), GroupInviteDocument::class.java)
            .filter { it.revokedAt == null && it.expiresAt.isAfter(Instant.now()) }
            .sortedByDescending { it.createdAt }
            .map { GroupInviteResponse(it.id, "", it.expiresAt, it.maxUses, it.requireApproval) }
    }

    fun updateInfo(actorId: UUID, groupId: String, request: UpdateGroupInfoRequest): GroupResponse {
        val doc = group(groupId)
        val member = membership(groupId, actorId)
        if (doc.settings.onlyAdminsCanEditInfo) require(member.role == GroupRole.OWNER || member.role == GroupRole.ADMIN) { "Only admins can edit group info" }
        var changed = false
        var newName = doc.name
        var newDesc = doc.description
        var newPrivacy = doc.privacy
        request.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
            require(it.length in 1..100) { "Group name must be 1-100 characters" }
            newName = it; changed = true
        }
        if (request.description != null) {
            val d = request.description.trim()
            require(d.length <= 500) { "Description must be <=500" }
            newDesc = d.ifBlank { null }; changed = true
        }
        request.privacy?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let {
            newPrivacy = try { GroupPrivacy.valueOf(it) } catch (_: Exception) { throw IllegalArgumentException("Invalid privacy") }
            changed = true
        }
        if (!changed) return response(doc)
        val updated = doc.copy(name = newName, description = newDesc, privacy = newPrivacy, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    fun updateDisappearing(actorId: UUID, groupId: String, request: UpdateDisappearingRequest): Map<String, Any?> {
        requireManager(groupId, actorId)
        val doc = group(groupId)
        // server-enforced disappearing setting
        val duration = request.durationSeconds
        if (duration != null) require(duration in 0..604800 || duration == -1L) { "Invalid disappearing duration" }
        val settingsDoc = com.red.server.database.DisappearingSettingsDocument(
            id = groupId,
            groupId = groupId,
            disappearAfterSeconds = (duration ?: 0).toInt(),
            enabled = duration != null && duration != 0L,
            updatedBy = actorId.toString()
        )
        mongo.save(settingsDoc)
        return mapOf("groupId" to doc.id, "disappearAfterSeconds" to (duration ?: 0), "enabled" to (duration != null && duration != 0L))
    }

    fun updateSettings(actorId: UUID, groupId: String, request: UpdateGroupSettingsRequest): GroupResponse {
        requireManager(groupId, actorId)
        val current = group(groupId)
        val newSettings = GroupSettings(
            onlyAdminsCanSend = request.onlyAdminsCanSend,
            onlyAdminsCanEditInfo = request.onlyAdminsCanEditInfo,
            requireJoinApproval = request.requireJoinApproval
        )
        val updated = current.copy(settings = newSettings, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    fun count(): Long = mongo.count(Query(), GroupDocument::class.java)

    private fun hashToken(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun GroupJoinRequestDocument.response() = GroupJoinRequestResponse(id, groupId, redId, username, status, createdAt)

    /** إدراج عضو مع تحويل سباق التكرار إلى 409 بدل 500 (الفهرس المركب الفريد هو الحكم النهائي). */
    private fun insertMember(member: GroupMember) = runCatching { mongo.save(member) }
        .getOrElse { e -> throw if (e is DuplicateKeyException) IllegalStateException("User is already a member") else e }

    private fun group(id: String) = mongo.findById(id, GroupDocument::class.java) ?: throw NoSuchElementException("Group not found")
    private fun membership(groupId: String, userId: UUID) = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java)
        ?: throw NoSuchElementException("Group membership not found")
    private fun requireManager(groupId: String, userId: UUID) = membership(groupId, userId).also { require(it.role == GroupRole.OWNER || it.role == GroupRole.ADMIN) }
    private fun touch(groupId: String) { group(groupId).also { it.updatedAt = Instant.now(); mongo.save(it) } }
    private fun response(group: GroupDocument) = GroupResponse(
        group.id,
        group.name,
        group.description,
        group.ownerRedId,
        group.avatarMediaKey?.let { "/api/media/$it" },
        group.privacy,
        group.settings,
        group.createdAt,
        mongo.find(Query(Criteria.where("groupId").`is`(group.id)).with(Sort.by(Sort.Direction.ASC, "joinedAt")), GroupMember::class.java)
    )
}
