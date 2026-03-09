package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxRepository
import org.http4k.contract.ContractRoute
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.template.TemplateRenderer

class DevDashboardRoutes(
    private val outboxRepository: OutboxRepository,
    private val cache: MessageCache,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val devDashboardEnabled: Boolean
) : ServerRoutes {
    private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()

    override val routes = if (!devDashboardEnabled) emptyList() else listOf(
        "/admin/dev" meta {
            summary = "Developer Dashboard"
        } bindContract GET to { request ->
            val ctx = WebContext(request, devDashboardEnabled)
            val metrics = Metrics.registry.scrape()
            val cacheStats = cache.getStats()
            val outboxPendingCount = outboxRepository.countByStatus("PENDING")
            val outboxProcessedCount = outboxRepository.countByStatus("PROCESSED")
            val outboxFailedCount = outboxRepository.countByStatus("FAILED")
            val telemetryStatus = Telemetry.openTelemetry.toString()

            val page = pageFactory.buildDevDashboardPage(
                ctx,
                metrics,
                cacheStats,
                outboxPendingCount,
                outboxProcessedCount,
                outboxFailedCount,
                telemetryStatus
            )

            Response(Status.OK)
                .header("content-type", htmlContentType)
                .body(renderer(page))
        }
    )
}
