package dev.mosaic.app.data.source

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderPickerContractTest {
    private val contract = FolderPickerContract()

    @Test fun requestsNativeReadOnlyTreeAccessWithoutWritePermission() {
        val intent = contract.createIntent(RuntimeEnvironment.getApplication(), Unit)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test fun cancellationDoesNotProduceASelection() {
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, selectedIntent()))
    }

    @Test fun absentResultUriDoesNotProduceASelection() {
        assertNull(contract.parseResult(Activity.RESULT_OK, Intent()))
    }

    @Test fun retainsActualFlagsInsteadOfInventingPersistentAccess() {
        val selection = contract.parseResult(Activity.RESULT_OK, selectedIntent())
        assertEquals("content://local.test/tree/root", selection?.treeUri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, selection?.grantFlags)
    }

    private fun selectedIntent() = Intent().apply {
        data = Uri.parse("content://local.test/tree/root")
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
