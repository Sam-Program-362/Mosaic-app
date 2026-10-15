package dev.mosaic.app.feature.folders

import dev.mosaic.app.core.model.MediaKey

internal data class ResumeRefreshPlan(
    val preserveMedia: Set<MediaKey>,
    val regenerateFeedSession: Boolean,
)

/** Treats this process's first resume as a cold start and later resumes as preserved sessions. */
internal class ResumeRefreshPolicy {
    private var hasResumed = false

    fun next(protectedMedia: Set<MediaKey>): ResumeRefreshPlan {
        if (!hasResumed) {
            hasResumed = true
            return ResumeRefreshPlan(preserveMedia = emptySet(), regenerateFeedSession = true)
        }
        return ResumeRefreshPlan(preserveMedia = protectedMedia.toSet(), regenerateFeedSession = false)
    }
}
