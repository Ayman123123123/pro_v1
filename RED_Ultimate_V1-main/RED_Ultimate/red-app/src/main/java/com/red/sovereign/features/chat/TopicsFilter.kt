package com.red.sovereign.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * P1-A — فلترة المواضيع (Topics Filter).
 *
 * منطق نقي + واجهة Material3 معزولة في ملف جديد فقط (لا يعدّل أي ملف قائم).
 *
 * الربط لاحقاً (بدون تعديل RichMessage الأصلي):
 * - ChatsScreen يستخرج hashtags من RichMessage (مثلاً: richMessage.hashtags
 *   أو via HashtagParser.extract(rawText)) إلى List<String>.
 * - يحوّلها هنا عبر [extractTopicsFromHashtags] إلى List<TopicSpec>.
 * - يعرضها عبر [TopicsChipsRow] فوق قائمة الرسائل.
 * - عند اختيار topic يستدعي [filterByTopic] على List<Pair(messageId, hashtags)>
 *   ليعرض فقط الرسائل المطابقة، و topic == null يعني "الكل" (بدون فلترة).
 * - RichMessage الأصلي يبقى كما هو (read-only) — هذا الملف يعمل على نسخ
 *   Pair(messageId, hashtags) فقط.
 */

/** واصف موضوع واحد مشتق من هاشتاغ. id = النص المطبّع بدون '#'، label = النص للعرض مع '#'. */
data class TopicSpec(
    val id: String,
    val label: String
)

/**
 * يطبّع قائمة هاشتاغات خام إلى مواضيع فريدة مرتبة.
 * - يتجاهل الفراغات والمدخلات الفارغة.
 * - يزيل '#' البادئة إن وجدت، ويطبّع بـ trim + lowercase.
 * - يزيل التكرار مع الحفاظ على أول ظهور، ثم يرتب أبجدياً لثبات العرض/الاختبار.
 */
fun extractTopicsFromHashtags(hashtags: List<String>): List<TopicSpec> {
    return hashtags
        .asSequence()
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .map { it.lowercase() }
        .distinct()
        .sorted()
        .map { normalized -> TopicSpec(id = normalized, label = "#$normalized") }
        .toList()
}

/**
 * فلترة نقية قابلة للاختبار: تعيد الرسائل المطابقة للموضوع.
 * @param messages قائمة Pair(messageId, hashtags) — hashtags قد تتضمن '#' أو لا.
 * @param topic معرّف الموضوع (مطبّع بدون '#')، أو null/blank = بدون فلترة (يعيد الكل).
 * @return القائمة الفرعية المطابقة بنفس الترتيب الأصلي.
 */
fun filterByTopic(
    messages: List<Pair<String, List<String>>>,
    topic: String?
): List<Pair<String, List<String>>> {
    val normalized = topic?.trim()?.removePrefix("#")?.trim()?.lowercase()
    if (normalized.isNullOrEmpty()) return messages
    return messages.filter { (_, tags) ->
        tags.any { tag ->
            tag.trim().removePrefix("#").trim().lowercase() == normalized
        }
    }
}

/**
 * صف شرائح المواضيع بـ Material3 FilterChip.
 * @param topics مواضيع مستخرجة عبر [extractTopicsFromHashtags].
 * @param selected المعرّف المختار حالياً (topic id) أو null = الكل.
 * @param onSelect يُستدعى بالمعرّف الجديد أو null عند إلغاء الاختيار / اختيار "الكل".
 */
@Composable
fun TopicsChipsRow(
    topics: List<TopicSpec>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (topics.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("الكل") }
            )
        }
        items(topics, key = { it.id }) { topic ->
            FilterChip(
                selected = selected == topic.id,
                onClick = {
                    if (selected == topic.id) onSelect(null) else onSelect(topic.id)
                },
                label = { Text(topic.label) }
            )
        }
    }
}
