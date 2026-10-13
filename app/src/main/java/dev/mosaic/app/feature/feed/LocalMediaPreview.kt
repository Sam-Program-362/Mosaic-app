package dev.mosaic.app.feature.feed

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.ui.theme.LocalMosaicColors

@Composable
fun LocalMediaPreview(post: FeedPost, active: Boolean, imageLoader: ImageLoader, onBounds: (Rect) -> Unit = {}) {
    val document = post.media.document
    var ratio by remember(document.contentUri, document.modifiedAtMillis) { mutableFloatStateOf(4f / 3f) }
    BoxWithConstraints(Modifier.fillMaxWidth().onGloballyPositioned { onBounds(it.boundsInRoot()) }.background(LocalMosaicColors.current.mediaBackdrop)) {
        val previewHeight = (maxWidth / ratio).coerceIn(120.dp, 520.dp)
        Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.Center) {
            when {
                document.kind == MediaKind.Video && active -> VideoPreview(document.contentUri) { ratio = it }
                document.kind == MediaKind.Video -> Text("MP4 · playback paused", color = LocalMosaicColors.current.textSecondary)
                document.kind == MediaKind.Gif && !active -> Text("GIF · playback paused", color = LocalMosaicColors.current.textSecondary)
                else -> LocalImage(
                    document.contentUri, document.modifiedAtMillis, imageLoader,
                    description = mediaTitle(document.fullName), animated = document.kind == MediaKind.Gif,
                    onRatio = { ratio = it },
                )
            }
        }
    }
}

@Composable
private fun LocalImage(uri: String, modified: Long?, imageLoader: ImageLoader, description: String, animated: Boolean, onRatio: (Float) -> Unit) {
    val context = LocalContext.current
    val request = remember(uri, modified, animated, context) {
        ImageRequest.Builder(context).data(Uri.parse(uri)).memoryCacheKey("$uri@$modified:$animated")
            .apply {
                // Static photos (including WebP) must not silently become extra animated formats.
                if (!animated) decoderFactory(BitmapFactoryDecoder.Factory())
            }.build()
    }
    var loading by remember(request) { mutableStateOf(true) }
    var failed by remember(request) { mutableStateOf(false) }
    if (loading) Text("Loading media…", color = LocalMosaicColors.current.textSecondary)
    AsyncImage(
        model = request, imageLoader = imageLoader, contentDescription = description,
        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
        onSuccess = {
            loading = false
            val image = it.result.image
            if (image.width > 0 && image.height > 0) onRatio(image.width.toFloat() / image.height)
        },
        onError = { loading = false; failed = true },
    )
    if (failed) Text("Media is missing or cannot be decoded.\nThe rest of your feed is still available.",
        Modifier.padding(16.dp), color = LocalMosaicColors.current.textSecondary)
}

/** Only composed for the one visible animation while the app/feed is active. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPreview(uri: String, onRatio: (Float) -> Unit) {
    val context = LocalContext.current.applicationContext
    val colors = LocalMosaicColors.current
    var failed by remember(uri) { mutableStateOf(false) }
    var loading by remember(uri) { mutableStateOf(true) }
    var muted by remember(uri) { mutableStateOf(true) }
    var paused by remember(uri) { mutableStateOf(false) }
    val player = remember(uri) { ExoPlayer.Builder(context).build() }
    val audioAttributes = remember {
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
    }
    val viewHolder = remember(player) { object { var view: PlayerView? = null } }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failed = true; loading = false }
            override fun onPlaybackStateChanged(playbackState: Int) {
                loading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    onRatio(videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height)
                }
            }
        }
        player.addListener(listener)
        player.volume = 0f
        player.repeatMode = Player.REPEAT_MODE_ONE
        // Silent previews must not steal audio focus from the user's music.
        player.setAudioAttributes(audioAttributes, false)
        player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            viewHolder.view?.player = null
            viewHolder.view = null
            player.release()
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext -> PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                viewHolder.view = this
            } },
            modifier = Modifier.fillMaxSize(),
            onRelease = { it.player = null; if (viewHolder.view === it) viewHolder.view = null },
        )
        if (failed || loading) {
            Text(if (failed) "Video is unavailable or cannot be played." else "Loading video…",
                Modifier.align(Alignment.Center).background(colors.surfaceRaised).padding(12.dp),
                color = colors.textPrimary)
        }
        if (!failed) {
            Row(Modifier.align(Alignment.BottomEnd).background(colors.surfaceRaised)) {
                TextButton(onClick = { paused = !paused; player.playWhenReady = !paused }) {
                    Text(if (paused) "Play" else "Pause")
                }
                TextButton(onClick = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                    player.setAudioAttributes(audioAttributes, !muted)
                }) {
                    Text(if (muted) "Unmute" else "Mute")
                }
            }
        }
    }
}
