package io.github.rygel.outerstellar.facegallery.api.repository

import io.github.rygel.outerstellar.facegallery.api.dto.Face
import io.github.rygel.outerstellar.facegallery.api.dto.FaceGroup
import io.github.rygel.outerstellar.facegallery.api.dto.Photo
import io.github.rygel.outerstellar.facegallery.api.dto.PhotoFaceAssignment
import io.github.rygel.outerstellar.facegallery.api.dto.Session
import java.util.UUID

interface SessionRepository {
    fun create(session: Session): Session

    fun findById(id: UUID): Session?

    fun findAll(): List<Session>

    fun update(session: Session): Session

    fun delete(id: UUID)
}

interface FaceRepository {
    fun create(face: Face): Face

    fun findById(id: UUID): Face?

    fun findBySessionId(sessionId: UUID): List<Face>

    fun findUngroupedBySessionId(sessionId: UUID): List<Face>

    fun update(face: Face): Face

    fun delete(id: UUID)

    fun deleteBySessionId(sessionId: UUID)
}

interface FaceGroupRepository {
    fun create(group: FaceGroup): FaceGroup

    fun findById(id: UUID): FaceGroup?

    fun findBySessionId(sessionId: UUID): List<FaceGroup>

    fun findFaceGroupForFace(faceId: UUID): FaceGroup?

    fun update(group: FaceGroup): FaceGroup

    fun delete(id: UUID)

    fun deleteBySessionId(sessionId: UUID)
}

interface PhotoRepository {
    fun create(photo: Photo): Photo

    fun findById(id: UUID): Photo?

    fun findBySessionId(sessionId: UUID): List<Photo>

    fun update(photo: Photo): Photo

    fun delete(id: UUID)

    fun deleteBySessionId(sessionId: UUID)
}

interface PhotoFaceAssignmentRepository {
    fun create(assignment: PhotoFaceAssignment): PhotoFaceAssignment

    fun findByPhotoId(photoId: UUID): List<PhotoFaceAssignment>

    fun findByFaceId(faceId: UUID): List<PhotoFaceAssignment>

    fun delete(id: UUID)

    fun deleteByPhotoId(photoId: UUID)

    fun deleteByFaceId(faceId: UUID)
}
