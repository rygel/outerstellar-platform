package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncApi
import dev.outerstellar.starter.web.WebPageFactory
import org.http4k.core.ContentType
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.template.TemplateRenderer

private const val defaultAuthor = "Server"
private const val contentRequiredMessage = "Content is required."
private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()

fun app(messageService: MessageService, repository: MessageRepository, renderer: TemplateRenderer): HttpHandler {
  val syncApi = SyncApi(messageService)
  val pageFactory = WebPageFactory(repository)

  return routes(
    static(ResourceLoader.Classpath("static")),
    syncApi.routes,
    homeRoutes(messageService, pageFactory, renderer),
    authRoutes(pageFactory, renderer),
    errorRoutes(pageFactory, renderer),
    footerRoutes(pageFactory, renderer),
    sidebarRoutes(pageFactory, renderer),
    "/health" bind GET to { Response(Status.OK).body("ok") },
  )
}

private fun homeRoutes(
  messageService: MessageService,
  pageFactory: WebPageFactory,
  renderer: TemplateRenderer,
) =
  routes(
    "/" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildHomePage(request)))
      },
    "/messages" bind
      POST to
      { request ->
        val parameters = request.form().toParametersMap()
        val author =
          parameters.getFirst("author").takeUnless { it.isNullOrBlank() } ?: defaultAuthor
        val content = parameters.getFirst("content")?.trim().orEmpty()

        if (content.isBlank()) {
          Response(Status.BAD_REQUEST).body(contentRequiredMessage)
        } else {
          messageService.createServerMessage(author, content)
          val location =
            pageFactory.url("/", pageFactory.langTag(request), pageFactory.themeId(request), pageFactory.layoutId(request))
          Response(Status.FOUND).header("location", location)
        }
      },
  )

private fun authRoutes(pageFactory: WebPageFactory, renderer: TemplateRenderer) =
  routes(
    "/auth" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildAuthPage(request)))
      },
    "/auth/components/forms/{mode}" bind
      GET to
      { request ->
        htmlResponse(
          Status.OK,
          renderer(pageFactory.buildAuthForm(request, request.uri.path.substringAfterLast("/"))),
        )
      },
    "/auth/components/result" bind
      POST to
      { request ->
        val parameters = request.form().toParametersMap()
        htmlResponse(
          Status.OK,
          renderer(
            pageFactory.buildAuthResult(
              request,
              mapOf(
                "mode" to parameters.getFirst("mode"),
                "email" to parameters.getFirst("email"),
                "password" to parameters.getFirst("password"),
                "confirmPassword" to parameters.getFirst("confirmPassword"),
              ),
            )
          ),
        )
      },
  )

private fun errorRoutes(pageFactory: WebPageFactory, renderer: TemplateRenderer) =
  routes(
    "/errors/not-found" bind
      GET to
      { request ->
        htmlResponse(Status.NOT_FOUND, renderer(pageFactory.buildErrorPage(request, "not-found")))
      },
    "/errors/server-error" bind
      GET to
      { request ->
        htmlResponse(
          Status.INTERNAL_SERVER_ERROR,
          renderer(pageFactory.buildErrorPage(request, "server-error")),
        )
      },
    "/errors/components/help/{kind}" bind
      GET to
      { request ->
        htmlResponse(
          Status.OK,
          renderer(pageFactory.buildErrorHelp(request, request.uri.path.substringAfterLast("/"))),
        )
      },
  )

private fun footerRoutes(pageFactory: WebPageFactory, renderer: TemplateRenderer) =
  routes(
    "/components/footer-status" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildFooterStatus(request)))
      }
  )

private fun sidebarRoutes(pageFactory: WebPageFactory, renderer: TemplateRenderer) =
  routes(
    "/components/navigation/page" bind
      GET to
      { request ->
        val pagePath = request.query("pagePath").orEmpty()
        val lang = request.query("lang") ?: pageFactory.langTag(request)
        val theme = request.query("theme") ?: pageFactory.themeId(request)
        val layout = request.query("layout") ?: pageFactory.layoutId(request)
        val location = pageFactory.url(pagePath, lang, theme, layout)
        
        if (request.header("HX-Request") == "true") {
          Response(Status.OK).header("HX-Redirect", location)
        } else {
          Response(Status.FOUND).header("location", location)
        }
      },
    "/components/sidebar/theme-selector" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildThemeSelector(request)))
      },
    "/components/sidebar/language-selector" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildLanguageSelector(request)))
      },
    "/components/sidebar/layout-selector" bind
      GET to
      { request ->
        htmlResponse(Status.OK, renderer(pageFactory.buildLayoutSelector(request)))
      },
  )

private fun htmlResponse(status: Status, body: String): Response =
  Response(status).header("content-type", htmlContentType).body(body)
