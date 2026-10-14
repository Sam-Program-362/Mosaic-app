package dev.mosaic.app.feature.feed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/** Small, debug-build-only media diagnostics. Identifiers are hashed; local paths are never logged. */
internal object MediaDiagnostics {
    private const val TAG = "MosaicMedia"

    fun log(context: Context, message: String) {
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.d(TAG, message)
        }
    }

    fun shortId(identifier: String): String = Integer.toUnsignedString(identifier.hashCode(), 16)

    fun causeTypes(throwable: Throwable?): String =
        generateSequence(throwable) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString("->") { it.javaClass.simpleName.ifBlank { "Throwable" } }
            .ifBlank { "unknown" }

    private const val MAX_CAUSE_DEPTH = 4
}
