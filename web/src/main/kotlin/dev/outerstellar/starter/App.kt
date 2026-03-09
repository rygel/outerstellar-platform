package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.MessageRepository
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
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.App")

fun app(messageService: MessageService, repository: MessageRepository, renderer: TemplateRenderer): HttpHandler {
  logger.info("Initializing Outerstellar application")
  val pageFactory = WebPageFactory(repository)

  val serverRoutes = listOf(
    SyncApi(messageService),
    HomeRoutes(messageService, pageFactory, renderer),
    AuthRoutes(pageFactory, renderer),
    ErrorRoutes(pageFactory, renderer),
    ComponentRoutes(pageFactory, renderer)
  )

  val apiContract = contract {
    this.renderer = OpenApi3(ApiInfo("Outerstellar Web API", "v1.0"), Jackson)
    descriptionPath = "/openapi.json"
    routes += serverRoutes.flatMap { it.routes }
  }

  logger.info("Contract routes: {}", apiContract)

  val baseApp: HttpHandler = routes(
    static(ResourceLoader.Classpath("static")),
    apiContract,
    "/health" bind GET to { Response(Status.OK).body("ok") },
    "/metrics" bind GET to { Response(Status.OK).body(dev.outerstellar.starter.web.Metrics.registry.scrape()) }
  )

  return Filters.telemetry
    .then(Filters.requestLogging)
    .then(Filters.serverMetrics)
    .then(Filters.globalErrorHandler(pageFactory, renderer))
    .then(baseApp)
}
