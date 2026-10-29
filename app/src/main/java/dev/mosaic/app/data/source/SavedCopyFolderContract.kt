package dev.mosaic.app.data.source

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * One-time Android 8/9 folder choice for saved copies. Unlike the read-only library
 * [FolderPickerContract], this asks for write access and a persistable grant, because Mosaic
 * creates its own "Mosaic/Saved" folder inside the chosen tree. Cancelling returns null.
 */
class SavedCopyFolderContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data?.toString()
    }
}
