package dev.mosaic.app.data.source

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import dev.mosaic.app.core.model.FolderSelection

/** Unlike OpenDocumentTree, retains the returned flags so we never assume a grant. */
class FolderPickerContract : ActivityResultContract<Unit, FolderSelection?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )

    override fun parseResult(resultCode: Int, intent: Intent?): FolderSelection? {
        if (resultCode != Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        return FolderSelection(uri.toString(), intent.flags)
    }
}
