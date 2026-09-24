package com.red.sovereign.ui

/**
 * الموجز: الحالات (Stories) والمنشورات.
 *
 * استُخرج من `RedDashboard.kt` ضمن تفكيك الملف الضخم. يعتمد على
 * `conversationId` المشتركة، ويُصدِّر `FeedScreen` وحدها إلى اللوحة.
 *
 * تُحذف الحالات بعد 24 ساعة عبر `StoryCleanupWorker` لا من هنا.
 *
 * لم يتغيّر أي سطر منطق أثناء النقل — النقل بنيوي بحت.
 */

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.UuidV7
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.media.MediaApi
import com.red.sovereign.media.VoiceNotePlayer
import com.red.sovereign.social.FeedState
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.social.Post
import com.red.sovereign.social.ThreadState
import com.red.sovereign.social.isPollExpired
import com.red.sovereign.social.mediaCacheExtension
import com.red.sovereign.social.postMediaPath
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryState
import com.red.sovereign.stories.StoryVideoPlayer
import com.red.sovereign.stories.StoryVisibility
import com.red.sovereign.stories.StoryViewModel
import com.red.sovereign.stories.StoryViewerState
import coil3.compose.AsyncImage
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.ui.components.SovereignAvatarThemed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تبويب في فيد المنشورات: عنوانه ونطاقه كما يفهمه الخادم.
 * `scope = null` يعني «كل شيء» فلا يُرسل مُرشِّحًا.
 */
internal data class FeedTab(val title: String, val scope: String?)

/**
 * تبويبات الفيد بترتيب ظهورها.
 *
 * «الأصدقاء» حلّ محلّ «أتابعهم»، و«عام» حلّ محلّ «اليمن».
 * والفرق سلوكيّ لا لفظيّ: الخادم يحسب الأصدقاء بتبادل المتابعة في
 * الاتجاهين (`FeedService.friendIds`)، فلا يظهر هنا منشور شخص أتابعه
 * دون أن يتابعني — بخلاف «أتابعهم» التي كانت أحادية الاتجاه.
 *
 * الفهرس المستعمل في `FilterChip` هو ترتيب العنصر في هذه القائمة،
 * فإضافة تبويب لا تستلزم تعديل أي `when` منفصل.
 */
internal val FEED_TABS = listOf(
    FeedTab("لك", null),
    FeedTab("الأصدقاء", "FRIENDS"),
    FeedTab("عام", "PUBLIC"),
)

@Composable
internal fun FeedScreen(
    account: AuthState.Authenticated,
    feed: FeedViewModel,
    stories: StoryViewModel,
    directory: DirectoryViewModel,
    onCreate: () -> Unit
) {
    var filter by remember { mutableIntStateOf(0) }
    var threadPost by remember { mutableStateOf<Post?>(null) }
    var quotePost by remember { mutableStateOf<Post?>(null) }
    var editPost by remember { mutableStateOf<Post?>(null) }
    var editText by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("") }
    var quoteText by remember { mutableStateOf("") }
    // جمهور الحالة يُختار *قبل* منتقي الوسيط: بعد اختيار الصورة يبدأ
    // الرفع فورًا، فلا تبقى فرصة للسؤال عن الجمهور دون رفعٍ يُلغى.
    var showStoryAudience by remember { mutableStateOf(false) }
    var storyVisibility by remember { mutableStateOf(StoryVisibility.CONTACTS) } // String const
    var storyAllowedIds by remember { mutableStateOf(setOf<String>()) }
    var storyAudienceQuery by remember { mutableStateOf("") }
    // مؤلفا المعاينة الحية (2026-09-10): منشورات + حالات — يتفوقان على مسودة X العمياء.
    var showPostComposer by remember { mutableStateOf(false) }
    var showStoryComposer by remember { mutableStateOf(false) }
    val layout = WindowLayout.current()
    val page = layout.pagePadding
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { stories.upload(it, visibleTo = storyVisibility, allowedUserIds = storyAllowedIds.toList()) }
    }
    val refreshing = feed.state == FeedState.Loading && feed.posts.isNotEmpty()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { feed.refresh() }, modifier = Modifier.fillMaxSize().padding(innerPadding)) {
    LazyColumn(
        Modifier.fillMaxSize().then(if (layout.isWide) Modifier.padding(horizontal = 48.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (layout.compactChrome) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            LazyRow(Modifier.padding(horizontal = page), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // مؤلف الحالات الجديد (معاينة حية + صوت/نص) — يحل محل حوار الجمهور
                // القديم للمسارين CONTACTS/EVERYONE؛ المخصص SELECTED يبقى في الحوار أدناه.
                item { StoryCircle(if (stories.state == StoryState.Uploading) "يرفع…" else "قصتك", true) { showStoryComposer = true } }
                items(stories.stories.filter { !stories.isMuted(it.ownerRedId) }.sortedBy { it.isViewed }, key = Story::id) { story -> StoryCircle(story.ownerDisplayName + if (story.viewCount > 0) " • ${story.viewCount}" else "", false) { stories.open(story) } }
            }
        }
        item {
            LazyRow(Modifier.padding(horizontal = page), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // مصدر واحد للتبويبات: كان الفهرس يُشتق بـ`when` منفصل عن
                // القائمة، فأي إضافة تبويب توجب تعديل موضعين ويسهل أن
                // يختلّ التطابق. الآن الفهرس هو ترتيب العنصر نفسه.
                itemsIndexed(FEED_TABS) { idx, tab ->
                    FilterChip(filter == idx, {
                        filter = idx
                        feed.load(tab.scope)
                    }, { Text(tab.title) })
                }
            }
        }
        item {
            Card(
                // مؤلف المعاينة الحية أولًا؛ onCreate (بث/استكشاف/حالة مخصصة) بزر ثانوي داخله.
                Modifier.fillMaxWidth().padding(horizontal = page).clickable(onClick = { showPostComposer = true }),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column {
                    Row(Modifier.padding(if (layout.compactChrome) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SovereignAvatarThemed(account.username.take(1)); Text(
                            "ماذا يحدث في يونس؟",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        ); Icon(Icons.Default.Add, "إنشاء منشور", tint = MaterialTheme.colorScheme.primary)
                    }
                    // مسار onCreate القديم (بث مباشر/استكشاف/حالة مخصصة) يبقى متاحًا —
                    // لا يُحذف حتى لا ينكسر FAB اللوحة الذي يفتح CreateSheet.
                    TextButton(onClick = onCreate, modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)) {
                        Text("المزيد: بث • استكشاف • حالة مخصصة", fontSize = 11.sp)
                    }
                }
            }
        }
        if (feed.state is FeedState.Message) item { Text((feed.state as FeedState.Message).text, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = page + 4.dp)) }
        when {
            feed.state == FeedState.Loading && feed.posts.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } }
            feed.state is FeedState.Error && feed.posts.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(page), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Default.DynamicFeed, "تعذر تحميل نبض يونس", (feed.state as FeedState.Error).message)
                    Button(onClick = { feed.refresh() }) { Text("إعادة المحاولة") }
                }
            }
            feed.posts.isEmpty() -> item { EmptyState(Icons.Default.DynamicFeed, "ابدأ مجتمع يونس", "اكتب أول منشور محلي. النظام يدعم السلاسل والاقتباسات والاستطلاعات. هذا النبض عام — ليس E2EE.") }
            else -> items(feed.posts, key = { it.id }) { post -> PostCard(post, account.redId, feed::toggleLike, feed::react, feed::requestFriend, feed::vote, { threadPost = post; feed.loadThread(post) }, { quotePost = post }, onRepost = feed::repost, onEdit = { p, t -> editPost = p; editText = t }, onDelete = { p -> feed.delete(p); snackbarScope.launch { if (snackbarHostState.showSnackbar(message = "تم حذف المنشور", actionLabel = "تراجع") == SnackbarResult.ActionPerformed) feed.undoDelete() } }, onHide = feed::hide, onMute = feed::mute, onReport = feed::report) }
        }
        // ── المجدولة (تتفوق على X Premium: مجانية ومحلية) ──
        if (feed.scheduledPosts.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = page),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏰ منشورات مجدولة (${feed.scheduledPosts.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
                        feed.scheduledPosts.sortedBy { it.scheduledAtMs }.take(3).forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    Text(com.red.sovereign.social.scheduledLabel(s.scheduledAtMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                TextButton({ feed.cancelScheduled(s.id) }) { Text("إلغاء", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
    }
    }
    if (showStoryAudience) {
        AlertDialog(
            onDismissRequest = { showStoryAudience = false },
            title = { Text("جمهور الحالة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تُعرض الحالة لمدة 24 ساعة. اختر من يمكنه مشاهدتها قبل اختيار الوسيط.")
                    // الثوابت من StoryVisibility لا نصوص حرفية: الخادم
                    // يرفض أي قيمة خارج التعداد، وخطأ حرفٍ هنا لا يكشفه
                    // مترجم بل يظهر 400 وقت الرفع.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            StoryVisibility.CONTACTS to "جهات اتصالي",
                            StoryVisibility.EVERYONE to "الجميع",
                            StoryVisibility.SELECTED to "محددون"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = storyVisibility == value,
                                onClick = { storyVisibility = value },
                                label = { Text(label) }
                            )
                        }
                    }
                    if (storyVisibility == StoryVisibility.SELECTED) {
                        OutlinedTextField(
                            value = storyAudienceQuery,
                            onValueChange = { storyAudienceQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("بحث في جهات الاتصال…") },
                            singleLine = true
                        )
                        val candidates = directory.contacts.filter {
                            storyAudienceQuery.isBlank() ||
                                it.displayName.contains(storyAudienceQuery, ignoreCase = true) ||
                                it.username.contains(storyAudienceQuery, ignoreCase = true)
                        }.take(30)
                        if (candidates.isEmpty()) {
                            Text("لا توجد جهات اتصال مطابقة — أضف أصدقاء أولًا.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        } else {
                            Text("المحددون: ${storyAllowedIds.size}", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                candidates.forEach { contact ->
                                    val checked = contact.redId in storyAllowedIds
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            storyAllowedIds = if (checked) storyAllowedIds - contact.redId
                                            else storyAllowedIds + contact.redId
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                storyAllowedIds = if (it) storyAllowedIds + contact.redId
                                                else storyAllowedIds - contact.redId
                                            }
                                        )
                                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                            Text(contact.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text("@${contact.username}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStoryAudience = false
                        storyPicker.launch(arrayOf("image/*", "video/*"))
                    },
                    enabled = storyVisibility != StoryVisibility.SELECTED || storyAllowedIds.isNotEmpty()
                ) { Text("اختيار صورة أو فيديو") }
            },
            dismissButton = { TextButton(onClick = { showStoryAudience = false }) { Text("إلغاء") } }
        )
    }
    threadPost?.let { root ->
        AlertDialog(
            onDismissRequest = { threadPost = null; replyText = ""; feed.closeThread() },
            title = { Text("سلسلة يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val threadState = feed.threadState) {
                        ThreadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.primary)
                        is ThreadState.Error -> Text(threadState.message, color = MaterialTheme.colorScheme.error)
                        // عمود ممرَّر بارتفاع ثابت بدل LazyColumn متداخلة: القائمتان
                        // الكسولتان كانتا تتنازعان إيماءات التمرير داخل الحوار.
                        else -> Column(
                            Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            feed.threadPosts.forEach { item ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (item.id == root.id) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(Modifier.padding(12.dp)) { Text("@${item.authorUsername} · ${item.authorRedId}", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp); Text(item.text) }
                                }
                            }
                        }
                    }
                    OutlinedTextField(replyText, { replyText = it }, Modifier.fillMaxWidth(), placeholder = { Text("اكتب ردًا علنيًا في نبض يونس…") }, maxLines = 4)
                    Button({ feed.reply(root, replyText) { replyText = "" } }, Modifier.fillMaxWidth(), enabled = replyText.isNotBlank() && feed.threadState != ThreadState.Publishing) { Text("إرسال الرد") }
                }
            },
            confirmButton = { TextButton({ threadPost = null; replyText = ""; feed.closeThread() }) { Text("إغلاق") } }
        )
    }
    quotePost?.let { quoted ->
        AlertDialog(
            onDismissRequest = { quotePost = null; quoteText = "" },
            title = { Text("اقتباس منشور @${quoted.authorUsername}") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Card { Text(quoted.text, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; OutlinedTextField(quoteText, { quoteText = it }, Modifier.fillMaxWidth(), label = { Text("تعليقك") }, maxLines = 5) } },
            confirmButton = { Button({ feed.quote(quoted, quoteText) { quotePost = null; quoteText = "" } }, enabled = quoteText.isNotBlank() && feed.state != FeedState.Publishing) { Text("نشر الاقتباس") } },
            dismissButton = { TextButton({ quotePost = null; quoteText = "" }) { Text("إلغاء") } }
        )
    }
    editPost?.let { post ->
        AlertDialog(
            onDismissRequest = { editPost = null; editText = "" },
            title = { Text("تعديل المنشور") },
            text = { OutlinedTextField(editText, { editText = it }, Modifier.fillMaxWidth(), label = { Text("النص الجديد") }, maxLines = 7) },
            confirmButton = { Button({ feed.edit(post, editText) { editPost = null; editText = "" } }, enabled = editText.isNotBlank() && editText != post.text) { Text("حفظ التعديل") } },
            dismissButton = { TextButton({ editPost = null; editText = "" }) { Text("إلغاء") } }
        )
    }
    // ── مؤلفا المعاينة الحية (2026-09-10) ──
    if (showPostComposer) {
        com.red.sovereign.ui.components.PostComposerSheet(
            feed = feed,
            username = account.username,
            onDismiss = { showPostComposer = false }
        )
    }
    if (showStoryComposer) {
        com.red.sovereign.ui.components.StoryComposerSheet(
            stories = stories,
            onDismiss = { showStoryComposer = false },
            candidates = remember(directory.contacts) { directory.contacts.map { it.redId to it.displayName } }
        )
    }
    val viewer = stories.viewer
    val context = LocalContext.current
    if (viewer !is StoryViewerState.Closed) {
        val currentStoryId = when (viewer) {
            is StoryViewerState.Loading -> viewer.story.id
            is StoryViewerState.Image -> viewer.story.id
            is StoryViewerState.Video -> viewer.story.id
            is StoryViewerState.Text -> viewer.story.id
            is StoryViewerState.Voice -> viewer.story.id
            is StoryViewerState.Unsupported -> viewer.story.id
            is StoryViewerState.Error -> viewer.story.id
            StoryViewerState.Closed -> ""
        }
        StoryFullscreen(
            viewer = viewer,
            onClose = stories::closeViewer,
            onNext = {
                val idx = stories.stories.indexOfFirst { it.id == currentStoryId }
                if (idx != -1 && idx < stories.stories.size - 1) {
                    stories.open(stories.stories[idx + 1])
                } else {
                    stories.closeViewer()
                }
            },
            onPrev = {
                val idx = stories.stories.indexOfFirst { it.id == currentStoryId }
                if (idx > 0) {
                    stories.open(stories.stories[idx - 1])
                } else {
                    stories.closeViewer()
                }
            },
            onReact = stories::react,
            isOwn = stories.stories.firstOrNull { it.id == currentStoryId }?.ownerRedId == account.redId,
            nameResolver = { viewerId ->
                directory.contacts.firstOrNull { it.redId == viewerId }?.displayName ?: viewerId
            },
            onDelete = stories::delete,
            viewers = stories.storyViewers,
            viewersLoading = stories.viewersLoading,
            onOpenViewers = stories::loadViewers,
            viewersError = stories.viewersError,
            onRetryViewers = stories::loadViewers,
            muted = stories.stories.firstOrNull { it.id == currentStoryId }?.let { stories.isMuted(it.ownerRedId) } ?: false,
            onToggleMute = { stories.stories.firstOrNull { it.id == currentStoryId }?.let { stories.toggleMute(it.ownerRedId) } },
            onReply = { story, text ->
                RedConnectionService.sendRichText(
                    context,
                    story.ownerRedId,
                    conversationId(account.redId, story.ownerRedId),
                    RichMessage(action = "STORY_REPLY", text = text, replyTo = story.id),
                    UuidV7.next()
                )
            }
        )
    }
}

@Composable

private fun StoryCircle(label: String, own: Boolean, click: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = click, onClickLabel = label)) {
    // حلقة القصة من colorScheme: أساسي للخاصة، ثانوي للآخرين — فاتح/داكن معًا.
    Box(
        Modifier.size(66.dp).clip(CircleShape)
            .background(if (own) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(58.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (own) Icons.Default.Add else Icons.Default.Person,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Text(
        label,
        fontSize = 11.sp,
        maxLines = 1,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = com.red.sovereign.ui.theme.PlexArabicFamily
    )
}

@Composable

private fun PostCard(
    post: Post,
    currentRedId: String,
    onLike: (Post) -> Unit,
    onReact: (Post, String) -> Unit = { p, _ -> onLike(p) },
    onFollow: (Post) -> Unit,
    onVote: (Post, String) -> Unit,
    onThread: () -> Unit,
    onQuote: () -> Unit,
    onRepost: (Post) -> Unit = {},
    onEdit: (Post, String) -> Unit = { _, _ -> },
    onDelete: (Post) -> Unit = {},
    onHide: (Post) -> Unit = {},
    onMute: (Post) -> Unit = {},
    onReport: (Post) -> Unit = {}
) = Card(
    Modifier.fillMaxWidth().padding(horizontal = WindowLayout.current().pagePadding),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // مفتاح post.id: بلا مفتاح كانت حالة القائمة (showMenu) تُعاد استخدامها
        // لعنصر آخر بعد الحذف/الإدراج في LazyColumn (قائمة منشور A تنفتح على B).
        var showMenu by androidx.compose.runtime.remember(post.id) { androidx.compose.runtime.mutableStateOf(false) }
        // توسيع تفاعلي يتفوق على المنافسين: إظهار صف التفاعلات الأربع بعد ضغطة
        // مطوّلة بدل زر واحد — الحالة لكل منشور بمفتاح post.id.
        var showReactions by androidx.compose.runtime.remember(post.id) { androidx.compose.runtime.mutableStateOf(false) }
        var showEditHistory by androidx.compose.runtime.remember(post.id) { androidx.compose.runtime.mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Avatar موحد عبر SovereignAvatarThemed (Coil + كاش + سقوط حرف).
            // كان Box بتدرّج يدوي مكررًا في كل بطاقة بلا صورة فعلية.
            SovereignAvatarThemed(
                text = post.authorDisplayName.take(1).ifBlank { "ي" },
                size = 48.dp
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(post.authorDisplayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${post.authorUsername} · ${post.authorRedId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ showMenu = true }) { Icon(Icons.Default.MoreVert, "خيارات") }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (post.authorRedId == currentRedId) {
                    DropdownMenuItem(text = { Text("تعديل") }, onClick = { showMenu = false; onEdit(post, post.text) })
                    DropdownMenuItem(text = { Text("حذف") }, onClick = { showMenu = false; onDelete(post) })
                } else {
                    DropdownMenuItem(text = { Text("إخفاء") }, onClick = { showMenu = false; onHide(post) })
                    DropdownMenuItem(text = { Text("كتم @${post.authorUsername}") }, onClick = { showMenu = false; onMute(post) })
                    DropdownMenuItem(text = { Text("إبلاغ") }, onClick = { showMenu = false; onReport(post) })
                }
            }
            if (post.authorRedId != currentRedId) TextButton({ onFollow(post) }) { Text("متابعة") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // شارات عرض فقط (كانت AssistChip معطلة بـ onClick فارغ) — نصوص ثابتة بلا تفاعل وهمي.
            Text(if (post.visibility == "LOCAL") "نبض محلي" else "عام", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (post.poll != null) "استطلاع" else if (post.parentId != null) "رد" else "منشور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (post.kind != "POST") Text(post.kind, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(post.text, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
        if (post.hashtags.isNotEmpty() || post.mentions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                post.hashtags.forEach { tag -> Text(tag, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                post.mentions.forEach { m -> Text(m, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
            }
        }
        if (SettingsRuntime.current.linkPreviews) {
            post.linkCard?.let { card ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(card.title ?: card.url, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(card.description ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
                    }
                }
            }
        }
        // وسائط المنشور (كانت تُتجاهل كليًا رغم وجود post.media في النموذج).
        if (post.media.isNotEmpty()) PostMediaGallery(post.media)
        if (post.editedAt != null) {
            TextButton(onClick = { showEditHistory = true }) {
                Text("تم التعديل", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            if (showEditHistory) {
                AlertDialog(
                    onDismissRequest = { showEditHistory = false },
                    title = { Text("سجل التعديلات") },
                    text = {
                        if (post.editHistory.isEmpty()) {
                            Text("لا يوجد سجل متاح")
                        } else {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                post.editHistory.forEach { entry ->
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(entry.text, fontSize = 14.sp)
                                        Text(entry.editedAt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton({ showEditHistory = false }) { Text("إغلاق") } }
                )
            }
        }
        post.quotePostId?.let { quotedId ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, "اقتباس", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Text(" اقتباس يونس · ${quotedId.take(8)}", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp)
                }
            }
        }
        post.poll?.let { poll ->
            FeedPollBlock(postId = post.id, poll = poll, onVote = { optionId -> onVote(post, optionId) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
        // التفاعلات الأربع المدعومة خادماً — كانت LIKE فقط.
        // نمط متفوق: زر LIKE سريع + مبدّل يوسّع الباقي (يقلل ازدحام الصف على
        // الشاشات الضيقة). الحالة لكل منشور بمفتاح post.id أعلاه.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            val likeCount = post.reactionCounts["LIKE"] ?: 0
            TextButton({ onReact(post, "LIKE") }) { Text("👍", fontSize = 15.sp); if (likeCount > 0) Text(" $likeCount", fontSize = 11.sp) }
            if (showReactions) {
                listOf("LOVE" to "❤️", "SUPPORT" to "🙏", "INSIGHTFUL" to "💡").forEach { (type, emoji) ->
                    val count = post.reactionCounts[type] ?: 0
                    TextButton({ onReact(post, type) }) { Text(emoji, fontSize = 15.sp); if (count > 0) Text(" $count", fontSize = 11.sp) }
                }
            }
            TextButton({ showReactions = !showReactions }) { Text(if (showReactions) "‹" else "› +", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PostAction(Icons.AutoMirrored.Filled.Chat, post.replyCount.toString(), true, onThread)
            PostAction(Icons.Default.Repeat, post.repostCount.toString(), true) { onRepost(post) }
            PostAction(Icons.Default.FormatQuote, "اقتباس", true, onQuote)
            PostAction(Icons.Default.Share, "مشاركة", true) {
                val shareText = buildString {
                    append(post.text)
                    if (post.hashtags.isNotEmpty()) append("\n").append(post.hashtags.joinToString(" "))
                    append("\n\nيونس · @").append(post.authorUsername)
                }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(intent, "مشاركة منشور يونس"))
                }.onFailure {
                    Toast.makeText(context, "تعذر مشاركة المنشور", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable private fun PostAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) = TextButton(action, enabled = enabled) { Icon(icon, label, Modifier.size(18.dp)); Text(" $label", fontSize = 11.sp) }
// (حُذف المغلف الرقيق Avatar — استيراد SovereignAvatarThemed مباشرة من ui/components/Avatar.kt. 2026-09-10)

/**
 * استطلاع الموجز بنمط X 2026: قبل التصويت تُعرض الخيارات المصوّرة
 * كشبكة صور قابلة للاختيار (والنصية كبطاقات بلا نِسَب)، وبعد التصويت
 * تنكمش الصور إلى مصغّرات بجانب أشرطة النِسَب. التصويت المنتهي
 * (poll.expiresAt) يُحجب مع شارة "انتهى التصويت" — الخادم يرفضه أصلًا.
 *
 * مفتاح الحالة postId (معادل poll.id — موديل social.Poll بلا id مستقل):
 * كان remember(hash الخيارات, expiresAt) فيعيد استخدام تصويت منشور لآخر
 * عند تساوي البصمة (خياران نصيان متطابقان)، ويضيع التصويت عند تحديث
 * عدّادات الخادم (تغيّر hash). postId مستقر عبر التحديثات.
 */
@Composable
private fun FeedPollBlock(postId: String, poll: com.red.sovereign.social.Poll, onVote: (String) -> Unit) {
    val expired = isPollExpired(poll.expiresAt)
    var myVoteId by remember(postId) { mutableStateOf<String?>(null) }
    val hasVoted = myVoteId != null
    val hasImages = poll.options.any { !it.imageUrl.isNullOrBlank() }
    val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (expired) {
            Text("انتهى التصويت", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (!expired && !hasVoted) {
            // ── قبل التصويت ──
            if (hasImages) {
                poll.options.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            Card(
                                Modifier.weight(1f).clickable { myVoteId = option.id; onVote(option.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column {
                                    option.imageUrl?.let { key ->
                                        AuthedMediaImage(
                                            pathOrKey = key,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(option.text, Modifier.padding(10.dp), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                poll.options.forEach { option ->
                    Card(
                        Modifier.fillMaxWidth().clickable { myVoteId = option.id; onVote(option.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(option.text, Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // ── بعد التصويت أو بعد الانتهاء: مصغّرات + نِسَب ──
            poll.options.forEach { option ->
                val ratio = (option.votes.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
                val mine = option.id == myVoteId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        option.imageUrl?.let { key ->
                            AuthedMediaImage(
                                pathOrKey = key,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(option.text, fontWeight = if (mine) FontWeight.Bold else FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${(ratio * 100).toInt()}%", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Text("${option.votes} صوت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        Text("إجمالي الأصوات: ${poll.options.sumOf { it.votes }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** شبكة وسائط المنشور: عنصر واحد عريض، وإلا صفوف ثنائية (بحد أقصى 4 + عدّاد). */
@Composable
private fun PostMediaGallery(media: List<com.red.sovereign.social.PostMedia>) {
    val shown = media.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (shown.size == 1) {
            PostMediaItem(shown[0], Modifier.fillMaxWidth())
        } else {
            shown.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        Box(Modifier.weight(1f)) { PostMediaItem(item, Modifier.fillMaxWidth()) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (media.size > shown.size) {
            Text("+${media.size - shown.size} وسائط أخرى", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PostMediaItem(item: com.red.sovereign.social.PostMedia, modifier: Modifier = Modifier) {
    val mime = item.mimeType.lowercase().substringBefore(';')
    when {
        mime.startsWith("image/") -> AuthedMediaImage(
            pathOrKey = postMediaPath(item.objectKey),
            mimeType = mime,
            modifier = modifier.aspectRatio(4f / 3f),
            contentScale = ContentScale.Crop
        )
        mime.startsWith("video/") -> PostAuthedVideo(item, modifier.aspectRatio(16f / 9f))
        mime.startsWith("audio/") -> PostAuthedAudio(item, modifier)
        else -> Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.InsertDriveFile, "ملف مرفق", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Text(mime.ifBlank { "ملف" }, Modifier.padding(start = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/**
 * صورة عبر Coil من مصدرين: رابط http(s) مباشر، أو وسيط مصدَّق يُنزَّل
 * أولًا إلى الكاش الخاص عبر MediaApi (المسار يتطلب مصادقة فلا يصلح
 * لـ Coil مباشرة) ثم يُعرض من الملف. مشترك مع شاشة الاستطلاعات.
 */
@Composable
internal fun AuthedMediaImage(
    pathOrKey: String,
    modifier: Modifier = Modifier,
    mimeType: String? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (pathOrKey.startsWith("http://") || pathOrKey.startsWith("https://")) {
        AsyncImage(
            model = pathOrKey,
            contentDescription = "صورة المنشور",
            contentScale = contentScale,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
        return
    }
    val appContext = LocalContext.current.applicationContext
    val path = remember(pathOrKey) { postMediaPath(pathOrKey) }
    val ext = remember(mimeType) { mediaCacheExtension(mimeType ?: "") }
    val api = remember(appContext) { MediaApi(appContext, AuthorizedApiClient(DashboardStoresHolder.tokenStore(appContext))) }
    val result by produceState<Result<java.io.File?>?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                when (val r = api.downloadToPrivateCache(path, ext)) {
                    is ApiResult.Success -> r.value
                    is ApiResult.Error -> null
                }
            }.getOrNull()?.let { Result.success(it) } ?: Result.success(null)
        }
    }
    if (result == null) {
        Box(
            modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).height(160.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp) }
        return
    }
    val r = result ?: return
    when {
        r.getOrNull() != null -> AsyncImage(
            model = r.getOrNull(),
            contentDescription = "صورة المنشور",
            contentScale = contentScale,
            modifier = modifier.clip(RoundedCornerShape(16.dp))
        )
        else -> Box(
            modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).height(120.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.BrokenImage, "تعذر التحميل", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                Text("تعذر تحميل الصورة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

/** فيديو منشور مصدَّق: تنزيل إلى الكاش الخاص ثم مشغّل Media3. */
@Composable
private fun PostAuthedVideo(item: com.red.sovereign.social.PostMedia, modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val path = remember(item.objectKey) { postMediaPath(item.objectKey) }
    val ext = remember(item.mimeType) { mediaCacheExtension(item.mimeType) }
    // توحيد 2026-09-10: نفس TokenStore عبر Holder (داخل remember غير-Composable).
    val api = remember(appContext) { MediaApi(appContext, AuthorizedApiClient(DashboardStoresHolder.tokenStore(appContext))) }
    val file by produceState<java.io.File?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            when (val r = api.downloadToPrivateCache(path, ext)) {
                is ApiResult.Success -> r.value
                is ApiResult.Error -> null
            }
        }
    }
    val local = file
    if (local != null) {
        Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            StoryVideoPlayer(android.net.Uri.fromFile(local), Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        }
    } else {
        Box(
            modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black).height(180.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Videocam, "جارٍ تحميل الفيديو", tint = Color.White, modifier = Modifier.size(32.dp))
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
    }
}

/** مقطع صوتي لمنشور مصدَّق: تنزيل ثم مشغّل الملاحظات الصوتية. */
@Composable
private fun PostAuthedAudio(item: com.red.sovereign.social.PostMedia, modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val path = remember(item.objectKey) { postMediaPath(item.objectKey) }
    val ext = remember(item.mimeType) { mediaCacheExtension(item.mimeType) }
    // توحيد 2026-09-10: نفس TokenStore عبر Holder (داخل remember غير-Composable).
    val api = remember(appContext) { MediaApi(appContext, AuthorizedApiClient(DashboardStoresHolder.tokenStore(appContext))) }
    val file by produceState<java.io.File?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            when (val r = api.downloadToPrivateCache(path, ext)) {
                is ApiResult.Success -> r.value
                is ApiResult.Error -> null
            }
        }
    }
    val local = file
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (local != null) {
            VoiceNotePlayer(uri = android.net.Uri.fromFile(local), modifier = Modifier.fillMaxWidth().padding(8.dp))
        } else {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                Text("جارٍ تحميل الصوت…", Modifier.padding(start = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * GroupAvatar موحد — يُعاد التصدير من components/Avatar.kt.
 * أُبقي الاسم القديم internal لتفادي كسر المنادين، لكن التنفيذ هو
 * SovereignGroupAvatar مع LaunchedEffect(group.id, group.avatarUrl) —
 * إصلاح عطب المفتاح المنفرد الذي كان يعلّق صورة مجموعة على أخرى عند
 * التمرير (نفس null) ولا يلغي طلبًا سابقًا عند تبدّل الرابط.
 */
@Composable private fun GroupAvatar(group: com.red.sovereign.groups.Group, groups: GroupViewModel) {
    com.red.sovereign.ui.components.SovereignGroupAvatar(group, groups)
}

