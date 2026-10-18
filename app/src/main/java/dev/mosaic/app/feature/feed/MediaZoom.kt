package dev.mosaic.app.feature.feed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize

/**
 * Pinch-zoom support for still images and GIFs inside the full-screen viewer
 * (the "inner feed"). The top-level feed never enables this viewport, and
 * videos never pass through it, so their behaviour is unchanged.
 */

internal const val MIN_IMAGE_ZOOM_SCALE = 1f
internal const val MAX_IMAGE_ZOOM_SCALE = 4f
internal const val DOUBLE_TAP_IMAGE_ZOOM_SCALE = 2.5f

internal const val ZOOMABLE_IMAGE_TEST_TAG = "zoomableImageViewport"

/** Current zoom scale of a [ZoomableImageViewport]; exposed for UI tests. */
internal val ZoomScaleSemanticsKey = SemanticsPropertyKey<Float>("ZoomScale")

internal var SemanticsPropertyReceiver.zoomScale by ZoomScaleSemanticsKey

/** Scale after applying a pinch [zoomChange], clamped to the supported range. */
internal fun zoomScaleAfter(currentScale: Float, zoomChange: Float): Float =
    (currentScale * zoomChange).coerceIn(MIN_IMAGE_ZOOM_SCALE, MAX_IMAGE_ZOOM_SCALE)

/**
 * Offset after [panChange] at [scale], clamped so the zoomed content always
 * covers the container: at scale 1 the offset is always zero, otherwise each
 * axis may move at most half of the overflow beyond the container.
 */
internal fun zoomOffsetAfter(
    currentOffset: Offset,
    panChange: Offset,
    scale: Float,
    containerWidthPx: Int,
    containerHeightPx: Int,
): Offset {
    if (scale <= MIN_IMAGE_ZOOM_SCALE || containerWidthPx <= 0 || containerHeightPx <= 0) {
        return Offset.Zero
    }
    val maxX = containerWidthPx * (scale - 1f) / 2f
    val maxY = containerHeightPx * (scale - 1f) / 2f
    val next = currentOffset + panChange
    return Offset(
        x = next.x.coerceIn(-maxX, maxX),
        y = next.y.coerceIn(-maxY, maxY),
    )
}

/**
 * Hosts [content] with pinch-to-zoom and double-tap zoom toggle when
 * [enabled]. Zoom state is remembered against [resetKey] (the media request),
 * so paging to another item or reusing the surface for another item always
 * starts at scale 1 — zoom never leaks between items.
 *
 * Gesture contract with the surrounding vertical pager: single-finger drags
 * at scale 1 are never consumed, so feed scrolling/paging keeps working;
 * touches are consumed only while pinching (two pointers) or while zoomed,
 * where panning moves within the zoomed image instead of paging.
 *
 * [onSingleTap] reports confirmed single taps only: the tap detector waits
 * out the double-tap window, so a double tap zooms without also firing a
 * single tap (no interface flicker), and pinch gestures never fire it.
 */
@Composable
internal fun ZoomableImageViewport(
    enabled: Boolean,
    resetKey: Any,
    modifier: Modifier = Modifier,
    onSingleTap: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    var scale by remember(resetKey) { mutableFloatStateOf(MIN_IMAGE_ZOOM_SCALE) }
    var offset by remember(resetKey) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(resetKey) { mutableStateOf(IntSize.Zero) }
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .testTag(ZOOMABLE_IMAGE_TEST_TAG)
            .semantics { zoomScale = scale }
            .pointerInput(resetKey) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var event: PointerEvent
                    do {
                        event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val pinching = event.changes.size > 1
                        if (pinching || scale > MIN_IMAGE_ZOOM_SCALE || zoomChange != 1f) {
                            if (zoomChange != 1f) {
                                val nextScale = zoomScaleAfter(scale, zoomChange)
                                scale = nextScale
                                offset = zoomOffsetAfter(
                                    offset, Offset.Zero, nextScale,
                                    containerSize.width, containerSize.height,
                                )
                            }
                            if (scale > MIN_IMAGE_ZOOM_SCALE && panChange != Offset.Zero) {
                                offset = zoomOffsetAfter(
                                    offset, panChange, scale,
                                    containerSize.width, containerSize.height,
                                )
                            }
                            event.changes.forEach { change -> change.consume() }
                        }
                    } while (event.changes.any { change -> change.pressed })
                }
            }
            .pointerInput(resetKey) {
                detectTapGestures(
                    onTap = { onSingleTap?.invoke() },
                    onDoubleTap = {
                        if (scale > MIN_IMAGE_ZOOM_SCALE) {
                            scale = MIN_IMAGE_ZOOM_SCALE
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_IMAGE_ZOOM_SCALE
                        }
                    },
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        ) {
            content()
        }
    }
}
