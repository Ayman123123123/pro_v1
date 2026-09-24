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
        val communityId = request.communityId?.trim()?.takeIf(String::isNotEmpty)
        require(communityId == null || communityId.length <= 64) { "Invalid community id" }
        val group = mongo.save(GroupDocument(UuidV7.next(), name, description, owner.redId, privacy = privacy, memberCount = 1L, communityId = communityId))
        // AUTO-FIX (groups visibility): group + owner membership are two separate writes without a
        // transaction; if the membership write failed the group became a permanently invisible orphan
        // (list() only follows group_members). Compensate by removing the group on failure.
        try {
            mongo.save(GroupMember("${group.id}:${owner.id}", group.id, owner.id.toString(), owner.redId, owner.username, GroupRole.OWNER))
        } catch (e: Exception) {
            mongo.remove(Query(Criteria.where("_id").`is`(group.id)), GroupDocument::class.java)
            throw e
        }

        // إبلاغ المُنشئ فوراً ليتم حفظ المجموعة في Room DB وإظهارها في شاشة الدردشات
        notifyMembershipChanged(group.id)

        return response(group)
    }

    fun list(userId: UUID): List<GroupResponse> {
        val memberIds = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), GroupMember::class.java).map(GroupMember::groupId)
        // AUTO-FIX (groups visibility): also include groups the user owns, so the creator always
        // sees their group even if the owner membership row is missing.
        val ownerRedId = users.findById(userId).map { it.redId }.orElse("")
        val ownedIds = if (ownerRedId.isEmpty()) emptyList()
            else mongo.find(Query(Criteria.where("ownerRedId").`is`(ownerRedId)), GroupDocument::class.java).map(GroupDocument::id)
        val ids = (memberIds + ownedIds).distinct()
        if (ids.isEmpty()) return emptyList()
        return mongo.find(Query(Criteria.where("id").`in`(ids)).with(Sort.by(Sort.Direction.DESC, "updatedAt")), GroupDocument::class.java).map(::response)
    }

    /** P0-F: ترقيم keyset بسيط — cursor بصيغة updatedAt_id (epochMillis أو ISO-8601)، limit في 1..50. */
    fun listPaged(userId: UUID, cursor: String?, limit: Int?): List<GroupResponse> {
        val lim = (limit ?: 20).coerceIn(1, 50)
        val memberIds = mongo.find(Query(Criteria.where("userId").`is`(userId.toString())), GroupMember::class.java).map(GroupMember::groupId)
        // AUTO-FIX (groups visibility): same owner-inclusion as list().
        val ownerRedId = users.findById(userId).map { it.redId }.orElse("")
        val ownedIds = if (ownerRedId.isEmpty()) emptyList()
            else mongo.find(Query(Criteria.where("ownerRedId").`is`(ownerRedId)), GroupDocument::class.java).map(GroupDocument::id)
        val ids = (memberIds + ownedIds).distinct()
        if (ids.isEmpty()) return emptyList()
        var criteria = Criteria.where("id").`in`(ids)
        if (!cursor.isNullOrBlank()) {
            val sep = cursor.lastIndexOf('_')
            if (sep > 0) {
                val timePart = cursor.substring(0, sep)
                val idPart = cursor.substring(sep + 1)
                val cursorTime: Instant? = runCatching { Instant.ofEpochMilli(timePart.toLong()) }.getOrNull()
                    ?: runCatching { Instant.parse(timePart) }.getOrNull()
                if (cursorTime != null && idPart.isNotBlank()) {
                    criteria = Criteria().andOperator(
                        Criteria.where("id").`in`(ids),
                        Criteria().orOperator(
                            Criteria.where("updatedAt").lt(cursorTime),
                            Criteria().andOperator(
                                Criteria.where("updatedAt").`is`(cursorTime),
                                Criteria.where("id").lt(idPart)
                            )
                        )
                    )
                }
            }
        }
        val q = Query(criteria).with(Sort.by(Sort.Direction.DESC, "updatedAt", "id")).limit(lim)
        return mongo.find(q, GroupDocument::class.java).map(::response)
    }

    /** P0-F: اكتشاف المجموعات العامة فقط — بحث name regex مع حد 20. LEGENDARY: حد طول q لمنع ReDoS */
    fun discover(q: String?, limit: Int = 20): List<GroupResponse> {
        val lim = limit.coerceIn(1, 20)
        val query = (q?.trim().orEmpty()).takeIf { it.isNotBlank() }?.let {
            require(it.length <= 64) { "QUERY_TOO_LONG" }
            it
        }
        val base = Criteria.where("privacy").`is`(GroupPrivacy.PUBLIC)
        val criteria = if (!query.isNullOrBlank()) {
            val pattern = ".*${java.util.regex.Pattern.quote(query)}.*"
            base.and("name").regex(pattern, "i")
        } else base
        val mongoQuery = Query(criteria).with(Sort.by(Sort.Direction.DESC, "updatedAt")).limit(lim)
        return mongo.find(mongoQuery, GroupDocument::class.java).map(::response)
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
        checkNotBanned(groupId, target.id)
        val id = "$groupId:${target.id}"
        insertMember(GroupMember(id, groupId, target.id.toString(), target.redId, target.username, effectiveRole))
        incMemberCount(groupId, 1)
        writeAudit(groupId, "MEMBER_ADD", actorId.toString(), target.id.toString(), "role=$effectiveRole")
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
        val oldRole = target.role
        target.role = request.role; mongo.save(target); touch(groupId)
        writeAudit(groupId, "ROLE_CHANGE", actorId.toString(), targetUserId.toString(), "$oldRole->${request.role}")
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
        incMemberCount(groupId, -1)
        writeAudit(groupId, "MEMBER_REMOVE", actorId.toString(), targetUserId.toString(), null)
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
        writeAudit(groupId, "OWNERSHIP_TRANSFER", ownerId.toString(), targetUserId.toString(), null)
        notifyMembershipChanged(groupId)
        return response(updated)
    }

    fun delete(ownerId: UUID, groupId: String) {
        require(membership(groupId, ownerId).role == GroupRole.OWNER) { "Only owner can delete group" }
        // 🔔 نبّه الأعضاء قبل الحذف كي يزيلوا المجموعة من واجهاتهم فوراً
        notifyMembershipChanged(groupId)
        // Group avatars are uploader-owned objects and may be shared with a
        // story, post or another group. Deleting the group removes its grant,
        // not the underlying object (see the observational-only orphan scan).
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupMember::class.java)
        // 🧹 تنظيف البيانات اليتيمة: الدعوات وطلبات الانضمام والرسائل والمثبتات وإعدادات الاختفاء
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupInviteDocument::class.java)
        mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupJoinRequestDocument::class.java)
        // P0-F: تنظيف الحظر عند حذف المجموعة
        runCatching { mongo.remove(Query(Criteria.where("groupId").`is`(groupId)), GroupBan::class.java) }
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
        incMemberCount(groupId, -1)
        notifyMembershipChanged(groupId, extraRedIds = listOf(member.redId))
        // 🔐 E2EE: Leave triggers rotation
    }

    fun updateAvatar(actorId: UUID, groupId: String, request: UpdateGroupAvatarRequest): GroupResponse {
        requireManager(groupId, actorId)
        require(request.mediaKey.startsWith("users/$actorId/")) { "Group avatar must belong to the manager" }
        require(media.exists(request.mediaKey)) { "Avatar media not found" }
        require(media.metadata(request.mediaKey).mimeType.startsWith("image/")) { "Group avatar must be an image" }
        group(groupId) // fail closed if the group was removed after membership check
        // An atomic field update avoids overwriting concurrently changed roles,
        // counters/settings. The old uploader-owned object may still be shared.
        mongo.updateFirst(Query(Criteria.where("id").`is`(groupId)),
            Update().set("avatarMediaKey", request.mediaKey).set("updatedAt", Instant.now()),
            GroupDocument::class.java)
        return response(group(groupId))
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
        val invite = mongo.findOne(Query(Criteria.where("tokenHash").`is`(hashToken(request.token.trim()))), GroupInviteDocument::class.java)
            ?: throw NoSuchElementException("Invite not found")
        require(invite.revokedAt == null && invite.expiresAt.isAfter(Instant.now())) { "Invite is expired or exhausted" }
        val user = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        val memberId = "${invite.groupId}:$userId"
        require(!mongo.exists(Query(Criteria.where("id").`is`(memberId)), GroupMember::class.java)) { "User is already a member" }
        // P0-F: ارفض المحظور
        checkNotBanned(invite.groupId, userId)

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
            incMemberCount(invite.groupId, 1)
            writeAudit(invite.groupId, "MEMBER_ADD", userId.toString(), userId.toString(), "via-invite")
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

    /** LEGENDARY: معاينة دعوة قبل الانضمام (اسم/وصف/صورة/عدد/خصوصية) — انضمام أعمى كان يكشف SECRET */
    fun previewInvite(token: String): Map<String, Any?> {
        val t = token.trim().takeIf { it.isNotEmpty() } ?: throw NoSuchElementException("Invite not found")
        val invite = mongo.findOne(
            Query(Criteria.where("tokenHash").`is`(hashToken(t))), GroupInviteDocument::class.java
        ) ?: throw NoSuchElementException("Invite not found")
        require(invite.revokedAt == null && invite.expiresAt.isAfter(Instant.now())) { "Invite is expired or exhausted" }
        val g = group(invite.groupId)
        val count = mongo.count(Query(Criteria.where("groupId").`is`(g.id)), GroupMember::class.java)
        return mapOf(
            "groupId" to g.id,
            "name" to g.name,
            "description" to g.description,
            "avatarUrl" to g.avatarMediaKey,
            "privacy" to g.privacy.name,
            "memberCount" to count,
            "requireApproval" to invite.requireApproval,
            "expiresAt" to invite.expiresAt.toString()
        )
    }

    fun resolveJoinRequest(actorId: UUID, groupId: String, requestId: String, approve: Boolean): GroupResponse {
        requireManager(groupId, actorId)
        val pending = mongo.findById(requestId, GroupJoinRequestDocument::class.java)
            ?: throw NoSuchElementException("Join request not found")
        require(pending.groupId == groupId && pending.status == "PENDING") { "Join request is not pending" }
        pending.status = if (approve) "APPROVED" else "REJECTED"; pending.resolvedAt = Instant.now(); pending.resolvedBy = actorId.toString(); mongo.save(pending)
        if (approve) {
            runCatching { checkNotBanned(groupId, UUID.fromString(pending.userId)) }.onFailure { throw IllegalStateException("User is banned from this group") }
            insertMember(GroupMember("$groupId:${pending.userId}", groupId, pending.userId, pending.redId, pending.username, GroupRole.MEMBER))
            incMemberCount(groupId, 1)
            writeAudit(groupId, "MEMBER_ADD", actorId.toString(), UUID.fromString(pending.userId).toString(), "join-request-approved")
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
        // دمج لا استبدال — كان حفظ أي إعداد يعيد الأعلام الأربعة الأخرى لقيمها
        // الافتراضية فيسقط تخصيص المشرف (مثلاً فتح الإضافة للجميع) بصمت.
        val old = current.settings
        val newSettings = GroupSettings(
            onlyAdminsCanSend = request.onlyAdminsCanSend ?: old.onlyAdminsCanSend,
            onlyAdminsCanEditInfo = request.onlyAdminsCanEditInfo ?: old.onlyAdminsCanEditInfo,
            requireJoinApproval = request.requireJoinApproval ?: old.requireJoinApproval,
            onlyAdminsCanAddMembers = request.onlyAdminsCanAddMembers ?: old.onlyAdminsCanAddMembers,
            onlyAdminsCanInvite = request.onlyAdminsCanInvite ?: old.onlyAdminsCanInvite,
            onlyAdminsCanPin = request.onlyAdminsCanPin ?: old.onlyAdminsCanPin,
            onlyAdminsCanCall = request.onlyAdminsCanCall ?: old.onlyAdminsCanCall
        )
        val updated = current.copy(settings = newSettings, updatedAt = Instant.now())
        mongo.save(updated)
        return response(updated)
    }

    // ── P1-E: إدارة المجموعات ──────────────────────────────────────────
    // slowModeSeconds: حدّ الفاصل بين رسائل العضو الواحد (0 = معطّل، 1..3600).
    // يُخزَّن كحقل أعلى مستوى "slowModeSeconds" عبر Update مباشر حتى لا يتطلب
    // تغيير GroupModels.kt — وقراءة عدّاد الإرسال تبقى محلية (آخر رسالة + X).
    // الصلاحية: OWNER/ADMIN فقط. يُسجَّل في التدقيق ويُبثّ للأعضاء.
    fun updateSlowMode(actorId: UUID, groupId: String, slowModeSeconds: Int): Map<String, Any?> {
        requireManager(groupId, actorId)
        require(slowModeSeconds in 0..3600) { "slowModeSeconds must be 0..3600" }
        group(groupId) // يرمي إن غابت المجموعة
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(groupId)),
            Update().set("slowModeSeconds", slowModeSeconds).set("updatedAt", Instant.now()),
            GroupDocument::class.java
        )
        writeAudit(groupId, "SLOWMODE_UPDATE", actorId.toString(), null, "slowModeSeconds=$slowModeSeconds")
        notifyMembershipChanged(groupId)
        return mapOf("groupId" to groupId, "slowModeSeconds" to slowModeSeconds)
    }

    /** قراءة slowMode الحالي — 0 عند الغياب (توافق مع المجموعات القديمة). */
    fun slowModeSeconds(groupId: String): Int {
        return runCatching {
            val raw = mongo.getCollection("groups")
                .find(org.bson.Document("_id", groupId))
                .limit(1).firstOrNull() ?: return 0
            (raw["slowModeSeconds"] as? Number)?.toInt()?.coerceIn(0, 3600) ?: 0
        }.getOrDefault(0)
    }

    // P1-E: حذف المشرف لرسائل الآخرين للجميع — نافذة 24h + رسالة نظام.
    // الصلاحية: OWNER/ADMIN فقط (MODERATOR/MEMBER مرفوض). العضو يحذف رسالته
    // عبر مسار الحذف العادي؛ هذه للأدمن على رسائل الآخرين (وتعمل على رسالته أيضاً).
    // الآثار: deletedForEveryoneAt + تفريغ payload + رسالة SYSTEM + تدقيق.
    fun deleteForAllByAdmin(actorId: UUID, groupId: String, messageUuid: String): Map<String, Any?> {
        val actor = membership(groupId, actorId)
        require(actor.role == GroupRole.OWNER || actor.role == GroupRole.ADMIN) { "Only owner/admin can delete others' messages" }
        val msg = mongo.findOne(
            Query(Criteria.where("uuid").`is`(messageUuid).and("groupId").`is`(groupId)),
            com.red.server.database.GroupMessageDocument::class.java
        ) ?: throw NoSuchElementException("Group message not found")
        val ageHours = ChronoUnit.HOURS.between(msg.createdAt, Instant.now())
        require(ageHours < 24) { "Admin delete window expired (24h)" }
        if (msg.deletedForEveryoneAt != null) {
            return mapOf("messageUuid" to messageUuid, "alreadyDeleted" to true, "groupId" to groupId)
        }
        val now = Instant.now()
        mongo.updateFirst(
            Query(Criteria.where("uuid").`is`(messageUuid)),
            Update().set("deletedForEveryoneAt", now).set("payload", ByteArray(0)),
            com.red.server.database.GroupMessageDocument::class.java
        )
        // رسالة نظام تُعلن الحذف — مرئية للجميع في سجل المجموعة.
        val sysUuid = UuidV7.next()
        runCatching {
            mongo.save(
                com.red.server.database.GroupMessageDocument(
                    uuid = sysUuid,
                    groupId = groupId,
                    senderId = actor.redId,
                    senderDeviceId = 1,
                    payload = "🗑️ حذف المشرف رسالة — ${msg.senderId}".toByteArray(Charsets.UTF_8),
                    messageType = "SYSTEM",
                    ciphertextType = 7,
                    sequenceNumber = nextGroupSequence(groupId)
                )
            )
        }
        writeAudit(groupId, "ADMIN_DELETE_FOR_ALL", actorId.toString(), msg.senderId, messageUuid)
        return mapOf(
            "groupId" to groupId,
            "messageUuid" to messageUuid,
            "deletedForEveryoneAt" to now.toString(),
            "systemMessageUuid" to sysUuid
        )
    }

    // P1-E: إنهاء المجموعة للمالك — أرشفة + تعطيل روابط الدعوة (لا حذف للرسائل).
    // الصلاحية: OWNER فقط. الأرشفة حقل أعلى مستوى "archived/archivedAt" عبر
    // Update مباشر (بلا تغيير GroupModels.kt)، والروابط تُسحب عبر revokedAt.
    fun endGroup(ownerId: UUID, groupId: String): Map<String, Any?> {
        require(membership(groupId, ownerId).role == GroupRole.OWNER) { "Only owner can end group" }
        group(groupId)
        val now = Instant.now()
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(groupId)),
            Update().set("archived", true).set("archivedAt", now).set("updatedAt", now),
            GroupDocument::class.java
        )
        mongo.updateMulti(
            Query(Criteria.where("groupId").`is`(groupId).and("revokedAt").`is`(null)),
            Update().set("revokedAt", now),
            GroupInviteDocument::class.java
        )
        writeAudit(groupId, "GROUP_END", ownerId.toString(), null, "archived+invites-disabled")
        notifyMembershipChanged(groupId)
        return mapOf("groupId" to groupId, "archived" to true, "archivedAt" to now.toString(), "invitesDisabled" to true)
    }

    /** هل المجموعة مُنهاة (مؤرشفة)؟ — يقرأ الحقل الخام بلا تغيير الموديل. */
    fun isGroupEnded(groupId: String): Boolean {
        return runCatching {
            mongo.getCollection("groups")
                .find(org.bson.Document("_id", groupId))
                .limit(1).firstOrNull()?.getBoolean("archived", false) == true
        }.getOrDefault(false)
    }

    private fun nextGroupSequence(groupId: String): Long {
        return runCatching {
            val seq = mongo.findAndModify(
                Query(Criteria.where("id").`is`("group:$groupId")),
                Update().inc("sequence", 1),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().upsert(true).returnNew(true),
                com.red.server.database.ConversationSequence::class.java
            )
            seq?.sequence ?: System.currentTimeMillis()
        }.getOrDefault(System.currentTimeMillis())
    }

    fun count(): Long = mongo.count(Query(), GroupDocument::class.java)

    // ── P0-F: حظر المجموعات ──────────────────────────────────────────────
    fun ban(actorId: UUID, groupId: String, request: BanGroupMemberRequest): GroupBanResponse {
        requireManager(groupId, actorId)
        val targetId = request.userId
        val existingMember = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$targetId")), GroupMember::class.java)
        if (existingMember != null) require(existingMember.role != GroupRole.OWNER) { "Owner cannot be banned" }
        val targetAccount = users.findById(targetId).orElseThrow { NoSuchElementException("Target account not found") }
        if (existingMember != null) {
            mongo.remove(Query(Criteria.where("id").`is`(existingMember.id)), GroupMember::class.java)
            mongo.remove(Query(Criteria.where("id").`is`(existingMember.id)), GroupJoinRequestDocument::class.java)
            incMemberCount(groupId, -1)
        } else {
            mongo.remove(Query(Criteria.where("id").`is`("$groupId:$targetId")), GroupJoinRequestDocument::class.java)
            touch(groupId)
        }
        val ban = GroupBan(
            id = "$groupId:$targetId",
            groupId = groupId,
            userId = targetId.toString(),
            redId = targetAccount.redId,
            reason = request.reason?.trim()?.takeIf(String::isNotEmpty),
            bannedBy = actorId.toString(),
            expiresAt = request.expiresAt
        )
        mongo.save(ban)
        writeAudit(groupId, "MEMBER_BAN", actorId.toString(), targetId.toString(), request.reason?.trim()?.takeIf(String::isNotEmpty))
        notifyMembershipChanged(groupId, extraRedIds = listOf(targetAccount.redId))
        return ban.toResponse()
    }

    fun unban(actorId: UUID, groupId: String, targetUserId: UUID) {
        requireManager(groupId, actorId)
        mongo.remove(Query(Criteria.where("id").`is`("$groupId:$targetUserId")), GroupBan::class.java)
        touch(groupId)
        writeAudit(groupId, "MEMBER_UNBAN", actorId.toString(), targetUserId.toString(), null)
    }

    fun listBans(actorId: UUID, groupId: String): List<GroupBanResponse> {
        requireManager(groupId, actorId)
        val now = Instant.now()
        return mongo.find(Query(Criteria.where("groupId").`is`(groupId)), GroupBan::class.java)
            .filter { it.expiresAt == null || it.expiresAt.isAfter(now) }
            .sortedByDescending { it.bannedAt }
            .map { it.toResponse() }
    }

    private fun hashToken(token: String) = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun GroupJoinRequestDocument.response() = GroupJoinRequestResponse(id, groupId, redId, username, status, createdAt)

    /** إدراج عضو مع تحويل سباق التكرار إلى 409 بدل 500 (الفهرس المركب الفريد هو الحكم النهائي). */
    private fun insertMember(member: GroupMember) = runCatching { mongo.save(member) }
        .getOrElse { e -> throw if (e is DuplicateKeyException) IllegalStateException("User is already a member") else e }

    private fun group(id: String) = mongo.findById(id, GroupDocument::class.java) ?: throw NoSuchElementException("Group not found")
    private fun membership(groupId: String, userId: UUID) = mongo.findOne(Query(Criteria.where("id").`is`("$groupId:$userId")), GroupMember::class.java)
        ?: throw NoSuchElementException("Group membership not found")
    private fun requireManager(groupId: String, userId: UUID) = membership(groupId, userId).also { require(it.role == GroupRole.OWNER || it.role == GroupRole.ADMIN) }
    /** P0-F: لمس ذري — لا يعيد كتابة memberCount (كان save الكامل يخاطر بإسقاط inc متزامن). */
    private fun touch(groupId: String) {
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(groupId)),
            Update().set("updatedAt", Instant.now()),
            GroupDocument::class.java
        )
    }
    /** P0-F: عدّاد أعضاء ذري — inc عند add/remove (ويُحدّث updatedAt معه). */
    private fun incMemberCount(groupId: String, delta: Long) {
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(groupId)),
            Update().inc("memberCount", delta).set("updatedAt", Instant.now()),
            GroupDocument::class.java
        )
    }
    /** P0-F: فحص الحظر — يرفض المحظور (مع تنظيف كسول للحظر المنتهي). */
    private fun checkNotBanned(groupId: String, userId: UUID) {
        val ban = mongo.findById("$groupId:$userId", GroupBan::class.java)
            ?: mongo.findOne(Query(Criteria.where("groupId").`is`(groupId).and("userId").`is`(userId.toString())), GroupBan::class.java)
            ?: return
        if (ban.expiresAt != null && ban.expiresAt.isBefore(Instant.now())) {
            runCatching { mongo.remove(Query(Criteria.where("id").`is`(ban.id)), GroupBan::class.java) }
            return
        }
        throw IllegalStateException("User is banned from this group")
    }
    /** P0-F: تدقيق — يُستدعى في add/remove/role/transfer/ban (+unban). */
    private fun writeAudit(groupId: String, action: String, actor: String, target: String?, details: String? = null) {
        runCatching { mongo.save(GroupAudit(groupId = groupId, action = action, actor = actor, target = target, details = details)) }
    }
    private fun response(group: GroupDocument) = GroupResponse(
        group.id,
        group.name,
        group.description,
        group.ownerRedId,
        group.avatarMediaKey?.let { "/api/media/$it" },
        group.privacy,
        group.settings,
        group.createdAt,
        mongo.find(Query(Criteria.where("groupId").`is`(group.id)).with(Sort.by(Sort.Direction.ASC, "joinedAt")), GroupMember::class.java),
        group.memberCount,
        group.communityId
    )

    /** LEGENDARY: ربط/فك مجموعة بمجتمع (مدير فقط) — ينهي انقسام النظامين */
    fun setCommunity(actorId: UUID, groupId: String, communityId: String?): GroupResponse {
        requireManager(groupId, actorId)
        val clean = communityId?.trim()?.takeIf(String::isNotEmpty)
        require(clean == null || clean.length <= 64) { "Invalid community id" }
        mongo.updateFirst(
            Query(Criteria.where("id").`is`(groupId)),
            Update().set("communityId", clean).set("updatedAt", Instant.now()),
            GroupDocument::class.java
        )
        touch(groupId)
        writeAudit(groupId, "COMMUNITY_LINK", actorId.toString(), null, clean ?: "unlinked")
        notifyMembershipChanged(groupId)
        return response(group(groupId))
    }

    /** LEGENDARY: علامة قراءة رتيبة (لا ترجع للخلف) — أساس "من قرأ" */
    fun markRead(actorId: UUID, groupId: String, sequence: Long): Map<String, Any> {
        val member = membership(groupId, actorId)
        require(sequence >= 0) { "Invalid sequence" }
        val id = "$groupId:${actorId}"
        val existing = mongo.findById(id, GroupReadMark::class.java)
        val next = maxOf(existing?.lastReadSequence ?: 0L, sequence)
        mongo.save(GroupReadMark(id, groupId, actorId.toString(), member.redId, member.username, next, Instant.now()))
        return mapOf("groupId" to groupId, "lastReadSequence" to next)
    }

    /** LEGENDARY: من قرأ تسلسلاً معيناً — أي عضو يرى قراء الأعضاء (يعرفون بعضهم أصلاً) */
    fun readers(actorId: UUID, groupId: String, sequence: Long): List<GroupReadEntry> {
        membership(groupId, actorId) // عضوية فقط
        require(sequence >= 0) { "Invalid sequence" }
        return mongo.find(
            Query(Criteria.where("groupId").`is`(groupId).and("lastReadSequence").gte(sequence)),
            GroupReadMark::class.java
        ).sortedByDescending { it.lastReadSequence }.take(200)
            .map { GroupReadEntry(it.redId, it.username, it.lastReadSequence, it.updatedAt) }
    }
}
