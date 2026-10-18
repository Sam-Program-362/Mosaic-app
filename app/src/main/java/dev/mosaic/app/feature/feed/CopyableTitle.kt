package dev.mosaic.app.feature.feed

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

internal const val TITLE_COPIED_MESSAGE = "Title copied"

/**
 * A media title that copies its exact displayed text on long-press.
 *
 * The copied string is the already-formatted [mediaTitle] output shown to
 * the user (extension included or omitted exactly as displayed); no media
 * content is read. Uses the platform long-press interaction, so ordinary
 * taps keep their previous behaviour (including opening the viewer from a
 * feed card when [onClick] is supplied) and scrolling/paging gestures are
 * unaffected. Confirmation is a short "Title copied" toast.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CopyableTitleText(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = title,
        modifier = modifier.combinedClickable(
            role = if (onClick != null) Role.Button else null,
            onClickLabel = onClickLabel,
            onClick = { onClick?.invoke() },
            onLongClick = {
                clipboardManager.setText(AnnotatedString(title))
                Toast.makeText(context, TITLE_COPIED_MESSAGE, Toast.LENGTH_SHORT).show()
            },
        ),
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}
