package io.github.rygel.outerstellar.facegallery.di

import io.github.rygel.outerstellar.facegallery.api.config.FaceGalleryConfig
import io.github.rygel.outerstellar.facegallery.api.repository.FaceGroupRepository
import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import io.github.rygel.outerstellar.facegallery.services.FaceGalleryProcessingService
import io.github.rygel.outerstellar.facegallery.services.Http4kFaceDetectionService
import io.github.rygel.outerstellar.facegallery.services.ProgressPublisher
import org.jdbi.v3.core.Jdbi
import org.koin.core.module.Module
import org.koin.dsl.module

val faceGalleryModules: (FaceGalleryConfig) -> List<Module> = { config ->
    listOf(jdbiModule, serviceModule(config), processingModule)
}

val jdbiModule: Module = module {
    single { createJdbi() }
    single<SessionRepository> { JdbiSessionRepository(get()) }
    single<FaceRepository> { JdbiFaceRepository(get()) }
    single<FaceGroupRepository> { JdbiFaceGroupRepository(get()) }
    single<PhotoRepository> { JdbiPhotoRepository(get()) }
    single<PhotoFaceAssignmentRepository> { JdbiPhotoFaceAssignmentRepository(get()) }
}

val serviceModule: (FaceGalleryConfig) -> Module = { config ->
    module {
        single { Http4kFaceDetectionService(config.mlService) }
        single { FaceGalleryProcessingService(get(), get(), get(), get(), get()) }
    }
}

val processingModule: Module = module { single { ProgressPublisher } }

private fun createJdbi(): Jdbi {
    val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/facegallery"
    val databaseUser = System.getenv("DATABASE_USER") ?: "postgres"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "postgres"

    val ds =
        org.postgresql.ds.PGSimpleDataSource().apply {
            setURL(databaseUrl)
            user = databaseUser
            password = databasePassword
        }
    return Jdbi.create(ds)
}
