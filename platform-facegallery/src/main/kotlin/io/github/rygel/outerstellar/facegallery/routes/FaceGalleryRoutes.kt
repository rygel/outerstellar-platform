package io.github.rygel.outerstellar.facegallery.routes

import io.github.rygel.outerstellar.facegallery.api.repository.FaceRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoFaceAssignmentRepository
import io.github.rygel.outerstellar.facegallery.api.repository.PhotoRepository
import io.github.rygel.outerstellar.facegallery.api.repository.SessionRepository
import io.github.rygel.outerstellar.facegallery.api.service.ProcessingService
import io.github.rygel.outerstellar.facegallery.services.ProgressPublisher
import io.github.rygel.outerstellar.platform.infra.render
import io.github.rygel.outerstellar.platform.web.PluginContext
import io.github.rygel.outerstellar.platform.web.ServerRoutes
import java.time.Instant
import java.util.UUID
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.template.TemplateRenderer

class FaceGalleryRoutes(private val context: PluginContext) : ServerRoutes {
    private val renderer: TemplateRenderer = context.renderer
    private val sessionRepository: SessionRepository = context.koin()
    private val faceRepository: FaceRepository = context.koin()
    private val photoRepository: PhotoRepository = context.koin()
    private val photoFaceAssignmentRepository: PhotoFaceAssignmentRepository = context.koin()
    private val processingService: ProcessingService = context.koin()

    private val sessionIdPath = Path.uuid().of("id")
    private val faceIdPath = Path.uuid().of("faceId")

    override val routes =
        listOf(
            "/sessions" meta
                {
                    summary = "List sessions"
                } bindContract
                Method.GET to
                { request ->
                    val sessions = sessionRepository.findAll()
                    val viewModel = SessionsViewModel(sessions = sessions.map { it.toViewModel() })
                    renderer.render(context.buildPage(request, "Sessions", "/sessions", viewModel))
                },
            "/sessions" meta
                {
                    summary = "Create session"
                } bindContract
                Method.POST to
                { request ->
                    val name = request.form("name") ?: "New Session"
                    val session =
                        io.github.rygel.outerstellar.facegallery.api.dto.Session(
                            id = UUID.randomUUID(),
                            name = name,
                            status = io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus.PENDING,
                            createdAt = Instant.now(),
                            updatedAt = Instant.now(),
                        )
                    sessionRepository.create(session)
                    Response(Status.FOUND).header("location", "/sessions")
                },
            "/sessions/{id}" meta
                {
                    summary = "Get session"
                } bindContract
                Method.GET to
                { request ->
                    val sessionId = sessionIdPath(request)
                    val session = sessionRepository.findById(sessionId)
                    if (session != null) {
                        val photos = photoRepository.findBySessionId(sessionId)
                        val progress = ProgressPublisher.getCurrentProgress(sessionId)
                        val viewModel =
                            SessionDetailViewModel(
                                session = session.toViewModel(),
                                photos = photos,
                                progress = progress,
                            )
                        renderer.render(context.buildPage(request, session.name, "/sessions", viewModel))
                    } else {
                        Response(Status.NOT_FOUND)
                    }
                },
            "/sessions/{id}" meta
                {
                    summary = "Delete session"
                } bindContract
                Method.DELETE to
                { request ->
                    val sessionId = sessionIdPath(request)
                    processingService.cancel(sessionId)
                    sessionRepository.delete(sessionId)
                    Response(Status.NO_CONTENT)
                },
            "/sessions/{id}/process" meta
                {
                    summary = "Start processing"
                } bindContract
                Method.POST to
                { request ->
                    val sessionId = sessionIdPath(request)
                    processingService.start(sessionId)
                    Response(Status.FOUND).header("location", "/sessions/$sessionId")
                },
            "/sessions/{id}/photos" meta
                {
                    summary = "List photos"
                } bindContract
                Method.GET to
                { request ->
                    val sessionId = sessionIdPath(request)
                    val photos = photoRepository.findBySessionId(sessionId)
                    Response(Status.OK).body(photos.toString())
                },
            "/sessions/{id}/faces" meta
                {
                    summary = "List faces"
                } bindContract
                Method.GET to
                { request ->
                    val sessionId = sessionIdPath(request)
                    val faces = faceRepository.findBySessionId(sessionId)
                    Response(Status.OK).body(faces.toString())
                },
            "/sessions/{id}/faces/{faceId}" meta
                {
                    summary = "Delete face"
                } bindContract
                Method.DELETE to
                { request ->
                    val faceId = faceIdPath(request)
                    photoFaceAssignmentRepository.deleteByFaceId(faceId)
                    faceRepository.delete(faceId)
                    Response(Status.NO_CONTENT)
                },
        )
}

data class SessionsViewModel(val sessions: List<SessionViewModel>) : org.http4k.template.ViewModel {
    override fun template() = "io/github/rygel/outerstellar/facegallery/SessionsPage"
}

data class SessionDetailViewModel(
    val session: SessionViewModel,
    val photos: List<io.github.rygel.outerstellar.facegallery.api.dto.Photo>,
    val progress: io.github.rygel.outerstellar.facegallery.api.dto.JobProgress?,
) : org.http4k.template.ViewModel {
    override fun template() = "io/github/rygel/outerstellar/facegallery/SessionDetailPage"
}

data class SessionViewModel(
    val id: UUID,
    val name: String,
    val status: io.github.rygel.outerstellar.facegallery.api.dto.SessionStatus,
    val createdAt: String,
)

fun io.github.rygel.outerstellar.facegallery.api.dto.Session.toViewModel() =
    SessionViewModel(id = id, name = name, status = status, createdAt = createdAt.toString())
