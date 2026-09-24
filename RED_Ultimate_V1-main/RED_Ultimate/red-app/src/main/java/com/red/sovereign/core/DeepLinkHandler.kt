package com.red.sovereign.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.red.sovereign.MainActivity

/**
 * معالج الروابط العميقة المركزي (RED / Younes).
 *
 * يفك الصيغ الخمس المدعومة ويوجه للوجهة الصحيحة:
 * - younes://groupcall[?groupCallId=..] -> GroupCall
 * - younes://livestream[?streamId=..]   -> LiveStream
 * - https://red.ly/g/<token>            -> GroupInvite
 * - red://join?token=<token>            -> GroupInvite
 * - RED-GROUP:<link|token>              -> GroupInvite (يُعاد فك ما بعد البادئة)
 *
 * GroupsScreen تستخدم [parseGroupInviteToken] فقط (تحليل روابط الدعوة).
 */
object DeepLinkHandler {
    const val EXTRA_DEEP_LINK_KIND = "deep_link_kind"
    const val EXTRA_GROUP_INVITE_TOKEN = "group_invite_token"
    const val EXTRA_GROUP_CALL_ID = "deep_group_call_id"
    const val EXTRA_STREAM_ID = "deep_stream_id"
    const val EXTRA_CALL_TYPE = "deep_call_type"
    const val EXTRA_OPEN_SCHEDULED = "open_scheduled_messages"

    const val KIND_GROUP_INVITE = "group_invite"
    const val KIND_GROUP_CALL = "group_call"
    const val KIND_LIVE = "live"
    const val KIND_SCHEDULED = "scheduled"

    sealed interface RedDeepLink {
        data object Unknown : RedDeepLink
        data class GroupInvite(val token: String) : RedDeepLink
        data class GroupCall(val groupCallId: String, val isVideo: Boolean = false) : RedDeepLink
        data class LiveStream(val streamId: String) : RedDeepLink
    }

    /** تحليل دعوة مجموعة فقط — يُستخدم من GroupsScreen (يعيد "" إن لم تكن دعوة). */
    fun parseGroupInviteToken(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return ""
        // روابط مكالمة/بث ليست دعوات مجموعة — لا تمرر لمحلل الدعوات.
        when (parse(t)) {
            is RedDeepLink.GroupCall -> return ""
            is RedDeepLink.LiveStream -> return ""
            is RedDeepLink.GroupInvite -> { /* تابع للتوحيد عبر محلل المجموعات */ }
            RedDeepLink.Unknown -> return ""
        }
        return com.red.sovereign.groups.parseInviteTokenQrAware(t)
    }

    fun inviteShareLink(token: String): String =
        com.red.sovereign.groups.inviteShareLink(token)

    fun parse(raw: String?): RedDeepLink {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return RedDeepLink.Unknown
        // RED-GROUP:<payload> — جرّد البادئة (بغض النظر عن حالة الأحرف) ثم أعد الفك.
        if (t.startsWith("RED-GROUP:", ignoreCase = true)) {
            val inner = t.substring("RED-GROUP:".length).trim()
            if (inner.isEmpty()) return RedDeepLink.Unknown
            val nested = parse(inner)
            if (nested is RedDeepLink.GroupInvite) return nested
            val token = com.red.sovereign.groups.parseInviteTokenQrAware(inner)
            return if (token.isNotBlank()) RedDeepLink.GroupInvite(token) else RedDeepLink.Unknown
        }
        val uri = runCatching { Uri.parse(t) }.getOrNull() ?: return RedDeepLink.Unknown
        return parse(uri)
    }

    fun parse(uri: Uri?): RedDeepLink {
        if (uri == null) return RedDeepLink.Unknown
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        when (scheme) {
            "younes", "red" -> {
                // younes://groupcall | younes://livestream | red://join | red://groupcall | red://livestream
                when (host) {
                    "groupcall", "group_call", "call" -> {
                        val id = firstQueryParam(uri, "groupCallId", "group_call_id", "id", "roomId")
                            ?: uri.lastPathSegment?.trim().orEmpty()
                        return if (id.isNotBlank()) RedDeepLink.GroupCall(id, parseIsVideo(uri)) else RedDeepLink.Unknown
                    }
                    "livestream", "live", "stream" -> {
                        val id = firstQueryParam(uri, "streamId", "stream_id", "id")
                            ?: uri.lastPathSegment?.trim().orEmpty()
                        return if (id.isNotBlank()) RedDeepLink.LiveStream(id) else RedDeepLink.Unknown
                    }
                    "join", "g", "group", "invite" -> {
                        val token = firstQueryParam(uri, "token", "invite", "code")
                            ?: uri.lastPathSegment?.trim().orEmpty()
                            ?: com.red.sovereign.groups.parseInviteTokenQrAware(uri.toString())
                        return if (token.isNotBlank()) RedDeepLink.GroupInvite(token.trim()) else RedDeepLink.Unknown
                    }
                    else -> {
                        // red://<token> أو younes://<token> خام — دعوة إن صلحت، وإلا token= من الاستعلام.
                        val qp = firstQueryParam(uri, "token", "invite", "code")
                        if (!qp.isNullOrBlank()) return RedDeepLink.GroupInvite(qp.trim())
                        val seg = uri.lastPathSegment?.trim().orEmpty()
                        if (seg.isNotBlank() && host.isNotBlank() && seg != host) {
                            return RedDeepLink.GroupInvite(seg)
                        }
                        if (host.isNotBlank()) {
                            val token = com.red.sovereign.groups.parseInviteTokenQrAware(uri.toString())
                            if (token.isNotBlank() && token != host) return RedDeepLink.GroupInvite(token)
                        }
                        return RedDeepLink.Unknown
                    }
                }
            }
            "http", "https" -> {
                // https://red.ly/g/<token> (و www.red.ly)
                if (host == "red.ly" || host == "www.red.ly") {
                    val segs = uri.pathSegments.orEmpty()
                    val gIdx = segs.indexOf("g")
                    val token = when {
                        gIdx >= 0 && gIdx + 1 < segs.size -> segs[gIdx + 1].trim()
                        segs.size == 1 && segs[0].isNotBlank() -> segs[0].trim()
                        else -> firstQueryParam(uri, "token", "invite", "code")?.trim().orEmpty()
                    }
                    return if (token.isNotBlank()) RedDeepLink.GroupInvite(token) else RedDeepLink.Unknown
                }
                return RedDeepLink.Unknown
            }
            else -> {
                // بلا مخطط معروف: رمز خام أو رابط يحمل token=.
                val raw = uri.toString()
                val token = com.red.sovereign.groups.parseInviteTokenQrAware(raw)
                return if (token.isNotBlank()) RedDeepLink.GroupInvite(token) else RedDeepLink.Unknown
            }
        }
    }

    /** يبني Intent نحو MainActivity (singleTask) يحمل سياق الوجهة + الـ data الأصلية. */
    fun toMainActivityIntent(context: Context, link: RedDeepLink, sourceUri: Uri? = null): Intent? {
        val base = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (sourceUri != null) data = sourceUri
        }
        return when (link) {
            is RedDeepLink.GroupInvite -> base.apply {
                putExtra(EXTRA_DEEP_LINK_KIND, KIND_GROUP_INVITE)
                putExtra(EXTRA_GROUP_INVITE_TOKEN, link.token)
            }
            is RedDeepLink.GroupCall -> base.apply {
                putExtra(EXTRA_DEEP_LINK_KIND, KIND_GROUP_CALL)
                putExtra(EXTRA_GROUP_CALL_ID, link.groupCallId)
                putExtra(EXTRA_CALL_TYPE, if (link.isVideo) "VIDEO" else "VOICE")
            }
            is RedDeepLink.LiveStream -> base.apply {
                putExtra(EXTRA_DEEP_LINK_KIND, KIND_LIVE)
                putExtra(EXTRA_STREAM_ID, link.streamId)
            }
            RedDeepLink.Unknown -> null
        }
    }

    fun toMainActivityIntent(context: Context, raw: String?): Intent? {
        val link = parse(raw) ?: return null
        val uri = runCatching { if (raw != null) Uri.parse(raw.trim()) else null }.getOrNull()
        return toMainActivityIntent(context, link, uri)
    }

    private fun firstQueryParam(uri: Uri, vararg names: String): String? {
        for (n in names) {
            val v = runCatching { uri.getQueryParameter(n) }.getOrNull()?.trim()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun parseIsVideo(uri: Uri): Boolean {
        val v = firstQueryParam(uri, "isVideo", "video", "callType", "mode")?.lowercase().orEmpty()
        return v == "1" || v == "true" || v == "video" || v == "yes"
    }
}
