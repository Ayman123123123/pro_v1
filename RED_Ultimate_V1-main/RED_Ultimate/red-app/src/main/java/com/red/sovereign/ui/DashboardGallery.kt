package com.red.sovereign.ui

import androidx.compose.runtime.Composable
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.features.media.MediaGalleryDialog
import com.red.sovereign.media.AttachmentViewModel

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardGallery — تراكبات معرض الوسائط مستخرجة من `RedDashboard.kt`
 * ════════════════════════════════════════════════════════════════════════
 *
 *  المصدر: `RedDashboard.kt` — كتلتا `if (showMediaGallery …)` و
 *  `if (showGroupMediaGallery …)` (معرض المحادثة الخاصة + معرض المجموعة).
 *  نُقلتا كما هما منطقيًا؛ اللوحة تمرر الحالة فقط (لا تلمس هذه الملفات
 *  حالة اللوحة مباشرةً) — نفس نمط `DashboardSheets.kt` / `DashboardChat.kt`.
 *
 *  يتطلب `conversationId()` الداخلية في نفس الحزمة لحساب مفتاح
 *  المحادثة الخاصة من `(myRedId, target)`.
 */

/** تراكبا المعرض: الخاص (target) + المجموعة (groupConversationId). */
@Composable
internal fun DashboardGalleryOverlays(
    showMediaGallery: Boolean,
    target: String,
    myRedId: String,
    decrypted: List<DecryptedMessage>,
    attachments: AttachmentViewModel,
    onDismissMedia: () -> Unit,
    showGroupMediaGallery: Boolean,
    groupConversationId: String?,
    onDismissGroupMedia: () -> Unit
) {
    if (showMediaGallery && target.isNotBlank()) {
        val convKey = conversationId(myRedId, target)
        MediaGalleryDialog(
            title = "الوسائط المشتركة",
            messages = decrypted.filter { it.conversationId == convKey },
            attachments = attachments,
            onDismiss = onDismissMedia
        )
    }
    if (showGroupMediaGallery && groupConversationId != null) {
        MediaGalleryDialog(
            title = "وسائط المجموعة",
            messages = decrypted.filter { it.conversationId == groupConversationId },
            attachments = attachments,
            onDismiss = onDismissGroupMedia
        )
    }
}
