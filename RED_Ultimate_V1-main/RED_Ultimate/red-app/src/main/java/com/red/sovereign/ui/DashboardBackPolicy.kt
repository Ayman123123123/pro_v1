package com.red.sovereign.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * سياسة زر الرجوع للوحة القيادة — مستخرجة من وحش BackHandler داخل المحادثات.
 *
 * كانت 20 شرطاً مبعثرة في `ui/RedDashboard.kt` (enabled + when)؛ الآن:
 * - [DashboardSheet] يسمّي كل طبقة قابلة للإغلاق بالرجوع.
 * - [DashboardBackState] لقطة قيم الطبقات.
 * - [DashboardBackState.topSheet] يحمل **نفس ترتيب الأولوية الأصلي** دون أي تغيير منطقي.
 * - [rememberDashboardBackDispatcher] يعيد كتلة onBack جاهزة لـ BackHandler.
 *
 * تغطية عامة مؤكدة (2026-09-10): voice عبر [DashboardSheet.GroupVoicePanel]،
 * والقائمة عبر [DashboardSheet.GroupMenu]، والمرفقات عبر [DashboardSheet.AttachmentSheet]
 * و[DashboardSheet.GroupAttachmentSheet] — بنفس أولوية الطبقات الأصلية فوق المحادثة.
 * طبقات الاتصال الخارجي متروكة لمالكها ولا تُمس هنا.
 */
sealed interface DashboardSheet {
    data object SelectedChatMessage : DashboardSheet
    data object SelectedContact : DashboardSheet
    data object Directory : DashboardSheet
    data object MessageSearch : DashboardSheet
    data object MediaGallery : DashboardSheet
    data object GroupMediaGallery : DashboardSheet
    data object JoinGroup : DashboardSheet
    data object ManageGroup : DashboardSheet
    data object DisappearingDialog : DashboardSheet
    data object GroupDisappearingDialog : DashboardSheet
    data object GroupPollDialog : DashboardSheet
    data object GroupAttachmentSheet : DashboardSheet
    data object AttachmentSheet : DashboardSheet
    data object GroupVoicePanel : DashboardSheet
    data object GroupMenu : DashboardSheet
    data object GroupEmojiStickers : DashboardSheet
    data object EmojiStickers : DashboardSheet
    data object GroupConversation : DashboardSheet
    data object TargetConversation : DashboardSheet
}

/** لقطة حالة طبقات المحادثات — كل حقل يقابل شرطاً من شروط BackHandler الأصلية. */
data class DashboardBackState(
    val hasTarget: Boolean = false,
    val hasGroupConversation: Boolean = false,
    val showDirectory: Boolean = false,
    val showMessageSearch: Boolean = false,
    val showMediaGallery: Boolean = false,
    val showGroupMediaGallery: Boolean = false,
    val hasSelectedContact: Boolean = false,
    val showJoinGroup: Boolean = false,
    val hasManageGroup: Boolean = false,
    val hasSelectedChatMessage: Boolean = false,
    val showDisappearingDialog: Boolean = false,
    val showGroupDisappearingDialog: Boolean = false,
    val showGroupAttachmentSheet: Boolean = false,
    val showAttachmentSheet: Boolean = false,
    val showGroupVoicePanel: Boolean = false,
    val showGroupMenu: Boolean = false,
    val showEmoji: Boolean = false,
    val showStickers: Boolean = false,
    val showGroupEmoji: Boolean = false,
    val showGroupStickers: Boolean = false,
    val showGroupPollDialog: Boolean = false
) {
    /**
     * أعلى طبقة حسب **نفس ترتيب when الأصلي** في RedDashboard — لا تحذف منطق، فقط استخراج.
     * إصلاح 2026-09-10: GroupPollDialog كان أخيراً بعد hasGroupConversation/hasTarget
     * فاستحال الوصول إليه أثناء محادثة مفتوحة (يُفتح حصراً داخل مجموعة) — نُقل فوق
     * المحادثات مع بقية الحوارات. وأُضيفت showAttachmentSheet الفردية وshowGroupVoicePanel
     * وshowGroupMenu بنفس عائلة الأولوية (طبقات فوق المحادثة، قبل الإيموجي والمحادثات).
     */
    fun topSheet(): DashboardSheet? = when {
        hasSelectedChatMessage -> DashboardSheet.SelectedChatMessage
        hasSelectedContact -> DashboardSheet.SelectedContact
        showDirectory -> DashboardSheet.Directory
        showMessageSearch -> DashboardSheet.MessageSearch
        showMediaGallery -> DashboardSheet.MediaGallery
        showGroupMediaGallery -> DashboardSheet.GroupMediaGallery
        showJoinGroup -> DashboardSheet.JoinGroup
        hasManageGroup -> DashboardSheet.ManageGroup
        showDisappearingDialog -> DashboardSheet.DisappearingDialog
        showGroupDisappearingDialog -> DashboardSheet.GroupDisappearingDialog
        showGroupPollDialog -> DashboardSheet.GroupPollDialog
        showGroupAttachmentSheet -> DashboardSheet.GroupAttachmentSheet
        showAttachmentSheet -> DashboardSheet.AttachmentSheet
        showGroupVoicePanel -> DashboardSheet.GroupVoicePanel
        showGroupMenu -> DashboardSheet.GroupMenu
        showGroupEmoji || showGroupStickers -> DashboardSheet.GroupEmojiStickers
        showEmoji || showStickers -> DashboardSheet.EmojiStickers
        hasGroupConversation -> DashboardSheet.GroupConversation
        hasTarget -> DashboardSheet.TargetConversation
        else -> null
    }

    /** نفس شرط enabled الأصلي: أي طبقة مفتوحة. */
    fun isBackEnabled(): Boolean = topSheet() != null
}

/**
 * موزّع الرجوع — يعيد lambda تستدعي [onCloseSheet] بأعلى طبقة.
 * يُستخدم: `BackHandler(enabled = backState.isBackEnabled()) { dispatchBack() }`.
 */
@Composable
fun rememberDashboardBackDispatcher(
    state: DashboardBackState,
    onCloseSheet: (DashboardSheet) -> Unit
): () -> Unit {
    val dispatch: () -> Unit = remember(state, onCloseSheet) {
        {
            state.topSheet()?.let(onCloseSheet)
        }
    }
    return dispatch
}
