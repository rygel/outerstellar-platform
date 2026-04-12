package io.github.rygel.outerstellar.facegallery.services

import io.github.rygel.outerstellar.facegallery.api.dto.Face
import io.github.rygel.outerstellar.facegallery.api.dto.JobProgress
import io.github.rygel.outerstellar.facegallery.api.dto.PhotoFaceAssignment
import io.github.rygel.outerstellar.facegallery.api.dto.ProcessingSettings
import io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus
import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import io.github.rygel.outerstellar.facegallery.api.service.FaceDetectionService
import io.github.rygel.outerstellar.facegallery.api.service.ProcessingService
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking

private data class ProgressCounts(val total: Int, val processed: Int, val totalFaces: Int)

class FaceGalleryProcessingService(
    private val sessionRepository: SessionRepository,
    private val faceRepository: FaceRepository,
    private val photoRepository: PhotoRepository,
    private val photoFaceAssignmentRepository: PhotoFaceAssignmentRepository,
    private val faceDetectionService: FaceDetectionService,
) : ProcessingService {
    private var settings = ProcessingSettings()

    override fun getSettings(): ProcessingSettings = settings

    override fun updateSettings(newSettings: ProcessingSettings) {
        settings = newSettings
    }

    override fun start(sessionId: UUID) {
        runBlocking {
            val session = sessionRepository.findById(sessionId) ?: return@runBlocking
            sessionRepository.update(session.copy(status = SessionStatus.PROCESSING))

            val photos = photoRepository.findBySessionId(sessionId)
            val total = photos.size
            var processed = 0
            var totalFaces = 0

            emitProgress(sessionId, ProgressCounts(total, 0, 0), SessionStatus.PROCESSING, "Starting...")

            for (photo in photos) {
                if (isCancelled(sessionId)) break

                try {
                    totalFaces += processPhoto(sessionId, photo.id, photo.filePath)
                    processed++
                    emitProgress(
                        sessionId,
                        ProgressCounts(total, processed, totalFaces),
                        SessionStatus.PROCESSING,
                        "Processed $processed/$total photos, found $totalFaces faces",
                    )
                } catch (e: IOException) {
                    processed++
                    emitProgress(
                        sessionId,
                        ProgressCounts(total, processed, totalFaces),
                        SessionStatus.PROCESSING,
                        "Error reading file: ${e.message}",
                    )
                }
            }

            val finalStatus = determineFinalStatus(sessionId)
            sessionRepository.update(session.copy(status = finalStatus))
            emitProgress(
                sessionId,
                ProgressCounts(total, processed, totalFaces),
                finalStatus,
                "Complete! Found $totalFaces faces in $processed photos",
            )
        }
    }

    private fun isCancelled(sessionId: UUID): Boolean {
        return ProgressPublisher.getCurrentProgress(sessionId)?.status == SessionStatus.CANCELLED
    }

    private fun determineFinalStatus(sessionId: UUID): SessionStatus {
        return if (ProgressPublisher.getCurrentProgress(sessionId)?.status != SessionStatus.CANCELLED) {
            SessionStatus.COMPLETED
        } else {
            SessionStatus.CANCELLED
        }
    }

    private fun emitProgress(sessionId: UUID, counts: ProgressCounts, status: SessionStatus, message: String) {
        ProgressPublisher.emitProgress(
            JobProgress(
                sessionId = sessionId,
                totalPhotos = counts.total,
                processedPhotos = counts.processed,
                detectedFaces = counts.totalFaces,
                status = status,
                message = message,
            )
        )
    }

    private suspend fun processPhoto(sessionId: UUID, photoId: UUID, filePath: String): Int {
        val imageBytes = Files.readAllBytes(Paths.get(filePath))
        val result = faceDetectionService.detectFaces(imageBytes)
        var facesCreated = 0

        for (faceResult in result.faces) {
            val face =
                Face(
                    id = UUID.randomUUID(),
                    sessionId = sessionId,
                    encoding = faceResult.encoding,
                    createdAt = Instant.now(),
                )
            faceRepository.create(face)
            facesCreated++

            photoFaceAssignmentRepository.create(
                PhotoFaceAssignment(id = UUID.randomUUID(), photoId = photoId, faceId = face.id, bbox = faceResult.bbox)
            )
        }
        return facesCreated
    }

    override fun cancel(sessionId: UUID) {
        emitProgress(sessionId, ProgressCounts(0, 0, 0), SessionStatus.CANCELLED, "Cancelled")
    }
}
