package com.red.sovereign.groups

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.GroupEntity
import com.red.sovereign.core.database.LocalRepository
import com.red.sovereign.core.GroupSyncBus
import com.red.sovereign.media.MediaApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val client = AuthorizedApiClient(TokenStore(application))
    private val media = MediaApi(application, client)
    private val repository = LocalRepository(application)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val groups = mutableStateListOf<Group>()
    val avatars = mutableStateMapOf<String, ImageBitmap>()
    val joinRequests = mutableStateListOf<GroupJoinRequestResponse>()
    var latestInvite: GroupInviteResponse? by mutableStateOf(null); private set
    var state: GroupState by mutableStateOf(GroupState.Loading); private set

    init {
        load()
        viewModelScope.launch {
            repository.getGroups().collectLatest { entities ->
                // ⚠️ القائمة المحلية تُستخدم فقط كـ cache عند غياب الشبكة؛ لا نُنشئ أعضاء وهميين
                // بـ redId فارغ لأنها تُسمم حساب العضوية وتُفشل تشفير المجموعة (directory.get(""))
                if (state == GroupState.Loading || state is GroupState.Error) {
                    val known = groups.map { it.id }.toSet()
                    groups.clear()
                    groups.addAll(entities.mapNotNull { entity ->
                        if (entity.id in known) null else {
                            // نحتفظ بالعدد للعرض فقط، لكن قائمة الأعضاء الفعلية تُجلب من الخادم عند الاتصال
                            Group(entity.id, entity.name, entity.description, "owner", entity.avatarUrl, createdAt = entity.createdAt.toString(), members = emptyList())
                        }
                    })
                }
            }
        }
        // 🔄 شفاء ذاتي: GroupCryptoManager ينشر needRefresh عند فشل فك تشفير جماعي.
        // كان الـ Bus بلا مستمع (ميت) — هذا الـ collect يعيد التحميل من الخادم.
        viewModelScope.launch {
            GroupSyncBus.events.collect { load() }
        }
    }

    companion object {
        /**
         * حد التزامن للإضافة الجماعية: 4 طلبات متوازية — توازن بين السرعة (~4x
         * للقوائم الكبيرة) وعدم إغراق الخادم/حدود المعدل.
         * آمن: AuthorizedApiClient يستخدم OkHttp مشترك (خيط-آمن) + REFRESH_MUTEX
         * يحمي تجديد التوكن عند 401 المتزامنة، وكل إضافة مستقلة (redId مختلف).
         */
        const val BULK_ADD_MAX_CONCURRENCY = 4
        private val refreshScope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
        fun refreshGroups(context: android.content.Context) {
            refreshScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val client = com.red.sovereign.auth.AuthorizedApiClient(com.red.sovereign.auth.TokenStore(context))
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                when (val result = client.request("GET", "/api/groups")) {
                    is ApiResult.Success -> runCatching { json.decodeFromString<List<Group>>(result.value) }.onSuccess { list ->
                        com.red.sovereign.core.database.LocalRepository(context).saveGroups(list.map {
                            com.red.sovereign.core.database.GroupEntity(
                                id = it.id,
                                name = it.name,
                                description = it.description,
                                avatarUrl = it.avatarUrl,
                                ownerRedId = it.ownerRedId,
                                memberCount = it.members.size
                            )
                        })
                    }.onFailure { e ->
                        android.util.Log.w("GroupViewModel", "Background group refresh: invalid response", e)
                    }
                    is ApiResult.Error -> android.util.Log.w("GroupViewModel", "Background group refresh failed: ${result.message}")
                }
            }
        }
    }

    fun load() = viewModelScope.launch {
        state = GroupState.Loading
        when (val result = client.request("GET", "/api/groups")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<Group>>(result.value) }
                .onSuccess { list ->
                    state = GroupState.Ready
                    groups.clear()
                    // الأحدث أولاً — ظهور فوري مرتب عند الفتح
                    groups.addAll(list.sortedByDescending { it.createdAt })
                    // LEGENDARY FIX: بناء GroupEntity بالمسميات (كان موضعياً فكسر بعد إثراء الحقول: myRole استقبل Int!)
                    val myId = runCatching { com.red.sovereign.auth.TokenStore(getApplication()).redId }.getOrNull()
                    repository.saveGroups(list.map {
                        GroupEntity(
                            id = it.id,
                            name = it.name,
                            description = it.description,
                            avatarUrl = it.avatarUrl,
                            ownerRedId = it.ownerRedId,
                            myRole = it.members.firstOrNull { m -> m.redId == myId }?.role ?: if (it.ownerRedId == myId) "OWNER" else "MEMBER",
                            privacy = it.privacy,
                            settingsJson = runCatching { json.encodeToString(it.settings) }.getOrDefault("{}"),
                            communityId = it.communityId,
                            memberCount = it.members.size,
                            updatedAt = System.currentTimeMillis(),
                            createdAt = runCatching { it.createdAt.toLong() }.getOrDefault(0L)
                        )
                    })
                }
                .onFailure { state = GroupState.Error("INVALID_GROUP_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /**
     * P0-F: اكتشاف المجموعات العامة من الخادم — GET /api/groups/discover?q=&limit=
     * (عامة فقط، حد 1..20، q حتى 64 حرفًا) — بدل الفلترة المحلية فقط.
     * النتائج في [discoveredGroups] حتى لا تُستبدل قائمة مجموعاتي.
     */
    val discoveredGroups = mutableStateListOf<Group>()
    var discoverState: GroupState by mutableStateOf(GroupState.Ready); private set
    private var discoverJob: Job? = null

    fun discover(query: String, limit: Int = 20) {
        discoverJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) { discoveredGroups.clear(); discoverState = GroupState.Ready; return }
        discoverJob = viewModelScope.launch {
            delay(300)
            discoverState = GroupState.Loading
            val enc = java.net.URLEncoder.encode(q.take(64), "UTF-8")
            when (val result = client.request("GET", "/api/groups/discover?q=$enc&limit=${limit.coerceIn(1, 20)}")) {
                is ApiResult.Success -> runCatching { json.decodeFromString<List<Group>>(result.value) }
                    .onSuccess { discoveredGroups.clear(); discoveredGroups.addAll(it); discoverState = GroupState.Ready }
                    .onFailure { discoverState = GroupState.Error("INVALID_GROUP_RESPONSE") }
                is ApiResult.Error -> discoverState = GroupState.Error(result.message)
            }
        }
    }

    fun clearDiscovered() { discoverJob?.cancel(); discoveredGroups.clear(); discoverState = GroupState.Ready }

    /**
     * إنشاء مجموعة مع صورة اختيارية: تُرفع عبر MediaApi ثم PATCH /api/groups/{id}/avatar
     * (نفس مسار [updateAvatar]). فشل الرفع لا يُفشل الإنشاء — يُسجَّل تحذير
     * وتُحفظ المجموعة بلا صورة.
     */
    fun create(name: String, description: String?, privacy: String = "PRIVATE", memberRedIds: List<String> = emptyList(), avatarUri: Uri? = null, done: () -> Unit) = viewModelScope.launch {
        // LEGENDARY: تحقق فوري + ظهور متفائل قبل الشبكة (كانت الشاشة تتجمد بلا رد حتى عودة الخادم)
        val cleanName = name.trim()
        if (cleanName.length < 2) { state = GroupState.Error("اسم المجموعة قصير (حرفان على الأقل)"); return@launch }
        if (cleanName.length > 64) { state = GroupState.Error("اسم المجموعة طويل (64 حد أقصى)"); return@launch }
        // عنصر متفائل يظهر فوراً أعلى القائمة ثم يُستبدل بالحقيقي
        val optimisticId = "local-${System.currentTimeMillis()}"
        val optimistic = Group(
            id = optimisticId, name = cleanName, description = description?.trim(),
            ownerRedId = runCatching { com.red.sovereign.auth.TokenStore(getApplication()).redId }.getOrNull() ?: "",
            avatarUrl = null, privacy = privacy, settings = com.red.sovereign.groups.GroupSettings(),
            createdAt = System.currentTimeMillis().toString(), members = emptyList()
        )
        groups.add(0, optimistic)
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups", json.encodeToString(CreateGroupRequest(cleanName, description?.trim(), privacy)))) {
            is ApiResult.Success -> {
                val groupId = runCatching { json.decodeFromString<Group>(result.value).id }.getOrNull()
                var bulkPartialError: String? = null
                if (groupId != null) {
                    // إضافة الأعضاء المختارين بعد إنشاء المجموعة (أدوار أعضاء افتراضية)
                    val distinctIds = memberRedIds.distinct().filter { it.isNotBlank() }
                    // تزامن محدود بدل forEach المتسلسلة: النتائج تُجمع ثم تُحتسب
                    // (بلا حالة مشتركة قابلة للسباق داخل الكوروتينات المتوازية).
                    val semaphore = Semaphore(BULK_ADD_MAX_CONCURRENCY)
                    val addResults = distinctIds.map { redId ->
                        async {
                            semaphore.withPermit {
                                redId to client.request("POST", "/api/groups/$groupId/members", json.encodeToString(AddGroupMemberRequest(redId)))
                            }
                        }
                    }.awaitAll()
                    var addedCount = 0
                    addResults.forEach { (redId, add) ->
                        if (add is ApiResult.Error) {
                            // لا نُفشل إنشاء المجموعة إذا فشل إضافة عضو واحد، نتابع الباقي.
                            android.util.Log.w("GroupViewModel", "Failed to add member $redId: ${add.message}")
                        } else {
                            addedCount++
                        }
                    }
                    val failedCount = distinctIds.size - addedCount
                    if (failedCount > 0) {
                        android.util.Log.w("GroupViewModel", "Bulk add partial during create: $addedCount/${distinctIds.size} added")
                        bulkPartialError = "تمت إضافة $addedCount من ${distinctIds.size} — تعذر إضافة الباقي"
                        state = GroupState.Error(bulkPartialError)
                    }
                    // صورة المجموعة المختارة أثناء الإنشاء — تُرفع الآن وتُحفظ avatarUrl على الخادم.
                    if (avatarUri != null) {
                        when (val uploaded = media.upload(avatarUri)) {
                            is ApiResult.Error -> android.util.Log.w("GroupViewModel", "Create avatar upload failed: ${uploaded.message}")
                            is ApiResult.Success -> when (val avatarResult = client.request("PATCH", "/api/groups/$groupId/avatar", json.encodeToString(UpdateGroupAvatarRequest(uploaded.value.objectKey)))) {
                                is ApiResult.Success -> {
                                    avatars.remove(groupId); synchronized(avatarInflight) { avatarLoadedFor.remove(groupId) }; load()
                                    val partial = bulkPartialError
                                    decodeAndStore(avatarResult.value, prepend = true) {
                                        if (partial != null) state = GroupState.Error(partial)
                                        done()
                                    }
                                    return@launch
                                }
                                is ApiResult.Error -> { media.delete(uploaded.value.url); android.util.Log.w("GroupViewModel", "Create avatar patch failed: ${avatarResult.message}") }
                            }
                        }
                    }
                    load()
                } else {
                    // AUTO-FIX (groups visibility): POST succeeded but the response id failed to
                    // decode — re-sync from the server so the created group is never lost.
                    android.util.Log.w("GroupViewModel", "create: id decode failed after successful POST; reloading groups")
                    load()
                }
                val partial = bulkPartialError
                // إزالة المتفائل ثم إدخال الحقيقي أعلى القائمة — ظهور فوري بلا وميض
                groups.removeAll { it.id == optimisticId }
                decodeAndStore(result.value, prepend = true) {
                    // الإعدادات التابعة للإنشاء: مزامنة slow-mode/disappearing الافتراضية من الخادم (كانت محلية فقط)
                    runCatching {
                        val realId = json.decodeFromString<Group>(result.value).id
                        syncSlowModeFromServer(realId)
                    }
                    if (partial != null) state = GroupState.Error(partial)
                    done()
                }
            }
            is ApiResult.Error -> {
                // فشل الإنشاء: إزالة المتفائل + رسالة واضحة
                groups.removeAll { it.id == optimisticId }
                state = GroupState.Error(result.message)
            }
        }
    }

    fun addMember(group: Group, redId: String, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/members", json.encodeToString(AddGroupMemberRequest(redId.trim().uppercase())))) {
            is ApiResult.Success -> decodeAndStore(result.value, done = done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /**
     * إضافة جماعية بتقرير عددي: تُستخدم من نافذة إضافة الأعضاء.
     * تعدّ النجاحات/الإخفاقات؛ إن فشل البعض تُحفظ المجموعة من آخر
     * استجابة ناجحة ثم تُضبط الحالة على رسالة جزئية عربية، وإلا Ready.
     * النتيجة عبر [done] ليعرضها المنادي (Toast) — لا Snackbar وهمية.
     */
    fun addMembers(group: Group, redIds: List<String>, done: (added: Int, total: Int) -> Unit) = viewModelScope.launch {
        val distinct = redIds.map { it.trim().uppercase() }.distinct().filter { it.isNotBlank() }
        if (distinct.isEmpty()) {
            android.util.Log.w("GroupViewModel", "addMembers called with empty list for ${group.id}")
            state = GroupState.Ready
            done(0, 0)
            return@launch
        }
        state = GroupState.Saving
        // تزامن محدود (BULK_ADD_MAX_CONCURRENCY) بدل forEach المتسلسلة — نفس الدلالات
        // العددية للتقارير. ملاحظة: lastSuccess = آخر استجابة ناجحة *اكتملت* لا الأحدث
        // على الخادم بالضرورة؛ أي لقطة ناجحة تكفي للتحديث الفوري، والقائمة القانونية
        // الكاملة تُجلب عبر load() اللاحق.
        val semaphore = Semaphore(BULK_ADD_MAX_CONCURRENCY)
        val results = distinct.map { redId ->
            async {
                semaphore.withPermit {
                    redId to client.request("POST", "/api/groups/${group.id}/members", json.encodeToString(AddGroupMemberRequest(redId)))
                }
            }
        }.awaitAll()
        var added = 0
        var lastSuccess: String? = null
        results.forEach { (redId, result) ->
            when (result) {
                is ApiResult.Success -> { added++; lastSuccess = result.value }
                is ApiResult.Error -> android.util.Log.w("GroupViewModel", "Bulk add failed for $redId: ${result.message}")
            }
        }
        val total = distinct.size
        val failed = total - added
        if (added == 0) {
            android.util.Log.w("GroupViewModel", "Bulk add all failed: 0/$total for ${group.id}")
            state = GroupState.Error("تعذر إضافة الأعضاء")
            done(0, total)
            return@launch
        }
        val payload = lastSuccess
        if (payload != null) {
            decodeAndStore(payload) {}
        } else {
            load()
        }
        if (failed > 0) {
            android.util.Log.w("GroupViewModel", "Bulk add partial: $added/$total for ${group.id}")
            state = GroupState.Error("تمت إضافة $added من $total — تعذر إضافة الباقي")
        } else {
            state = GroupState.Ready
        }
        done(added, total)
    }

    fun updateRole(group: Group, member: GroupMember, role: String) = viewModelScope.launch {
        // الخادم يقبل ADMIN/MODERATOR/MEMBER ويفرض OWNER — لا نحجب MODERATOR محلياً (كان مفقوداً من الواجهة).
        require(role == "ADMIN" || role == "MEMBER" || role == "MODERATOR")
        state = GroupState.Saving
        when (val result = client.request("PATCH", "/api/groups/${group.id}/members/${member.userId}", json.encodeToString(UpdateGroupRoleRequest(role)))) {
            is ApiResult.Success -> decodeAndStore(result.value) {}
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun removeMember(group: Group, member: GroupMember) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/members/${member.userId}")) {
            is ApiResult.Success -> decodeAndStore(result.value) {}
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /** LEGENDARY: حظر+طرد (الخادم جاهز POST /ban — كانت بلا زر عميل إطلاقاً) */
    fun banMember(group: Group, member: GroupMember, reason: String = "", done: () -> Unit = {}) = viewModelScope.launch {
        state = GroupState.Saving
        val body = org.json.JSONObject().put("userId", member.userId).put("reason", reason.take(200)).toString()
        when (val result = client.request("POST", "/api/groups/${group.id}/ban", body)) {
            is ApiResult.Success -> { decodeAndStore(result.value) {}; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /** LEGENDARY: إلغاء حظر */
    fun unbanMember(group: Group, userId: String, done: () -> Unit = {}) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/bans/$userId")) {
            is ApiResult.Success -> { decodeAndStore(result.value) {}; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /** LEGENDARY: ربط/فك مجتمع (مدير) */
    fun setCommunity(group: Group, communityId: String?, done: () -> Unit = {}) = viewModelScope.launch {
        val body = org.json.JSONObject().apply {
            if (communityId == null) put("communityId", org.json.JSONObject.NULL) else put("communityId", communityId)
        }.toString()
        when (val r = client.request("POST", "/api/groups/${group.id}/community", body)) {
            is ApiResult.Success -> { decodeAndStore(r.value) {}; done() }
            is ApiResult.Error -> state = GroupState.Error(r.message)
        }
    }

    /** LEGENDARY: علامة قراءة رتيبة — تُرسل عند فتح الدردشة/وصول جديد (أساس "من قرأ") */
    fun markGroupRead(group: Group, sequence: Long) = viewModelScope.launch {
        if (sequence <= 0) return@launch
        runCatching {
            client.request("POST", "/api/groups/${group.id}/read",
                org.json.JSONObject().put("sequence", sequence).toString())
        }
    }

    val groupReaders = mutableStateListOf<com.red.sovereign.features.chat.GroupReaderUi>()
    fun loadGroupReaders(group: Group, sequence: Long) = viewModelScope.launch {
        val enc = sequence.coerceAtLeast(0)
        when (val r = client.request("GET", "/api/groups/${group.id}/reads?sequence=$enc")) {
            is ApiResult.Success -> runCatching {
                json.decodeFromString<List<com.red.sovereign.features.chat.GroupReaderUi>>(r.value)
            }.onSuccess { groupReaders.clear(); groupReaders.addAll(it) }
            is ApiResult.Error -> Unit
        }
    }

    /** مفتاح آخر رابط حُمل لكل مجموعة — يكشف تغيّر الصورة فيُعيد التحميل بدل الكاش العالق. */
    private val avatarLoadedFor = mutableMapOf<String, String>()
    /** طلبات جارية لمنع عاصفة تنزيلات عند إعادة التركيب السريعة (LazyColumn). */
    private val avatarInflight = mutableSetOf<String>()

    fun loadAvatar(group: Group) {
        val raw = group.avatarUrl?.ifBlank { null } ?: return
        // http(s) مباشر تعرضه Coil في SovereignGroupAvatar — لا تنزيل مصدّق هنا.
        if (raw.startsWith("http://") || raw.startsWith("https://")) return
        synchronized(avatarInflight) {
            // نفس الرابط محمّل مسبقًا → لا عمل. رابط جديد → اسمح بإعادة التحميل.
            if (avatarLoadedFor[group.id] == raw && avatars.containsKey(group.id)) return
            if (!avatarInflight.add(group.id)) return
        }
        viewModelScope.launch {
            try {
                // objectKey خام أو مسار كامل — وحّد إلى /api/media/... (مثل postMediaPath).
                val path = if (raw.startsWith("/api/media/")) raw else "/api/media/$raw"
                when (val response = media.download(path, 10 * 1024 * 1024)) {
                    is ApiResult.Success -> kotlinx.coroutines.withContext(Dispatchers.IO) {
                        runCatching {
                            BitmapFactory.decodeByteArray(response.value, 0, response.value.size)
                        }.getOrNull()?.let { bmp ->
                            val img = bmp.asImageBitmap()
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                // سباق: إن تغيّر الرابط أثناء التنزيل تجاهل النتيجة القديمة.
                                // (المقارنة على نسخة group الملتقطة؛ الأحدث سيُطلب بدوره).
                                avatars[group.id] = img
                                avatarLoadedFor[group.id] = raw
                            }
                        }
                    }
                    is ApiResult.Error -> android.util.Log.w("GroupViewModel", "Avatar download failed for ${group.id}: ${response.message}")
                }
            } finally {
                synchronized(avatarInflight) { avatarInflight.remove(group.id) }
            }
        }
    }

    fun updateAvatar(group: Group, uri: Uri) = viewModelScope.launch {
        state = GroupState.Saving
        when (val uploaded = media.upload(uri)) {
            is ApiResult.Error -> state = GroupState.Error(uploaded.message)
            is ApiResult.Success -> when (val response = client.request("PATCH", "/api/groups/${group.id}/avatar", json.encodeToString(UpdateGroupAvatarRequest(uploaded.value.objectKey)))) {
                is ApiResult.Success -> {
                    avatars.remove(group.id)
                    synchronized(avatarInflight) { avatarLoadedFor.remove(group.id) }
                    decodeAndStore(response.value) {}
                }
                is ApiResult.Error -> { media.delete(uploaded.value.url); state = GroupState.Error(response.message) }
            }
        }
    }

    fun updateSettings(group: Group, settings: GroupSettings) = viewModelScope.launch {
        state = GroupState.Saving
        // تُرسل الأعلام السبعة كاملة (كان يرسل 3 فقط فيعيد الخادم الباقي للافتراضي).
        val request = UpdateGroupSettingsRequest(
            settings.onlyAdminsCanSend, settings.onlyAdminsCanEditInfo, settings.requireJoinApproval,
            settings.onlyAdminsCanAddMembers, settings.onlyAdminsCanInvite, settings.onlyAdminsCanPin, settings.onlyAdminsCanCall
        )
        when (val result = client.request("PATCH", "/api/groups/${group.id}/settings", json.encodeToString(request))) {
            is ApiResult.Success -> decodeAndStore(result.value) {}
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    var invites = mutableStateListOf<GroupInviteResponse>(); private set

    fun createInvite(group: Group, requireApproval: Boolean = true, expiresHours: Long = 24, maxUses: Int = 10) = viewModelScope.launch {
        state = GroupState.Saving
        val effectiveApproval = requireApproval || group.settings.requireJoinApproval
        val safeHours = expiresHours.coerceIn(1, 168)
        val safeUses = maxUses.coerceIn(1, 100)
        when (val result = client.request("POST", "/api/groups/${group.id}/invites", json.encodeToString(CreateGroupInviteRequest(expiresHours = safeHours, maxUses = safeUses, requireApproval = effectiveApproval)))) {
            is ApiResult.Success -> runCatching { json.decodeFromString<GroupInviteResponse>(result.value) }.onSuccess { latestInvite = it; invites.add(0, it); state = GroupState.Ready }.onFailure { state = GroupState.Error("INVALID_INVITE_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun listInvites(group: Group) = viewModelScope.launch {
        when (val result = client.request("GET", "/api/groups/${group.id}/invites")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<GroupInviteResponse>>(result.value) }
                .onSuccess { invites.clear(); invites.addAll(it) }
                .onFailure { e -> android.util.Log.w("GroupViewModel", "Invalid invites response for ${group.id}", e) }
            is ApiResult.Error -> android.util.Log.w("GroupViewModel", "List invites failed for ${group.id}: ${result.message}")
        }
    }

    fun revokeInvite(group: Group, inviteId: String) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/invites/$inviteId")) {
            is ApiResult.Success -> { invites.removeAll { it.id == inviteId }; if (latestInvite?.id == inviteId) latestInvite = null; state = GroupState.Ready }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun joinWithToken(rawToken: String, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        // LEGENDARY: يقبل QR/Rابط/رمز (كان رابطاً فقط — QR يُرفض)
        val token = parseInviteTokenQrAware(rawToken)
        if (token.isBlank()) {
            state = GroupState.Error("رمز الدعوة فارغ")
            return@launch
        }
        when (val result = client.request("POST", "/api/groups/join-requests", json.encodeToString(JoinGroupRequest(token)))) {
            is ApiResult.Success -> { state = GroupState.Ready; load(); done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /** LEGENDARY: معاينة قبل الانضمام — تُستخدم من حوار الانضمام (لا انضمام أعمى) */
    suspend fun previewInvite(token: String): ApiResult<String> {
        val clean = parseInviteTokenQrAware(token)
        if (clean.isBlank()) return ApiResult.Error(null, "رمز الدعوة فارغ")
        val enc = java.net.URLEncoder.encode(clean, "UTF-8")
        return client.request("GET", "/api/groups/invites/preview?token=$enc")
    }

    fun loadJoinRequests(group: Group) = viewModelScope.launch {
        when (val result = client.request("GET", "/api/groups/${group.id}/join-requests")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<List<GroupJoinRequestResponse>>(result.value) }.onSuccess { joinRequests.clear(); joinRequests.addAll(it) }.onFailure { state = GroupState.Error("INVALID_JOIN_REQUEST_RESPONSE") }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun resolveJoin(group: Group, request: GroupJoinRequestResponse, approve: Boolean) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/join-requests/${request.id}", json.encodeToString(ResolveJoinRequest(approve)))) {
            is ApiResult.Success -> { decodeAndStore(result.value) {}; joinRequests.removeAll { it.id == request.id } }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun clearInvite() { latestInvite = null }

    // ── LEGENDARY: استطلاعات المجموعات (تجميع خادم: منع مزدوج/إخفاء/مهلة/إغلاق) ──
    val polls = mutableStateListOf<com.red.sovereign.features.chat.GroupPollUi>()
    fun loadPolls(group: Group) = viewModelScope.launch {
        when (val r = client.request("GET", "/api/groups/${group.id}/polls")) {
            is ApiResult.Success -> runCatching {
                json.decodeFromString<List<com.red.sovereign.features.chat.GroupPollUi>>(r.value)
            }.onSuccess { polls.clear(); polls.addAll(it) }
            is ApiResult.Error -> state = GroupState.Error(r.message)
        }
    }
    fun createPoll(group: Group, question: String, options: List<String>, hideResults: Boolean = false, closesInMinutes: Long? = null, done: () -> Unit = {}) = viewModelScope.launch {
        val body = org.json.JSONObject().put("question", question.trim())
            .put("options", org.json.JSONArray(options.map { it.trim() }.filter { it.isNotEmpty() }))
            .put("hideResults", hideResults)
            .apply { if (closesInMinutes != null) put("closesInMinutes", closesInMinutes) }.toString()
        when (val r = client.request("POST", "/api/groups/${group.id}/polls", body)) {
            is ApiResult.Success -> { loadPolls(group); done() }
            is ApiResult.Error -> state = GroupState.Error(r.message)
        }
    }
    fun votePoll(group: Group, pollId: String, indexes: List<Int>) = viewModelScope.launch {
        val body = org.json.JSONObject().put("optionIndexes", org.json.JSONArray(indexes)).toString()
        when (val r = client.request("POST", "/api/groups/${group.id}/polls/$pollId/vote", body)) {
            is ApiResult.Success -> loadPolls(group)
            is ApiResult.Error -> state = GroupState.Error(r.message)
        }
    }
    fun closePoll(group: Group, pollId: String) = viewModelScope.launch {
        when (val r = client.request("POST", "/api/groups/${group.id}/polls/$pollId/close", "{}")) {
            is ApiResult.Success -> loadPolls(group)
            is ApiResult.Error -> state = GroupState.Error(r.message)
        }
    }

    fun transferOwnership(group: Group, member: GroupMember, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("POST", "/api/groups/${group.id}/transfer-ownership", json.encodeToString(TransferGroupOwnershipRequest(member.userId)))) {
            is ApiResult.Success -> decodeAndStore(result.value, done = done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    /**
     * تعديل معلومات المجموعة (اسم/وصف) عبر PATCH /api/groups/{id}.
     * الحقول الفارغة تُحذف من الطلب (null) فيُبقي الخادم القيمة الحالية.
     */
    fun updateInfo(group: Group, name: String, description: String?, done: () -> Unit = {}) = viewModelScope.launch {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            state = GroupState.Error("اسم المجموعة فارغ")
            return@launch
        }
        state = GroupState.Saving
        val cleanDesc = description?.trim()?.takeIf { it.isNotEmpty() }
        when (val result = client.request("PATCH", "/api/groups/${group.id}", json.encodeToString(UpdateGroupInfoRequest(cleanName, cleanDesc)))) {
            is ApiResult.Success -> decodeAndStore(result.value, done = done)
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun updateDisappearing(group: Group, durationMs: Long?) = viewModelScope.launch {
        val secs = durationMs?.let { it / 1000 }
        // بناء يدوي لضمان إرسال null صريح عند الإيقاف (explicitNulls=false كان سيُسقط المفتاح).
        // الخادم يعيد Map {groupId, disappearAfterSeconds, enabled} وليس Group كاملة،
        // و Group المحلي بلا حقل disappearing — لذا نُعيد التحميل لمزامنة groups.
        val payload = if (secs == null) """{"durationSeconds":null}""" else """{"durationSeconds":$secs}"""
        when (val result = client.request("PATCH", "/api/groups/${group.id}/disappearing", payload)) {
            is ApiResult.Success -> { state = GroupState.Ready; load() }
            is ApiResult.Error -> android.util.Log.w("GroupViewModel", "disappearing sync failed: ${result.message}")
        }
    }

    fun deleteGroup(group: Group, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}")) {
            is ApiResult.Success -> { groups.removeAll { it.id == group.id }; state = GroupState.Ready; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    fun leave(group: Group, done: () -> Unit) = viewModelScope.launch {
        state = GroupState.Saving
        when (val result = client.request("DELETE", "/api/groups/${group.id}/membership")) {
            is ApiResult.Success -> { groups.removeAll { it.id == group.id }; state = GroupState.Ready; done() }
            is ApiResult.Error -> state = GroupState.Error(result.message)
        }
    }

    private fun decodeAndStore(value: String, prepend: Boolean = false, done: () -> Unit) {
        runCatching { json.decodeFromString<Group>(value) }
            .onSuccess { updated ->
                groups.indexOfFirst { it.id == updated.id }.takeIf { it >= 0 }?.let { groups[it] = updated }
                    ?: if (prepend) groups.add(0, updated) else groups.add(updated)
                state = GroupState.Ready
                done()
            }
            .onFailure {
                android.util.Log.w("GroupViewModel", "decodeAndStore failed: ${it.message}")
                state = GroupState.Error("INVALID_GROUP_RESPONSE")
                done()
            }
    }

    /** مزامنة إعدادات ما بعد الإنشاء من الخادم (slow-mode/archived) — كانت تُقرأ محلياً فقط فيُتجاوزها المهاجم */
    private fun syncSlowModeFromServer(groupId: String) = viewModelScope.launch {
        when (val r = client.request("GET", "/api/groups/$groupId/slow-mode")) {
            is ApiResult.Success -> runCatching {
                val obj = org.json.JSONObject(r.value)
                val secs = obj.optInt("slowModeSeconds", 0)
                com.red.sovereign.features.chat.setGroupSlowModeSeconds(getApplication(), groupId, secs)
            }
            else -> Unit
        }
    }
}

sealed interface GroupState { data object Loading:GroupState; data object Saving:GroupState; data object Ready:GroupState; data class Error(val message:String):GroupState }

