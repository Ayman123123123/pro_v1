package com.red.sovereign.stories

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Media3 player for an authenticated video already downloaded into the app-private cache. */
@Composable
fun StoryVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    /** يُستدعى ~8 مرات/ثانية أثناء التشغيل (positionMs, durationMs) — لشريط تقدم الحالات. */
    onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
    /** يُستدعى عند نهاية التشغيل — للانتقال التلقائي للحالة التالية. */
    onEnded: (() -> Unit)? = null
) {
    // G3: سياق التطبيق يمنع تسرب Activity عبر ExoPlayer، وعدم التشغيل
    // التلقائي يمنع تعدد مشغلات تعمل معاً في القائمة. الإيقاف عند
    // ON_PAUSE يحرر الصوت عند مغادرة الشاشة.
    val appContext = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(uri) {
        ExoPlayer.Builder(appContext).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = autoPlay
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(lifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }
    // نهاية التشغيل → onEnded (للتقدم التلقائي في عارض الحالات).
    // rememberUpdatedState يمنع إغلاقًا قديمًا على onNext بعد التنقل بين الحالات.
    val currentOnEnded by rememberUpdatedState(onEnded)
    DisposableEffect(player) {
        if (currentOnEnded == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) currentOnEnded?.invoke()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // موضع التشغيل → onProgress. القراءة على الخيط الرئيسي رخيصة
    // (getters فقط) وبتردد 8Hz فلا تُغرق إعادة التركيب.
    val currentOnProgress by rememberUpdatedState(onProgress)
    if (onProgress != null) {
        LaunchedEffect(player, uri) {
            while (isActive) {
                delay(125)
                val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                currentOnProgress?.invoke(player.currentPosition.coerceAtLeast(0L), duration.coerceAtLeast(0L))
            }
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext -> PlayerView(viewContext).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            this.player = player
        } },
        update = { it.player = player }
    )
}
