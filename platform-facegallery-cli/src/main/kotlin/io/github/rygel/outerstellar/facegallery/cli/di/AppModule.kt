package io.github.rygel.outerstellar.facegallery.cli.di

import io.github.rygel.outerstellar.facegallery.api.config.MlServiceConfig
import io.github.rygel.outerstellar.facegallery.api.repository.FaceGroupRepository
import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import io.github.rygel.outerstellar.facegallery.api.service.FaceDetectionService
import io.github.rygel.outerstellar.facegallery.api.service.ProcessingService
import io.github.rygel.outerstellar.facegallery.cli.services.Http4kFaceDetectionService
import org.jdbi.v3.core.Jdbi

object AppModule {
    val jdbi: Jdbi by lazy { Jdbi.create("jdbc:h2:./facegallery.db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "") }

    val sessionRepository: SessionRepository by lazy { JdbiSessionRepository(jdbi) }

    val faceRepository: FaceRepository by lazy { JdbiFaceRepository(jdbi) }

    val faceGroupRepository: FaceGroupRepository by lazy { JdbiFaceGroupRepository(jdbi) }

    val photoRepository: PhotoRepository by lazy { JdbiPhotoRepository(jdbi) }

    val photoFaceAssignmentRepository: PhotoFaceAssignmentRepository by lazy { JdbiPhotoFaceAssignmentRepository(jdbi) }

    val mlServiceConfig: MlServiceConfig by lazy { MlServiceConfig(baseUrl = "http://localhost:65432") }

    val faceDetectionService: FaceDetectionService by lazy { Http4kFaceDetectionService(mlServiceConfig) }

    val processingService: ProcessingService by lazy {
        CliProcessingService(
            sessionRepository,
            faceRepository,
            photoRepository,
            photoFaceAssignmentRepository,
            faceDetectionService,
        )
    }
}
