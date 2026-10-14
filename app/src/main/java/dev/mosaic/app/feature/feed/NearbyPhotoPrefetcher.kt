package dev.mosaic.app.feature.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun staticPhotoPrefetchCandidates(
    candidates: List<NearbyMediaCandidate>,
    anchorPosition: Int?,
): List<NearbyMediaCandidate> = candidates.filter { candidate ->
    candidate.feedIndex != anchorPosition && candidate.post.media.document.kind == dev.mosaic.app.core.model.MediaKind.Image
}

/** Prefetches only neighboring static photos and caches their decoded intrinsic aspect ratios. */
@Composable
internal fun NearbyPhotoPrefetcher(
    candidates: List<NearbyMediaCandidate>,
    anchorPosition: Int?,
    imageLoader: ImageLoader,
    enabled: Boolean,
    onPhotoAspectRatio: (NearbyMediaCandidate, Float) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val onPhotoAspectRatioState = rememberUpdatedState(onPhotoAspectRatio)
    val photoCandidates = remember(candidates, anchorPosition) {
        staticPhotoPrefetchCandidates(candidates, anchorPosition)
    }
    val requests = remember(photoCandidates, context) {
        photoCandidates.map { candidate ->
            candidate to createPhotoPrefetchRequest(context, candidate.post)
        }
    }

    LaunchedEffect(photoCandidates, anchorPosition) {
        val positions = photoCandidates.joinToString(",") {
            "${it.feedIndex}:${it.post.media.document.kind.name.lowercase()}:${it.direction}"
        }.ifBlank { "none" }
        MediaDiagnostics.log(context, "static-photo prefetch anchor=${anchorPosition ?: -1} candidates=$positions")
    }

    LaunchedEffect(imageLoader, requests, enabled) {
        if (!enabled) return@LaunchedEffect
        coroutineScope {
            requests.map { (candidate, request) ->
                async {
                    try {
                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val ratio = intrinsicImageAspectRatio(result.image.width, result.image.height)
                            if (ratio != null) onPhotoAspectRatioState.value(candidate, ratio)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A missing grant/unsupported local image stays isolated to that item.
                    }
                }
            }.awaitAll()
        }
    }
}
