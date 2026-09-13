package com.red.sovereign.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.InlinePoll
import com.red.sovereign.ui.ChatPollVoteStore
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.delay

/**
 * ════════════════════════════════════════════════════════════════════════
 *  PollsAdvanced — استطلاعات الدردشة القوية (P1-C)
 * ════════════════════════════════════════════════════════════════════════
 *
 *  طبقة عرض/منطق فوق [ChatPollVoteStore] الموجود (لا مخزن جديد منافس،
 *  لا تعديل على InlinePoll/RichMessage حتى لا ينكسر البناء ولا تتعارض
 *  الوكلاء المتوازيون).
 *
 *  الميزات:
 *   - endTime: مؤقت إغلاق تلقائي (countdown + إغلاق بصري + onAutoClose)
 *   - isAnonymous: إخفاء أسماء المصوتين (عرض إجماليات فقط)
 *   - allowMultiple: اختيار متعدد (مخزن مجموعات محلي + مرآة للمتوافق)
 *   - نتائج بيانية: شريط + نسبة % + عدد لكل خيار
 *   - منع التصويت المزدوج: ناخب واحد = صوت واحد (أو مجموعة واحدة)
 *   - زر إغلاق للمنشئ فقط
 *
 *  نقل الحقول E2EE: [InlinePoll] الحالي (question/options/pollId/isClosed/votes)
 *  لا يحمل الحقول الجديدة بعد. [PollAdvancedRegistry] يحمل مواصفة الاستطلاعات
 *  المنشأة محليًا، و[resolveSpec] يقرأ أيضًا أي حقول مستقبلية من JSON الخام
 *  (endTime/isAnonymous/allowMultiple) عند إضافتها لاحقًا دون تعديل هنا.
 */

// ─── المواصفة ─────────────────────────────────────────────────────────────

/** مواصفة الاستطلاع المتقدم — تُسجَّل لحظة الإنشاء وتُحل عند العرض. */
data class PollAdvancedSpec(
    /** مؤقت الإغلاق التلقائي (epoch millis) — null = بلا مؤقت. */
    val endTimeMillis: Long? = null,
    /** إخفاء أسماء المصوتين — عرض إجماليات فقط. */
    val isAnonymous: Boolean = false,
    /** السماح باختيارات متعددة. */
    val allowMultiple: Boolean = false
)

/** مسودة الإنشاء من الحوار. */
data class PollAdvancedDraft(
    val question: String,
    val options: List<String>,
    val spec: PollAdvancedSpec
)

/** نتيجة خيار واحد للعرض البياني. */
data class PollOptionResult(
    val index: Int,
    val text: String,
    val votes: Int,
    val percent: Int
)

/**
 * سجل المواصفات للاستطلاعات المنشأة من هذا الجهاز (pollId -> Spec).
 * Fallback حتى تُضاف الحقول إلى InlinePoll نفسه.
 */
object PollAdvancedRegistry {
    private val specs = androidx.compose.runtime.mutableStateMapOf<String, PollAdvancedSpec>()

    fun register(pollId: String, spec: PollAdvancedSpec) {
        if (pollId.isNotBlank()) specs[pollId] = spec
    }

    fun get(pollId: String): PollAdvancedSpec? = specs[pollId]

    fun clear(pollId: String) {
        specs.remove(pollId)
    }
}

/**
 * مخزن المجموعات للاختيار المتعدد (pollId -> ناخب -> مجموعة الفهارس).
 * [ChatPollVoteStore] أحادي (ناخب -> فهرس) فيُستخدم للتوافق + الأحادي،
 * وهذا المخزن يحمل المجموعات الكاملة عند allowMultiple.
 */
object PollAdvancedMultiStore {
    private val multi = androidx.compose.runtime.mutableStateMapOf<String, Map<String, Set<Int>>>()

    fun get(pollId: String, voter: String): Set<Int> =
        multi[pollId]?.get(voter) ?: emptySet()

    fun set(pollId: String, voter: String, indices: Set<Int>) {
        val next = (multi[pollId] ?: emptyMap()).toMutableMap()
        if (indices.isEmpty()) next.remove(voter) else next[voter] = indices.toSet()
        multi[pollId] = next.toMap()
    }

    fun toggle(pollId: String, voter: String, index: Int, optionCount: Int): Set<Int> {
        if (index !in 0 until optionCount) return get(pollId, voter)
        val cur = get(pollId, voter).toMutableSet()
        if (!cur.add(index)) cur.remove(index)
        set(pollId, voter, cur)
        return cur.toSet()
    }

    fun voterIds(pollId: String): Set<String> = multi[pollId]?.keys?.toSet() ?: emptySet()

    fun counts(pollId: String, optionCount: Int): List<Int> {
        val arr = IntArray(optionCount)
        multi[pollId]?.values?.forEach { set -> set.forEach { i -> if (i in arr.indices) arr[i]++ } }
        return arr.toList()
    }

    fun voterCount(pollId: String): Int = multi[pollId]?.size ?: 0
}

// ─── منطق خالص (قابل للتحقق offline) ─────────────────────────────────────

/** مغلق؟ علم المنشئ أو انتهاء المؤقت. */
fun pollAdvancedIsClosed(
    poll: InlinePoll,
    spec: PollAdvancedSpec? = null,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (poll.isClosed) return true
    val end = spec?.endTimeMillis
    if (end != null && nowMillis >= end) return true
    return false
}

/** حل المواصفة: السجل المحلي أولًا، ثم حقول JSON المستقبلية، ثم الافتراضي. */
fun resolveAdvancedSpec(poll: InlinePoll, rawJson: String? = null): PollAdvancedSpec {
    PollAdvancedRegistry.get(poll.pollId)?.let { return it }
    if (!rawJson.isNullOrBlank()) {
        runCatching {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(rawJson) as? kotlinx.serialization.json.JsonObject
            val end = obj?.get("endTime")?.toString()?.trim('"')?.toLongOrNull()
                ?: (obj?.get("endTimeMillis")?.toString()?.trim('"')?.toLongOrNull())
            val anon = obj?.get("isAnonymous")?.toString() == "true"
            val multi = obj?.get("allowMultiple")?.toString() == "true"
            if (end != null || anon || multi) return PollAdvancedSpec(end, anon, multi)
        }
    }
    return PollAdvancedSpec()
}

/**
 * نتائج بيانية مدمجة: لقطة المنشئ (poll.votes) + الوارد الجديد.
 * أحادي: [ChatPollVoteStore] مصدر الأصوات الجديدة (يمنع المزدوج بطبيعته:
 * ناخب واحد = مدخل واحد يُكتب فوقه).
 * متعدد: [PollAdvancedMultiStore] مصدر المجموعات + أصوات أحادية قديمة
 * من ناخبين لم ينتقلوا بعد (تُحتسب مرة واحدة فقط).
 */
fun computeAdvancedResults(
    poll: InlinePoll,
    spec: PollAdvancedSpec,
    singleCounts: List<Int>,
    multiCounts: List<Int>,
    legacySingleVoters: Set<String>
): List<PollOptionResult> {
    val n = poll.options.size
    val base = poll.votes
    val merged = IntArray(n) { i -> base.getOrElse(i) { 0 } }
    if (spec.allowMultiple) {
        // أصوات المجموعات الجديدة.
        multiCounts.forEachIndexed { i, c -> if (i in merged.indices) merged[i] += c }
        // ناخبون قدامى صوتوا أحاديًا قبل تفعيل المتعدد ولم يدخلوا مخزن
        // المجموعات: تُحتسب أصواتهم الأحادية دون مضاعفة (هم خارج multi).
        // (singleCounts هنا يجب أن تُمرَّر مخصومًا منها ناخبو multi —
        //  الدالة المساعدة أدناه تفعل ذلك.)
        singleCounts.forEachIndexed { i, c -> if (i in merged.indices) merged[i] += c }
    } else {
        singleCounts.forEachIndexed { i, c -> if (i in merged.indices) merged[i] += c }
    }
    val total = merged.sum().coerceAtLeast(1)
    return poll.options.mapIndexed { i, text ->
        val v = merged.getOrElse(i) { 0 }
        PollOptionResult(i, text, v, ((v.toFloat() / total) * 100).toInt())
    }
}

/** هل يحق للناخب التصويت الآن؟ (مغلق/منتهٍ/بلا خيارات = لا). */
fun pollAdvancedCanVote(
    poll: InlinePoll,
    spec: PollAdvancedSpec,
    nowMillis: Long = System.currentTimeMillis()
): Boolean = !pollAdvancedIsClosed(poll, spec, nowMillis) && poll.options.isNotEmpty()

/**
 * تسجيل تصويت أحادي مع منع المزدوج: يُكتب فوق التصويت السابق
 * (تغيير الرأي مسموح، التراكم ممنوع). يعيد false إن كان مغلقًا.
 * يكتب أيضًا في [ChatPollVoteStore] للتوافق مع البطاقة القديمة.
 */
fun recordAdvancedSingleVote(
    poll: InlinePoll,
    spec: PollAdvancedSpec,
    voter: String,
    optionIndex: Int?,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    if (!pollAdvancedCanVote(poll, spec, nowMillis)) return false
    if (optionIndex != null && optionIndex !in poll.options.indices) return false
    // الضغط على خياري الحالي = سحب الصوت (toggle) — لا صوت مزدوج أبدًا.
    ChatPollVoteStore.record(poll.pollId, voter, optionIndex)
    return true
}

/**
 * تبديل خيار في الوضع المتعدد مع منع المزدوج داخل المجموعة.
 * يعيد المجموعة الجديدة، أو null إن كان مغلقًا. يُرآة أول عنصر
 * في [ChatPollVoteStore] لتبقى البطاقة القديمة متسقة بصريًا.
 */
fun toggleAdvancedMultiVote(
    poll: InlinePoll,
    spec: PollAdvancedSpec,
    voter: String,
    optionIndex: Int,
    nowMillis: Long = System.currentTimeMillis()
): Set<Int>? {
    if (!spec.allowMultiple) return null
    if (!pollAdvancedCanVote(poll, spec, nowMillis)) return null
    val next = PollAdvancedMultiStore.toggle(poll.pollId, voter, optionIndex, poll.options.size)
    ChatPollVoteStore.record(poll.pollId, voter, next.minOrNull())
    return next
}

/** تسمية العد التنازلي للمؤقت — null إن بلا مؤقت. */
fun pollAdvancedRemainingLabel(endTimeMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String? {
    if (endTimeMillis == null) return null
    val remaining = endTimeMillis - nowMillis
    if (remaining <= 0) return "مغلق — انتهى الوقت"
    val minutes = remaining / 60000
    return when {
        minutes < 1 -> "ينتهي خلال أقل من دقيقة ⏳"
        minutes < 60 -> "ينتهي خلال $minutes دقيقة ⏳"
        minutes < 1440 -> "ينتهي خلال ${minutes / 60} ساعة ⏳"
        else -> "ينتهي خلال ${minutes / 1440} يوم ⏳"
    }
}

/** اسم العرض مع إخفاء الهوية. */
fun pollAdvancedVoterLabel(redId: String, isAnonymous: Boolean): String =
    if (isAnonymous) "مصوّت مجهول" else redId

/** بناء استطلاع جديد + تسجيل مواصفته — يستدعيها المنشئ من الحوار. */
fun buildAdvancedPoll(
    question: String,
    options: List<String>,
    spec: PollAdvancedSpec
): InlinePoll {
    val clean = options.map { it.trim() }.filter { it.length >= 2 }.take(6)
    require(question.trim().isNotBlank()) { "EMPTY_QUESTION" }
    require(clean.size >= 2) { "NEED_TWO_OPTIONS" }
    val poll = InlinePoll(
        question = question.trim().take(280),
        options = clean,
        pollId = "poll-${System.currentTimeMillis()}"
    )
    PollAdvancedRegistry.register(poll.pollId, spec)
    return poll
}

// ─── بطاقة العرض ─────────────────────────────────────────────────────────

/**
 * بطاقة الاستطلاع المتقدمة: نتائج بيانية (شريط + % + عدد)، مؤقت إغلاق
 * تلقائي، إخفاء أسماء عند isAnonymous، منع تصويت مزدوج، زر إغلاق للمنشئ.
 *
 * @param poll الاستطلاع الوارد (E2EE).
 * @param spec المواصفة (من [resolveAdvancedSpec] أو السجل) — null = افتراضي.
 * @param myRedId معرفي الحالي (لمنع المزدوج + تظليل صوتي).
 * @param isCreator true إن كنت منشئ الاستطلاع (يُظهر زر الإغلاق).
 * @param onVote تُستدعى عند (سحب/تغيير) التصويت الأحادي — يرسلها المتصل POLL_VOTE.
 * @param onMultiVote تُستدعى عند تبديل خيار متعدد (pollId, المجموعة الجديدة).
 * @param onClose تُستدعى عند ضغط المنشئ «إغلاق» — يبث المتصل نسخة isClosed=true.
 */
@Composable
fun PollAdvancedCard(
    poll: InlinePoll,
    spec: PollAdvancedSpec? = null,
    myRedId: String? = null,
    isCreator: Boolean = false,
    onVote: ((pollId: String, optionIndex: Int?) -> Unit)? = null,
    onMultiVote: ((pollId: String, indices: Set<Int>) -> Unit)? = null,
    onClose: ((pollId: String) -> Unit)? = null
) {
    val resolved = spec ?: PollAdvancedRegistry.get(poll.pollId) ?: PollAdvancedSpec()
    // تيك العد التنازلي: إعادة حساب كل 30 ثانية + إغلاق تلقائي بصري.
    var now by remember(poll.pollId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(poll.pollId, resolved.endTimeMillis) {
        val end = resolved.endTimeMillis ?: return@LaunchedEffect
        while (System.currentTimeMillis() < end) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    val closed = pollAdvancedIsClosed(poll, resolved, now)
    val canInteract = onVote != null && myRedId != null && !closed

    // الأصوات الجديدة من المخزن الموجود (أحادي) + مخزن المجموعات (متعدد).
    val singleCounts = ChatPollVoteStore.counts(poll.pollId, poll.options.size)
    // ناخبو multi يُخصم مرآتهم الأحادية عند الدمج (انظر effectiveSingle).
    val multiCounts = PollAdvancedMultiStore.counts(poll.pollId, poll.options.size)
    // خصم المرآة: toggleAdvancedMultiVote يعكس minOrNull في ChatPollVoteStore
    // لتبقى البطاقة القديمة متسقة — نخصمها هنا حتى لا يُحتسب الصوت مرتين.
    val effectiveSingle: List<Int> = if (resolved.allowMultiple) {
        // محافظ: لا أصوات سالبة أبدًا.
        singleCounts.mapIndexed { i, c -> (c - multiCounts.getOrElse(i) { 0 }).coerceAtLeast(0) }
    } else singleCounts
    val results = computeAdvancedResults(poll, resolved, effectiveSingle, multiCounts, emptySet())
    val totalVotes = results.sumOf { it.votes }
    val mySingle = if (myRedId != null) ChatPollVoteStore.myVote(poll.pollId, myRedId) else null
    val myMulti: Set<Int> = if (myRedId != null && resolved.allowMultiple) {
        PollAdvancedMultiStore.get(poll.pollId, myRedId)
    } else emptySet()

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📊 استطلاع", style = MaterialTheme.typography.labelMedium, color = YounesEmerald, fontWeight = FontWeight.Bold)
            Text(poll.question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

            // شارات الحالة: مجهول / متعدد / مؤقت.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (resolved.isAnonymous) Text("🙈 مجهول", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (resolved.allowMultiple) Text("☑️ اختيار متعدد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (closed) Text("🔒 مغلق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            pollAdvancedRemainingLabel(resolved.endTimeMillis, now)?.let { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // الخيارات بنتائج بيانية: شريط + نسبة + عدد.
            results.forEach { r ->
                val ratio = if (totalVotes > 0) (r.votes.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f) else 0f
                val selected = if (resolved.allowMultiple) myMulti.contains(r.index) else mySingle == r.index
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!canInteract || myRedId == null) return@Card
                        if (resolved.allowMultiple) {
                            val next = toggleAdvancedMultiVote(poll, resolved, myRedId, r.index)
                            if (next != null) {
                                onMultiVote?.invoke(poll.pollId, next)
                                // توافق: أول اختيار يُبث أيضًا كـ POLL_VOTE أحادي.
                                onVote?.invoke(poll.pollId, next.minOrNull())
                            }
                        } else {
                            val next = if (mySingle == r.index) null else r.index
                            if (recordAdvancedSingleVote(poll, resolved, myRedId, next)) {
                                onVote?.invoke(poll.pollId, next)
                            }
                        }
                    },
                    enabled = canInteract,
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) YounesEmerald.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                r.text,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${r.percent}% • ${r.votes}",
                                color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                            color = YounesEmerald,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }

            // التذييل: إجمالي + صوتي (يُخفى الاسم عند isAnonymous).
            val myLabel = when {
                myRedId == null -> "لا شيء"
                resolved.allowMultiple -> if (myMulti.isEmpty()) "لا شيء" else myMulti.sorted().mapNotNull { poll.options.getOrNull(it) }.joinToString("، ")
                else -> mySingle?.let { poll.options.getOrNull(it) } ?: "لا شيء"
            }
            Text(
                if (resolved.isAnonymous) "إجمالي الأصوات: $totalVotes"
                else "إجمالي الأصوات: $totalVotes • صوتك: $myLabel",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!resolved.isAnonymous && myRedId != null && mySingle != null && !resolved.allowMultiple) {
                Text(
                    "ناخب: ${pollAdvancedVoterLabel(myRedId, false)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // زر إغلاق للمنشئ فقط.
            if (isCreator && !closed && onClose != null) {
                OutlinedButton(
                    onClick = { onClose(poll.pollId) },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("إغلاق الاستطلاع 🔒", fontSize = 12.sp) }
            }
        }
    }
}

// ─── حوار الإنشاء ─────────────────────────────────────────────────────────

/** خيارات المؤقت الجاهزة للمنشئ. */
enum class PollAdvancedDuration(val label: String, val millis: Long?) {
    NONE("بلا مؤقت", null),
    HOUR("ساعة", 3_600_000L),
    DAY("24 ساعة", 86_400_000L),
    WEEK("7 أيام", 604_800_000L)
}

/**
 * حوار إنشاء استطلاع متقدم: سؤال + 2..6 خيارات + مؤقت + مجهول + متعدد.
 * يُرجع [PollAdvancedDraft] — يبني المتصل منها InlinePoll عبر [buildAdvancedPoll]
 * ويرسلها عبر قناته الحالية (sendGroupRichText) دون أي API جديد.
 */
@Composable
fun PollAdvancedCreateDialog(
    onDismiss: () -> Unit,
    onConfirm: (PollAdvancedDraft) -> Unit
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var duration by remember { mutableStateOf(PollAdvancedDuration.NONE) }
    var isAnonymous by remember { mutableStateOf(false) }
    var allowMultiple by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استطلاع متقدم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    question, { question = it.take(280) },
                    Modifier.fillMaxWidth(), label = { Text("السؤال") }, maxLines = 3
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            options = options.toMutableList().also { it[index] = next.take(80) }
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("الخيار ${index + 1}") },
                        singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        { if (options.size < 6) options = options + "" },
                        Modifier.weight(1f), enabled = options.size < 6
                    ) { Text("+ خيار") }
                    OutlinedButton(
                        { if (options.size > 2) options = options.dropLast(1) },
                        Modifier.weight(1f), enabled = options.size > 2
                    ) { Text("- خيار") }
                }
                Text("مؤقت الإغلاق التلقائي", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PollAdvancedDuration.values().forEach { d ->
                        FilterChip(
                            selected = duration == d,
                            onClick = { duration = d },
                            label = { Text(d.label, fontSize = 11.sp) }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isAnonymous, onCheckedChange = { isAnonymous = it })
                    Text("  مجهول (إخفاء أسماء المصوتين)", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = allowMultiple, onCheckedChange = { allowMultiple = it })
                    Text("  اختيار متعدد", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            val valid = question.isNotBlank() && options.count { it.trim().length >= 2 } >= 2
            Button(
                enabled = valid,
                onClick = {
                    val end = duration.millis?.let { System.currentTimeMillis() + it }
                    onConfirm(
                        PollAdvancedDraft(
                            question = question.trim(),
                            options = options.map { it.trim() }.filter { it.length >= 2 },
                            spec = PollAdvancedSpec(
                                endTimeMillis = end,
                                isAnonymous = isAnonymous,
                                allowMultiple = allowMultiple
                            )
                        )
                    )
                }
            ) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
