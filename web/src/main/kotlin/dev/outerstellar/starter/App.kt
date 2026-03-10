package dev.outerstellar.starter

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.*
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.App")

fun app(
    messageService: MessageService,
    repository: MessageRepository,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    i18nService: I18nService
): HttpHandler {
  logger.info("Initializing Outerstellar application")

  // 1. Data/Sync API (JSON)
  val apiRoutes = contract {
    renderer = OpenApi3(ApiInfo("Outerstellar Sync API", "v1.0"), Jackson)
    descriptionPath = "/api/openapi.json"
    routes += SyncApi(messageService).routes
  }

  // 2. Main UI (Full HTML Pages)
  val uiRoutes = contract {
    renderer = OpenApi3(ApiInfo("Outerstellar UI", "v1.0"), Jackson)
    descriptionPath = "/ui/openapi.json"
    routes += HomeRoutes(messageService, repository, pageFactory, jteRenderer, i18nService).routes
    routes += AuthRoutes(pageFactory, jteRenderer).routes
    routes += ErrorRoutes(pageFactory, jteRenderer).routes
    routes += DevDashboardRoutes(outboxRepository, cache, pageFactory, jteRenderer, config.devDashboardEnabled).routes
  }

  // 3. HTMX Components (HTML Fragments)
  val componentRoutes = contract {
    renderer = OpenApi3(ApiInfo("Outerstellar Components", "v1.0"), Jackson)
    descriptionPath = "/components/openapi.json"
    routes += ComponentRoutes(pageFactory, jteRenderer).routes
  }

  val baseApp: HttpHandler = routes(
    static(ResourceLoader.Classpath("static")),
    apiRoutes,
    // Inject WebContext into UI and Component routes
    ServerFilters.InitialiseRequestContext(WebContext.contexts)
        .then(Filters.stateFilter(config.devDashboardEnabled))
        .then(routes(uiRoutes, componentRoutes)),
    "/health" bind GET to { Response(Status.OK).body("ok") },
    "/metrics" bind GET to { Response(Status.OK).body(dev.outerstellar.starter.web.Metrics.registry.scrape()) }
  )

  return Filters.telemetry
    .then(Filters.requestLogging)
    .then(Filters.serverMetrics)
    .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
    .then(baseApp)
}
