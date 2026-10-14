package dev.mosaic.app.feature.feed

import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import dev.mosaic.app.core.model.MediaKind
import dev.mosaic.app.ui.theme.LocalMosaicColors
import kotlinx.coroutines.delay

@Composable
internal fun LocalMediaPreview(
    post: FeedPost,
    active: Boolean,
    imageLoader: ImageLoader,
    onBounds: (Rect) -> Unit = {},
    viewer: Boolean = false,
    onOpenViewer: (() -> Unit)? = null,
    videoPreloader: VideoPreloadSession? = null,
    feedPosition: Int = -1,
    preloadingEnabled: Boolean = true,
) {
    val document = post.media.document
    val colors = LocalMosaicColors.current
    var ratio by remember(document.contentUri, document.modifiedAtMillis) { mutableFloatStateOf(4f / 3f) }
    var savedPositionMillis by remember(post.media.key.stableKey) { mutableLongStateOf(0L) }
    var savedPlayWhenReady by remember(post.media.key.stableKey) { mutableStateOf(true) }
    val clickModifier = onOpenViewer?.let {
        Modifier.clickable(role = Role.Button, onClickLabel = "Open full-screen viewer", onClick = it)
    } ?: Modifier
    val sizeModifier = if (viewer) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    BoxWithConstraints(sizeModifier.then(clickModifier)
        .onGloballyPositioned { onBounds(it.boundsInRoot()) }
        .background(if (viewer) Color.Black else colors.mediaBackdrop)) {
        val previewHeight = if (viewer) maxHeight else (maxWidth / ratio).coerceIn(120.dp, 520.dp)
        val previewSize = if (viewer) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(previewHeight)
        Box(previewSize, contentAlignment = Alignment.Center) {
            when {
                document.kind == MediaKind.Video && active -> VideoPreview(
                    post = post,
                    viewer = viewer,
                    aspectRatio = ratio,
                    imageLoader = imageLoader,
                    videoPreloader = videoPreloader,
                    feedPosition = feedPosition,
                    preloadingEnabled = preloadingEnabled,
                    initialPositionMillis = savedPositionMillis,
                    initiallyPlayWhenReady = savedPlayWhenReady,
                    onPlaybackStateCaptured = { position, playWhenReady ->
                        savedPositionMillis = position
                        savedPlayWhenReady = playWhenReady
                    },
                    onRatio = { ratio = it },
                )
                document.kind == MediaKind.Video -> VideoThumbnail(
                    post = post,
                    imageLoader = imageLoader,
                    onRatio = { ratio = it },
                )
                document.kind == MediaKind.Gif && !active -> Text("GIF · playback paused", color = colors.textSecondary)
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
        ImageRequest.Builder(context).data(Uri.parse(uri))
            .memoryCacheKey(if (animated) "$uri@$modified:true" else staticImageCacheKey(uri, modified))
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

@Composable
private fun VideoThumbnail(
    post: FeedPost,
    imageLoader: ImageLoader,
    onRatio: (Float) -> Unit,
    showLoadingIndicator: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val request = remember(post.media.key, post.media.document.contentUri, post.media.document.modifiedAtMillis, context) {
        createPrefetchRequest(context, post)
    }
    var loading by remember(request) { mutableStateOf(true) }
    var failed by remember(request) { mutableStateOf(false) }
    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = "${mediaTitle(post.media.document.fullName)} video thumbnail",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
        onSuccess = {
            loading = false
            val image = it.result.image
            if (image.width > 0 && image.height > 0) onRatio(image.width.toFloat() / image.height)
        },
        onError = { loading = false; failed = true },
    )
    if (loading && showLoadingIndicator) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
    }
    if (failed) {
        Text("Video thumbnail unavailable.", Modifier.padding(16.dp), color = LocalMosaicColors.current.textSecondary)
    }
}

internal data class VideoFrameFallback(
    val showThumbnail: Boolean,
    val showLoading: Boolean,
    val showError: Boolean,
)

internal fun videoFrameFallback(hasRenderedFirstFrame: Boolean, failed: Boolean, loading: Boolean) =
    VideoFrameFallback(
        showThumbnail = !hasRenderedFirstFrame,
        showLoading = loading && !failed && !hasRenderedFirstFrame,
        showError = failed,
    )

/** Only composed for the one visible video while the app/feed is active. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPreview(
    post: FeedPost,
    viewer: Boolean,
    aspectRatio: Float,
    imageLoader: ImageLoader,
    videoPreloader: VideoPreloadSession?,
    feedPosition: Int,
    preloadingEnabled: Boolean,
    initialPositionMillis: Long,
    initiallyPlayWhenReady: Boolean,
    onPlaybackStateCaptured: (Long, Boolean) -> Unit,
    onRatio: (Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val colors = LocalMosaicColors.current
    val document = post.media.document
    val uri = document.contentUri
    val stableKey = post.media.key.stableKey
    var failed by remember(stableKey, uri) { mutableStateOf(false) }
    var loading by remember(stableKey, uri) { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(stableKey, uri) { mutableStateOf(false) }
    var muted by remember(stableKey) { mutableStateOf(true) }
    var shouldPlay by remember(stableKey) { mutableStateOf(initiallyPlayWhenReady) }
    var currentPositionMillis by remember(stableKey) { mutableLongStateOf(initialPositionMillis) }
    var durationMillis by remember(stableKey) { mutableLongStateOf(0L) }
    var isSeeking by remember(stableKey) { mutableStateOf(false) }
    var seekPositionMillis by remember(stableKey) { mutableFloatStateOf(initialPositionMillis.toFloat()) }
    var controlsVisible by remember(stableKey, viewer) { mutableStateOf(true) }
    var seekFeedback by remember(stableKey) { mutableStateOf<String?>(null) }
    var seekFeedbackToken by remember(stableKey) { mutableIntStateOf(0) }
    val (player, playerDiagnosticToken) = remember(stableKey, uri, videoPreloader) {
        try {
            val createdPlayer = videoPreloader?.createPlayer() ?: ExoPlayer.Builder(context).build()
            createdPlayer to MediaDiagnostics.playerCreated(context)
        } catch (error: Exception) {
            MediaDiagnostics.log(
                context,
                "player create error pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                    "cause=${MediaDiagnostics.causeTypes(error)}",
            )
            throw error
        }
    }
    val audioAttributes = remember {
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
    }
    val viewHolder = remember(player) {
        object {
            var frame: AspectRatioFrameLayout? = null
            var texture: TextureView? = null
        }
    }

    val latestPreloadingEnabled = rememberUpdatedState(preloadingEnabled)
    DisposableEffect(player, stableKey, uri, videoPreloader) {
        val activationStartedAt = SystemClock.elapsedRealtime()
        MediaDiagnostics.log(
            context,
            "player active token=${playerDiagnosticToken ?: -1} pos=$feedPosition " +
                "id=${MediaDiagnostics.shortId(stableKey)} viewer=$viewer " +
                "unreleased=${MediaDiagnostics.unreleasedPlayerCount()}",
        )
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failed = true
                loading = false
                MediaDiagnostics.log(
                    context,
                    "player error pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                        "code=${error.errorCodeName} activationToErrorMs=${SystemClock.elapsedRealtime() - activationStartedAt} " +
                        "cause=${MediaDiagnostics.causeTypes(error.cause)}",
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                loading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                MediaDiagnostics.log(
                    context,
                    "player state=$stateName pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                        "activationMs=${SystemClock.elapsedRealtime() - activationStartedAt}",
                )
            }

            override fun onRenderedFirstFrame() {
                if (!hasRenderedFirstFrame) {
                    hasRenderedFirstFrame = true
                    MediaDiagnostics.log(
                        context,
                        "player first-frame pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                            "activationToFirstFrameMs=${SystemClock.elapsedRealtime() - activationStartedAt}",
                    )
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldPlay = playWhenReady
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
        try {
            val source = videoPreloader?.sourceForPlayback(stableKey, uri, feedPosition)
            if (source != null) player.setMediaSource(source)
            else player.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            if (initialPositionMillis > 0L) player.seekTo(initialPositionMillis)
            player.prepare()
            player.playWhenReady = initiallyPlayWhenReady
        } catch (error: Exception) {
            failed = true
            loading = false
            MediaDiagnostics.log(
                context,
                "player activation error pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                    "cause=${MediaDiagnostics.causeTypes(error)}",
            )
        }
        onDispose {
            onPlaybackStateCaptured(player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
            player.removeListener(listener)
            viewHolder.texture?.let { texture ->
                MediaDiagnostics.log(
                    context,
                    "texture detach phase=player-dispose token=${playerDiagnosticToken ?: -1} available=${texture.isAvailable}",
                )
                player.clearVideoTextureView(texture)
            }
            viewHolder.texture = null
            viewHolder.frame = null
            player.release()
            MediaDiagnostics.playerReleased(context, playerDiagnosticToken)
            videoPreloader?.releasePlayback(stableKey, latestPreloadingEnabled.value)
            MediaDiagnostics.log(
                context,
                "player cleanup complete pos=$feedPosition id=${MediaDiagnostics.shortId(stableKey)} " +
                    "unreleased=${MediaDiagnostics.unreleasedPlayerCount()}",
            )
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            durationMillis = if (duration == C.TIME_UNSET || duration < 0L) 0L else duration
            if (!isSeeking) currentPositionMillis = player.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }
    LaunchedEffect(controlsVisible, shouldPlay, viewer, failed, isSeeking) {
        if (viewer && controlsVisible && shouldPlay && !failed && !isSeeking) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }
    LaunchedEffect(seekFeedbackToken) {
        if (seekFeedback != null) {
            delay(SEEK_FEEDBACK_MILLIS)
            seekFeedback = null
        }
    }

    val fallback = videoFrameFallback(hasRenderedFirstFrame, failed, loading)

    fun seekBy(offsetMillis: Long) {
        val target = seekVideoPosition(player.currentPosition, player.duration, offsetMillis)
        player.seekTo(target)
        currentPositionMillis = target
        seekPositionMillis = target.toFloat()
        isSeeking = false
        seekFeedback = if (offsetMillis < 0L) "−10s" else "+10s"
        seekFeedbackToken++
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                val texture = TextureView(viewContext)
                AspectRatioFrameLayout(viewContext).apply {
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    setAspectRatio(aspectRatio)
                    addView(
                        texture,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    player.setVideoTextureView(texture)
                    MediaDiagnostics.log(
                        context,
                        "texture attach token=${playerDiagnosticToken ?: -1} available=${texture.isAvailable}",
                    )
                    viewHolder.frame = this
                    viewHolder.texture = texture
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.setAspectRatio(aspectRatio) },
            onRelease = { frame ->
                viewHolder.texture?.let { texture ->
                    MediaDiagnostics.log(
                        context,
                        "texture detach phase=android-view-release token=${playerDiagnosticToken ?: -1} " +
                            "available=${texture.isAvailable}",
                    )
                    player.clearVideoTextureView(texture)
                }
                if (viewHolder.frame === frame) {
                    viewHolder.texture = null
                    viewHolder.frame = null
                }
            },
        )
        if (fallback.showThumbnail) {
            VideoThumbnail(
                post = post,
                imageLoader = imageLoader,
                onRatio = onRatio,
                showLoadingIndicator = false,
            )
        }
        if (viewer) {
            Box(
                Modifier.fillMaxSize().pointerInput(stableKey, viewer) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            seekBy(if (offset.x < size.width / 2f) -VIDEO_SEEK_STEP_MILLIS else VIDEO_SEEK_STEP_MILLIS)
                        },
                    )
                },
            )
        }
        seekFeedback?.takeIf { viewer }?.let { feedback ->
            Box(
                Modifier.align(if (feedback.startsWith("−")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(feedback, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (fallback.showError || fallback.showLoading) {
            Text(
                if (fallback.showError) "This video could not be opened. Check file access or format."
                else "Loading video…",
                Modifier.align(Alignment.Center).background(colors.surfaceRaised).padding(12.dp),
                color = colors.textPrimary,
            )
        }
        if (!failed) {
            AnimatedVisibility(
                visible = !viewer || controlsVisible,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = if (viewer) VIEWER_CONTROLS_BOTTOM_INSET.dp else 4.dp),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                VideoControls(
                    playWhenReady = shouldPlay,
                    muted = muted,
                    positionMillis = currentPositionMillis,
                    durationMillis = durationMillis,
                    isSeeking = isSeeking,
                    seekPositionMillis = seekPositionMillis,
                    onPlayPause = { player.playWhenReady = !player.playWhenReady },
                    onMuteToggle = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                        player.setAudioAttributes(audioAttributes, !muted)
                    },
                    onSeekChange = { value ->
                        isSeeking = true
                        seekPositionMillis = value
                    },
                    onSeekFinished = {
                        val target = seekPositionMillis.toLong().coerceAtLeast(0L)
                        player.seekTo(target)
                        currentPositionMillis = target
                        isSeeking = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VideoControls(
    playWhenReady: Boolean,
    muted: Boolean,
    positionMillis: Long,
    durationMillis: Long,
    isSeeking: Boolean,
    seekPositionMillis: Float,
    onPlayPause: () -> Unit,
    onMuteToggle: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val maximumPosition = durationMillis.coerceAtLeast(1L).toFloat()
    val displayedPosition = (if (isSeeking) seekPositionMillis else positionMillis.toFloat())
        .coerceIn(0f, maximumPosition)
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playWhenReady) "Pause video" else "Play video",
                tint = Color.White,
            )
        }
        Text(
            formatVideoTime(if (isSeeking) seekPositionMillis.toLong() else positionMillis),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Slider(
            value = displayedPosition,
            onValueChange = onSeekChange,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..durationMillis.coerceAtLeast(1L).toFloat(),
            enabled = durationMillis > 0L,
            modifier = Modifier.weight(1f).height(48.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFFFF4500),
                inactiveTrackColor = Color.White.copy(alpha = 0.36f),
            ),
        )
        Text(
            if (durationMillis > 0L) formatVideoTime(durationMillis) else "--:--",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        IconButton(onClick = onMuteToggle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (muted) "Unmute video" else "Mute video",
                tint = Color.White,
            )
        }
    }
}

private const val VIDEO_SEEK_STEP_MILLIS = 10_000L
private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
private const val SEEK_FEEDBACK_MILLIS = 650L
private const val VIEWER_CONTROLS_BOTTOM_INSET = 56
