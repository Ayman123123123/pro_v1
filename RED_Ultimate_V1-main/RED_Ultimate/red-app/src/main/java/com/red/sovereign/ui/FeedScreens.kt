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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.RichMessage
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.social.FeedState
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.social.Post
import com.red.sovereign.social.ThreadState
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryState
import com.red.sovereign.stories.StoryVisibility
import com.red.sovereign.stories.StoryViewModel
import com.red.sovereign.stories.StoryViewerState
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.AqyalRoyalBlue
import com.red.sovereign.ui.theme.AqyalSurfaceNavy
import com.red.sovereign.ui.theme.AqyalSurfaceRaised
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch

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
internal fun FeedScreen(account: AuthState.Authenticated, feed: FeedViewModel, stories: StoryViewModel, onCreate: () -> Unit) {
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
    val layout = WindowLayout.current()
    val page = layout.pagePadding
    val storyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { stories.upload(it, visibleTo = storyVisibility) }
    }
    val refreshing = feed.state == FeedState.Loading && feed.posts.isNotEmpty()
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { feed.refresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().then(if (layout.isWide) Modifier.padding(horizontal = 48.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (layout.compactChrome) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item {
            LazyRow(Modifier.padding(horizontal = page), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { StoryCircle(if (stories.state == StoryState.Uploading) "يرفع…" else "قصتك", true) { showStoryAudience = true } }
                items(stories.stories.sortedBy { it.isViewed }, key = Story::id) { story -> StoryCircle(story.ownerDisplayName + if (story.viewCount > 0) " • ${story.viewCount}" else "", false) { stories.open(story) } }
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
            Card(Modifier.fillMaxWidth().padding(horizontal = page).clickable(onClick = onCreate), colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy)) {
                Row(Modifier.padding(if (layout.compactChrome) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(account.username.take(1)); Text("ماذا يحدث في يونس؟", color = Color.LightGray, modifier = Modifier.weight(1f).padding(horizontal = 12.dp)); Icon(Icons.Default.Add, null, tint = AqyalGold)
                }
            }
        }
        if (feed.state is FeedState.Message) item { Text((feed.state as FeedState.Message).text, color = AqyalGold, modifier = Modifier.padding(horizontal = page + 4.dp)) }
        when {
            feed.state == FeedState.Loading && feed.posts.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }
            feed.state is FeedState.Error && feed.posts.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(page), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(Icons.Default.DynamicFeed, "تعذر تحميل نبض يونس", (feed.state as FeedState.Error).message)
                    Button(onClick = { feed.refresh() }) { Text("إعادة المحاولة") }
                }
            }
            feed.posts.isEmpty() -> item { EmptyState(Icons.Default.DynamicFeed, "ابدأ مجتمع يونس", "اكتب أول منشور محلي. النظام يدعم السلاسل والاقتباسات والاستطلاعات. هذا النبض عام — ليس E2EE.") }
            else -> items(feed.posts, key = { it.id }) { post -> PostCard(post, account.redId, feed::toggleLike, feed::requestFriend, feed::vote, { threadPost = post; feed.loadThread(post) }, { quotePost = post }, onEdit = { p, t -> editPost = p; editText = t }, onDelete = feed::delete, onHide = feed::hide, onMute = feed::mute, onReport = feed::report) }
        }
        item { Spacer(Modifier.height(12.dp)) }
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
                    listOf(
                        StoryVisibility.CONTACTS to "جهات اتصالي",
                        StoryVisibility.EVERYONE to "الجميع"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = storyVisibility == value,
                            onClick = { storyVisibility = value },
                            label = { Text(label) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showStoryAudience = false
                    storyPicker.launch(arrayOf("image/*", "video/*"))
                }) { Text("اختيار صورة أو فيديو") }
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
                        ThreadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = AqyalGold)
                        is ThreadState.Error -> Text(threadState.message, color = MaterialTheme.colorScheme.error)
                        else -> LazyColumn(Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(feed.threadPosts, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (item.id == root.id) AqyalSurfaceRaised else AqyalSurfaceNavy)) {
                                    Column(Modifier.padding(12.dp)) { Text("@${item.authorUsername} · ${item.authorRedId}", color = AqyalCyanGlow, fontSize = 10.sp); Text(item.text) }
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
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Card { Text(quoted.text, Modifier.padding(12.dp), color = Color.Gray) }; OutlinedTextField(quoteText, { quoteText = it }, Modifier.fillMaxWidth(), label = { Text("تعليقك") }, maxLines = 5) } },
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
            onReply = { story, text ->
                RedConnectionService.sendRichText(
                    context,
                    story.ownerRedId,
                    conversationId(account.redId, story.ownerRedId),
                    RichMessage(action = "STORY_REPLY", text = text, replyTo = story.id)
                )
            }
        )
    }
}

@Composable

private fun StoryCircle(label: String, own: Boolean, click: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = click)) {
    Box(Modifier.size(66.dp).clip(CircleShape).background(if (own) AqyalGold else AqyalCyanGlow), contentAlignment = Alignment.Center) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(AqyalRoyalBlue), contentAlignment = Alignment.Center) {
            Icon(if (own) Icons.Default.Add else Icons.Default.Person, null)
        }
    }
    Text(label, fontSize = 11.sp, maxLines = 1)
}

@Composable

private fun PostCard(
    post: Post,
    currentRedId: String,
    onLike: (Post) -> Unit,
    onFollow: (Post) -> Unit,
    onVote: (Post, String) -> Unit,
    onThread: () -> Unit,
    onQuote: () -> Unit,
    onEdit: (Post, String) -> Unit = { _, _ -> },
    onDelete: (Post) -> Unit = {},
    onHide: (Post) -> Unit = {},
    onMute: (Post) -> Unit = {},
    onReport: (Post) -> Unit = {}
) = Card(
    Modifier.fillMaxWidth().padding(horizontal = WindowLayout.current().pagePadding),
    colors = CardDefaults.cardColors(containerColor = AqyalSurfaceNavy.copy(alpha = .96f)),
    shape = RoundedCornerShape(24.dp)
) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(YounesEmerald, AqyalCyanGlow, AqyalGold))
                ),
                contentAlignment = Alignment.Center
            ) { Text(post.authorDisplayName.take(1).ifBlank { "ي" }, color = Color(0xFF03120E), fontWeight = FontWeight.Black) }
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
            AssistChip({}, { Text(if (post.visibility == "LOCAL_YEMEN") "نبض محلي" else "عام") }, enabled = false, leadingIcon = { Icon(Icons.Default.Public, null, Modifier.size(15.dp)) })
            AssistChip({}, { Text(if (post.poll != null) "استطلاع" else if (post.parentId != null) "رد" else "منشور") }, enabled = false)
            if (post.kind != "POST") AssistChip({}, { Text(post.kind) }, enabled = false)
        }
        Text(post.text, fontSize = 17.sp, lineHeight = 25.sp, color = MaterialTheme.colorScheme.onSurface)
        if (post.hashtags.isNotEmpty() || post.mentions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                post.hashtags.forEach { tag -> Text(tag, color = AqyalCyanGlow, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                post.mentions.forEach { m -> Text(m, color = YounesEmerald, fontSize = 13.sp) }
            }
        }
        post.linkCard?.let { card ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(card.title ?: card.url, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(card.description ?: "", color = Color.Gray, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
        if (post.editedAt != null) Text("تم التعديل", color = Color.Gray, fontSize = 11.sp)
        post.quotePostId?.let { quotedId ->
            Card(colors = CardDefaults.cardColors(containerColor = AqyalSurfaceRaised.copy(alpha = .72f))) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Repeat, null, tint = AqyalGold, modifier = Modifier.size(18.dp))
                    Text(" اقتباس يونس · ${quotedId.take(8)}", color = AqyalGold, fontSize = 12.sp)
                }
            }
        }
        post.poll?.let { poll ->
            val totalVotes = poll.options.sumOf { it.votes }.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                poll.options.forEach { option ->
                    val ratio = (option.votes.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
                    Card(
                        Modifier.fillMaxWidth().clickable { onVote(post, option.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(option.text, fontWeight = FontWeight.SemiBold)
                                Text("${(ratio * 100).toInt()}%", color = AqyalCyanGlow, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                                color = YounesEmerald,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            Text("${option.votes} صوت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Text("إجمالي الأصوات: ${poll.options.sumOf { it.votes }}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .28f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PostAction(Icons.Default.FavoriteBorder, "${post.reactionCounts["LIKE"] ?: 0}", true) { onLike(post) }
            PostAction(Icons.AutoMirrored.Filled.Chat, post.replyCount.toString(), true, onThread)
            PostAction(Icons.Default.Repeat, "اقتباس", true, onQuote)
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
                runCatching { context.startActivity(Intent.createChooser(intent, "مشاركة منشور يونس")) }
            }
        }
    }
}

@Composable private fun PostAction(icon: ImageVector, label: String, enabled: Boolean, action: () -> Unit) = TextButton(action, enabled = enabled) { Icon(icon, label, Modifier.size(18.dp)); Text(" $label", fontSize = 11.sp) }
@Composable private fun Avatar(text: String) = Box(Modifier.size(42.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) { Text(text, color = Color.Black, fontWeight = FontWeight.Black) }

@Composable private fun GroupAvatar(group: com.red.sovereign.groups.Group, groups: GroupViewModel) {
    LaunchedEffect(group.avatarUrl) { groups.loadAvatar(group) }
    val image = groups.avatars[group.id]
    if (image != null) Image(image, group.name, Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Avatar(group.name.take(1))
}

