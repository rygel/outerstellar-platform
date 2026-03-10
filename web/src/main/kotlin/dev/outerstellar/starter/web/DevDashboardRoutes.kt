package dev.outerstellar.starter.web

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxRepository
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.template.TemplateRenderer

class DevDashboardRoutes(
    private val outboxRepository: OutboxRepository,
    private val cache: MessageCache,
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val enabled: Boolean
) : ServerRoutes {

    override val routes = if (enabled) {
        listOf(
            "/admin/dev" meta {
                summary = "Developer dashboard"
            } bindContract GET to { request: org.http4k.core.Request ->
                val outboxStats = outboxRepository.getStats()
                val stats = OutboxStatsViewModel(
                    pending = outboxStats["PENDING"] ?: 0,
                    processed = outboxStats["PROCESSED"] ?: 0,
                    failed = outboxStats["FAILED"] ?: 0
                )
                
                val view = pageFactory.buildDevDashboardPage(
                    ctx = request.webContext,
                    metrics = "Active connections and cache performance",
                    cacheStats = cache.getStats(),
                    outboxStats = stats,
                    telemetryStatus = "Enabled"
                )
                renderer.render(view)
            }
        )
    } else {
        emptyList()
    }
}
