package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
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
    private val enabled: Boolean,
) : ServerRoutes {
    override val routes =
        if (!enabled) {
            emptyList()
        } else {
            listOf(
                "/admin/dev" meta
                    {
                        summary = "Developer Dashboard"
                    } bindContract
                    GET to
                    { request: org.http4k.core.Request ->
                        val outboxStats = outboxRepository.getStats()
                        val viewModel =
                            pageFactory.buildDevDashboardPage(
                                ctx = request.webContext,
                                metrics = "Prometheus export would go here",
                                cacheStats = cache.getStats(),
                                outboxStats =
                                    OutboxStatsViewModel(
                                        pending = outboxStats["PENDING"] ?: 0,
                                        processed = outboxStats["PROCESSED"] ?: 0,
                                        failed = outboxStats["FAILED"] ?: 0,
                                    ),
                                telemetryStatus = "Enabled",
                            )
                        renderer.render(viewModel)
                    }
            )
        }
}
