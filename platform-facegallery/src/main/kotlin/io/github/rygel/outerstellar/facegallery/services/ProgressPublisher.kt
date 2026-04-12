package io.github.rygel.outerstellar.facegallery.services

import io.github.rygel.outerstellar.facegallery.api.dto.JobProgress
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ProgressPublisher {
    private val flows = mutableMapOf<UUID, MutableSharedFlow<JobProgress>>()
    private val lastProgress = mutableMapOf<UUID, JobProgress>()

    fun getFlow(sessionId: UUID): SharedFlow<JobProgress> {
        return flows.getOrPut(sessionId) { MutableSharedFlow(replay = 1) }.asSharedFlow()
    }

    fun emitProgress(progress: JobProgress) {
        lastProgress[progress.sessionId] = progress
        flows[progress.sessionId]?.tryEmit(progress)
    }

    fun getCurrentProgress(sessionId: UUID): JobProgress? {
        return lastProgress[sessionId]
    }

    fun removeFlow(sessionId: UUID) {
        flows.remove(sessionId)
        lastProgress.remove(sessionId)
    }
}
