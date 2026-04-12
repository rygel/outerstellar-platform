package io.github.rygel.outerstellar.facegallery.cli.di

import io.github.rygel.outerstellar.facegallery.api.dto.Face
import io.github.rygel.outerstellar.facegallery.api.dto.PhotoFaceAssignment
import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import io.github.rygel.outerstellar.facegallery.api.service.FaceDetectionService
import io.github.rygel.outerstellar.facegallery.api.service.ProcessingService
import io.github.rygel.outerstellar.facegallery.api.service.ProcessingSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking

class CliProcessingService(
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
            sessionRepository.update(
                session.copy(status = io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus.PROCESSING)
            )

            val photos = photoRepository.findBySessionId(sessionId)
            var processed = 0
            var totalFaces = 0

            for (photo in photos) {
                try {
                    totalFaces += processPhoto(sessionId, photo.id, photo.filePath)
                    processed++
                } catch (e: IOException) {
                    processed++
                }
            }

            sessionRepository.update(
                session.copy(status = io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus.COMPLETED)
            )
        }
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

            val bbox = faceResult.bbox
            photoFaceAssignmentRepository.create(
                PhotoFaceAssignment(
                    id = UUID.randomUUID(),
                    photoId = photoId,
                    faceId = face.id,
                    bbox =
                        io.github.rygel.outerstellar.facegallery.api.dto.BoundingBox(
                            x = bbox.x,
                            y = bbox.y,
                            width = bbox.width,
                            height = bbox.height,
                        ),
                )
            )
        }
        return facesCreated
    }

    override fun cancel(sessionId: UUID) {
        // CLI doesn't support cancellation
    }
}
