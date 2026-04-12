package io.github.rygel.outerstellar.facegallery.di

import io.github.rygel.outerstellar.facegallery.api.dto.Face
import io.github.rygel.outerstellar.facegallery.api.dto.FaceGroup
import io.github.rygel.outerstellar.facegallery.api.dto.Photo
import io.github.rygel.outerstellar.facegallery.api.dto.PhotoFaceAssignment
import io.github.rygel.outerstellar.facegallery.api.dto.Session
import io.github.rygel.outerstellar.facegallery.api.repository.FaceGroupRepository
import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import java.time.Instant
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiSessionRepository(private val jdbi: Jdbi) : SessionRepository {
    override fun create(session: Session): Session {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |INSERT INTO facegallery_sessions (id, name, status, created_at, updated_at,
                |crop_padding, tolerance) VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                    .trimMargin(),
                session.id,
                session.name,
                session.status.name,
                session.createdAt,
                session.updatedAt,
                session.settings.cropPadding,
                session.settings.tolerance,
            )
        }
        return session
    }

    override fun findById(id: UUID): Session? {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_sessions WHERE id = ?")
                .bind(0, id)
                .mapTo(Session::class.java)
                .findOne()
                .orElse(null)
        }
    }

    override fun findAll(): List<Session> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_sessions ORDER BY created_at DESC")
                .mapTo(Session::class.java)
                .list()
        }
    }

    override fun update(session: Session): Session {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |UPDATE facegallery_sessions SET name = ?, status = ?, updated_at = ?,
                |crop_padding = ?, tolerance = ? WHERE id = ?
                """
                    .trimMargin(),
                session.name,
                session.status.name,
                Instant.now(),
                session.settings.cropPadding,
                session.settings.tolerance,
                session.id,
            )
        }
        return session
    }

    override fun delete(id: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_sessions WHERE id = ?", id) }
    }
}

class JdbiFaceRepository(private val jdbi: Jdbi) : FaceRepository {
    override fun create(face: Face): Face {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |INSERT INTO facegallery_faces (id, session_id, name, primary_photo_id,
                |group_id, disabled, encoding, created_at)
                |VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """
                    .trimMargin(),
                face.id,
                face.sessionId,
                face.name,
                face.primaryPhotoId,
                face.groupId,
                face.disabled,
                face.encoding.joinToString(","),
                face.createdAt,
            )
        }
        return face
    }

    override fun findById(id: UUID): Face? {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_faces WHERE id = ?")
                .bind(0, id)
                .mapTo(Face::class.java)
                .findOne()
                .orElse(null)
        }
    }

    override fun findBySessionId(sessionId: UUID): List<Face> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_faces WHERE session_id = ? ORDER BY created_at")
                .bind(0, sessionId)
                .mapTo(Face::class.java)
                .list()
        }
    }

    override fun findUngroupedBySessionId(sessionId: UUID): List<Face> {
        return jdbi.open().use { handle ->
            handle
                .createQuery(
                    "SELECT * FROM facegallery_faces WHERE session_id = ? AND group_id IS NULL ORDER BY created_at"
                )
                .bind(0, sessionId)
                .mapTo(Face::class.java)
                .list()
        }
    }

    override fun update(face: Face): Face {
        jdbi.open().use { handle ->
            handle.execute(
                "UPDATE facegallery_faces SET name = ?, primary_photo_id = ?, group_id = ?, disabled = ? WHERE id = ?",
                face.name,
                face.primaryPhotoId,
                face.groupId,
                face.disabled,
                face.id,
            )
        }
        return face
    }

    override fun delete(id: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_faces WHERE id = ?", id) }
    }

    override fun deleteBySessionId(sessionId: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_faces WHERE session_id = ?", sessionId) }
    }
}

class JdbiFaceGroupRepository(private val jdbi: Jdbi) : FaceGroupRepository {
    override fun create(group: FaceGroup): FaceGroup {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |INSERT INTO facegallery_face_groups (id, session_id, primary_face_id,
                |disabled, created_at) VALUES (?, ?, ?, ?, ?)
                """
                    .trimMargin(),
                group.id,
                group.sessionId,
                group.primaryFaceId,
                group.disabled,
                group.createdAt,
            )
        }
        return group
    }

    override fun findById(id: UUID): FaceGroup? {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_face_groups WHERE id = ?")
                .bind(0, id)
                .mapTo(FaceGroup::class.java)
                .findOne()
                .orElse(null)
        }
    }

    override fun findBySessionId(sessionId: UUID): List<FaceGroup> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_face_groups WHERE session_id = ?")
                .bind(0, sessionId)
                .mapTo(FaceGroup::class.java)
                .list()
        }
    }

    override fun findFaceGroupForFace(faceId: UUID): FaceGroup? {
        return jdbi.open().use { handle ->
            handle
                .createQuery(
                    """
                    SELECT * FROM facegallery_face_groups
                    WHERE id = (
                        SELECT group_id FROM facegallery_faces WHERE id = ?
                    )
                    """
                        .trimIndent()
                )
                .bind(0, faceId)
                .mapTo(FaceGroup::class.java)
                .findOne()
                .orElse(null)
        }
    }

    override fun update(group: FaceGroup): FaceGroup {
        jdbi.open().use { handle ->
            handle.execute(
                "UPDATE facegallery_face_groups SET primary_face_id = ?, disabled = ? WHERE id = ?",
                group.primaryFaceId,
                group.disabled,
                group.id,
            )
        }
        return group
    }

    override fun delete(id: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_face_groups WHERE id = ?", id) }
    }

    override fun deleteBySessionId(sessionId: UUID) {
        jdbi.open().use { handle ->
            handle.execute("DELETE FROM facegallery_face_groups WHERE session_id = ?", sessionId)
        }
    }
}

class JdbiPhotoRepository(private val jdbi: Jdbi) : PhotoRepository {
    override fun create(photo: Photo): Photo {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |INSERT INTO facegallery_photos (id, session_id, file_path, thumbnail_path,
                |width, height, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                    .trimMargin(),
                photo.id,
                photo.sessionId,
                photo.filePath,
                photo.thumbnailPath,
                photo.width,
                photo.height,
                photo.createdAt,
            )
        }
        return photo
    }

    override fun findById(id: UUID): Photo? {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_photos WHERE id = ?")
                .bind(0, id)
                .mapTo(Photo::class.java)
                .findOne()
                .orElse(null)
        }
    }

    override fun findBySessionId(sessionId: UUID): List<Photo> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_photos WHERE session_id = ? ORDER BY created_at")
                .bind(0, sessionId)
                .mapTo(Photo::class.java)
                .list()
        }
    }

    override fun update(photo: Photo): Photo {
        jdbi.open().use { handle ->
            handle.execute(
                "UPDATE facegallery_photos SET thumbnail_path = ?, width = ?, height = ? WHERE id = ?",
                photo.thumbnailPath,
                photo.width,
                photo.height,
                photo.id,
            )
        }
        return photo
    }

    override fun delete(id: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_photos WHERE id = ?", id) }
    }

    override fun deleteBySessionId(sessionId: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_photos WHERE session_id = ?", sessionId) }
    }
}

class JdbiPhotoFaceAssignmentRepository(private val jdbi: Jdbi) : PhotoFaceAssignmentRepository {
    override fun create(assignment: PhotoFaceAssignment): PhotoFaceAssignment {
        jdbi.open().use { handle ->
            handle.execute(
                """
                |INSERT INTO facegallery_photo_face_assignments (id, photo_id, face_id,
                |bbox_x, bbox_y, bbox_width, bbox_height)
                |VALUES (?, ?, ?, ?, ?, ?, ?)
                """
                    .trimMargin(),
                assignment.id,
                assignment.photoId,
                assignment.faceId,
                assignment.bbox?.x,
                assignment.bbox?.y,
                assignment.bbox?.width,
                assignment.bbox?.height,
            )
        }
        return assignment
    }

    override fun findByPhotoId(photoId: UUID): List<PhotoFaceAssignment> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_photo_face_assignments WHERE photo_id = ?")
                .bind(0, photoId)
                .mapTo(PhotoFaceAssignment::class.java)
                .list()
        }
    }

    override fun findByFaceId(faceId: UUID): List<PhotoFaceAssignment> {
        return jdbi.open().use { handle ->
            handle
                .createQuery("SELECT * FROM facegallery_photo_face_assignments WHERE face_id = ?")
                .bind(0, faceId)
                .mapTo(PhotoFaceAssignment::class.java)
                .list()
        }
    }

    override fun delete(id: UUID) {
        jdbi.open().use { handle -> handle.execute("DELETE FROM facegallery_photo_face_assignments WHERE id = ?", id) }
    }

    override fun deleteByPhotoId(photoId: UUID) {
        jdbi.open().use { handle ->
            handle.execute("DELETE FROM facegallery_photo_face_assignments WHERE photo_id = ?", photoId)
        }
    }

    override fun deleteByFaceId(faceId: UUID) {
        jdbi.open().use { handle ->
            handle.execute("DELETE FROM facegallery_photo_face_assignments WHERE face_id = ?", faceId)
        }
    }
}
