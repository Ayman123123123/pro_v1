package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardSearch — بحث الدليل مع debounce (مستخرج من RedDashboard.kt)
 * ════════════════════════════════════════════════════════════════════════
 *
 *  الأصل: `ChatHubScreen` كان يحمل `var directoryQuery` + زر «بحث آمن»
 *  يدوي فقط (سطر 2726-2727)، بلا debounce — كل ضغطة = طلب شبكة، ولا بحث
 *  تلقائي أثناء الكتابة. `messageSearchQuery` كان بلا debounce أيضًا
 *  (سطر 2707: LaunchedEffect مباشر على كل حرف).
 *
 *  التطوير (ممنوع الحذف — تمرير من الأصل دون تغيير سلوك):
 *  - الزر اليدوي يبقى كما هو (لا حذف).
 *  - يُضاف تأثير debounce تلقائي (500ms) فوقه: الكتابة المتواصلة تُدمج
 *    في طلب واحد، والاستعلام القصير (<3) لا يطلق شبكة أبدًا.
 *  - اللوحة تنادي `DebouncedDirectorySearchEffect(directoryQuery, directory)`
 *    فقط — كل المنطق هنا، والـ UI الأصلي لم يتغير.
 */

const val DIRECTORY_SEARCH_DEBOUNCE_MS = 500L
const val DIRECTORY_SEARCH_MIN_LENGTH = 3
const val MESSAGE_SEARCH_DEBOUNCE_MS = 300L
const val MESSAGE_SEARCH_MIN_LENGTH = 2

/** جاهزية استعلام الدليل: 3 أحرف على الأقل بعد التشذيب. */
fun isDirectoryQueryReady(query: String): Boolean =
    query.trim().length >= DIRECTORY_SEARCH_MIN_LENGTH

/**
 * تأثير البحث التلقائي مع debounce — يُوضع بجانب `var directoryQuery`
 * في اللوحة. يتجاهل: الحالة Loading (منع العاصفة)، والاستعلام القصير.
 * لا يمس الزر اليدوي. التنفيذ: LaunchedEffect(query) مع delay — كل حرف
 * يلغي السابق (debounce حقيقي)، والطلب الأخير المستقر فقط يطلق شبكة.
 */
@Composable
fun DebouncedDirectorySearchEffect(
    query: String,
    directory: DirectoryViewModel,
    enabled: Boolean = true
) {
    if (!enabled) return
    LaunchedEffect(query) {
        if (!isDirectoryQueryReady(query)) return@LaunchedEffect
        kotlinx.coroutines.delay(DIRECTORY_SEARCH_DEBOUNCE_MS)
        // لا تقاطع مع طلب جارٍ — الزر اليدوي يبقى المسار الفوري.
        if (directory.state != DirectoryState.Loading) {
            directory.search(query.trim())
        }
    }
}

/**
 * حقل بحث الدليل الموحد — نفس شكل اللوحة الأصلية (OutlinedTextField +
 * زر «بحث آمن»)، لكن مع debounce مدمج. اللوحة الأصلية تبقى تنادي الحقل
 * القديم؛ هذا المكون للشاشات الجديدة وللتوحيد التدريجي — لا حذف.
 */
@Composable
fun DirectorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    directory: DirectoryViewModel,
    modifier: Modifier = Modifier,
    autoSearch: Boolean = true,
    onManualSearch: (() -> Unit)? = null
) {
    DebouncedDirectorySearchEffect(query = query, directory = directory, enabled = autoSearch)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("username أو معرّف يونس") },
            singleLine = true
        )
        Button(
            onClick = {
                if (onManualSearch != null) onManualSearch()
                else if (isDirectoryQueryReady(query)) directory.search(query.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isDirectoryQueryReady(query) && directory.state != DirectoryState.Loading
        ) {
            Icon(Icons.Default.Search, null)
            Text(" بحث آمن")
        }
    }
}

/**
 * حالة بحث الرسائل مع debounce — للاستخدام داخل حوار «البحث داخل المحادثة».
 * الأصل كان `LaunchedEffect(messageSearchQuery, currentConversation)` بلا
 * debounce؛ هنا 300ms + حد أدنى حرفين. تُرجع الاستعلام المُستقر.
 */
@Composable
fun rememberDebouncedMessageQuery(rawQuery: String): String {
    var stable by remember { mutableStateOf(rawQuery) }
    LaunchedEffect(rawQuery) {
        if (rawQuery.length < MESSAGE_SEARCH_MIN_LENGTH) {
            stable = rawQuery
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(MESSAGE_SEARCH_DEBOUNCE_MS)
        stable = rawQuery
    }
    return stable
}
