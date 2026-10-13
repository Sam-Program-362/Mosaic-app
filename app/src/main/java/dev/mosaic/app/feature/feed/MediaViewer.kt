package dev.mosaic.app.feature.feed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.ImageLoader

/** Full-screen vertical paging over a frozen copy of the exact feed order opened by the user. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(
    snapshot: FeedSnapshot,
    initialPage: Int,
    imageLoader: ImageLoader,
    onClose: () -> Unit,
    onSaveCopy: ((FeedPost) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (snapshot.posts.isEmpty()) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("No media is available in this feed.", color = Color.White)
        }
        return
    }

    HideSystemBars()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val pagerState = rememberPagerState(initialPage.coerceIn(0, snapshot.posts.lastIndex)) {
        snapshot.posts.size
    }
    Box(modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,
            key = { page -> snapshot.posts[page].media.key.stableKey },
        ) { page ->
            val post = snapshot.posts[page]
            LocalMediaPreview(
                post = post,
                active = page == pagerState.currentPage && lifecycle.isAtLeast(Lifecycle.State.RESUMED),
                imageLoader = imageLoader,
                viewer = true,
            )
        }
        val currentPost = snapshot.posts[pagerState.currentPage.coerceIn(0, snapshot.posts.lastIndex)]
        ViewerChrome(
            post = currentPost,
            page = pagerState.currentPage + 1,
            pageCount = snapshot.posts.size,
            onClose = onClose,
            onSaveCopy = onSaveCopy,
            modifier = Modifier.fillMaxSize().zIndex(1f),
        )
    }
}

@Composable
private fun ViewerChrome(
    post: FeedPost,
    page: Int,
    pageCount: Int,
    onClose: () -> Unit,
    onSaveCopy: ((FeedPost) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.68f)).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close full-screen viewer", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(post.media.folderLabel, color = Color.White,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(mediaTitle(post.media.document.fullName), color = Color.White,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("$page / $pageCount", color = Color.White,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Row(
            Modifier.align(Alignment.Start).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.68f)).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Swipe up or down to browse",
                Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            onSaveCopy?.let { save ->
                TextButton(onClick = { save(post) }) { Text("Save copy", color = Color.White) }
            }
        }
    }
}

/** Hide bars only while this viewer is composed, and restore their previous visibility on exit. */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    val window = remember(view) { view.context.findActivity()?.window }
    DisposableEffect(window, view) {
        if (window == null) {
            onDispose { }
        } else {
            val decorView = window.decorView
            val controller = WindowCompat.getInsetsController(window, decorView)
            val rootInsets = ViewCompat.getRootWindowInsets(decorView)
            val statusBars = WindowInsetsCompat.Type.statusBars()
            val navigationBars = WindowInsetsCompat.Type.navigationBars()
            val statusWasVisible = rootInsets?.isVisible(statusBars) ?: true
            val navigationWasVisible = rootInsets?.isVisible(navigationBars) ?: true
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.systemBarsBehavior = previousBehavior
                if (statusWasVisible) controller.show(statusBars) else controller.hide(statusBars)
                if (navigationWasVisible) controller.show(navigationBars) else controller.hide(navigationBars)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
