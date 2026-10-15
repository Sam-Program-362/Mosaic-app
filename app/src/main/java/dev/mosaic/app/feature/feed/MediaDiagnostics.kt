package dev.mosaic.app.feature.feed

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Small, debug-build-only media diagnostics. Identifiers are hashed; local paths are never logged. */
internal object MediaDiagnostics {
    private const val TAG = "MosaicMedia"
    private val nextPlayerToken = AtomicLong(1L)
    private val unreleasedPlayerTokens = ConcurrentHashMap.newKeySet<Long>()

    fun log(context: Context, message: String) {
        if (isDebuggable(context)) Log.d(TAG, message)
    }

    /** Count ExoPlayer instances from allocation through release, including failed preparation. */
    fun playerCreated(context: Context, feedPosition: Int? = null, mediaKey: String? = null): Long? {
        if (!isDebuggable(context)) return null
        val token = nextPlayerToken.getAndIncrement()
        unreleasedPlayerTokens.add(token)
        val identity = mediaKey?.let(::shortId)?.let { " pos=${feedPosition ?: -1} id=$it" }.orEmpty()
        log(
            context,
            "player instance acquired token=$token$identity unreleased=${unreleasedPlayerTokens.size}",
        )
        return token
    }

    fun playerReleased(
        context: Context,
        token: Long?,
        feedPosition: Int? = null,
        mediaKey: String? = null,
        state: String? = null,
        firstFrameRendered: Boolean? = null,
    ) {
        if (token == null) return
        val removed = unreleasedPlayerTokens.remove(token)
        val identity = mediaKey?.let(::shortId)?.let { " pos=${feedPosition ?: -1} id=$it" }.orEmpty()
        val playback = listOfNotNull(state?.let { "state=$it" }, firstFrameRendered?.let { "firstFrame=$it" })
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ", prefix = " ")
            .orEmpty()
        log(
            context,
            "player instance released token=$token$identity$playback tracked=$removed " +
                "unreleased=${unreleasedPlayerTokens.size}",
        )
    }

    fun unreleasedPlayerCount(): Int = unreleasedPlayerTokens.size

    fun shortId(identifier: String): String = Integer.toUnsignedString(identifier.hashCode(), 16)

    fun causeTypes(throwable: Throwable?): String =
        generateSequence(throwable) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString("->") { it.javaClass.simpleName.ifBlank { "Throwable" } }
            .ifBlank { "unknown" }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private const val MAX_CAUSE_DEPTH = 4
}
