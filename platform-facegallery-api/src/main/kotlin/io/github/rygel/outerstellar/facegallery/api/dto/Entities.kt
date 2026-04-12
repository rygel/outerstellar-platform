package io.github.rygel.outerstellar.facegallery.api.dto

import java.time.Instant
import java.util.UUID

data class Session(
    val id: UUID,
    val name: String,
    val status: SessionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val settings: ProcessingSettings = ProcessingSettings(),
)

enum class SessionStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class ProcessingSettings(val cropPadding: Int = 45, val tolerance: Double = 0.6)

data class Face(
    val id: UUID,
    val sessionId: UUID,
    val name: String? = null,
    val primaryPhotoId: UUID? = null,
    val groupId: UUID? = null,
    val disabled: Boolean = false,
    val encoding: List<Double>,
    val createdAt: Instant,
)

data class FaceGroup(
    val id: UUID,
    val sessionId: UUID,
    val primaryFaceId: UUID,
    val disabled: Boolean = false,
    val createdAt: Instant,
)

data class Photo(
    val id: UUID,
    val sessionId: UUID,
    val filePath: String,
    val thumbnailPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val createdAt: Instant,
)

data class PhotoFaceAssignment(val id: UUID, val photoId: UUID, val faceId: UUID, val bbox: BoundingBox? = null)

data class BoundingBox(val x: Int, val y: Int, val width: Int, val height: Int)

data class JobProgress(
    val sessionId: UUID,
    val totalPhotos: Int,
    val processedPhotos: Int,
    val detectedFaces: Int,
    val status: SessionStatus,
    val message: String? = null,
)
