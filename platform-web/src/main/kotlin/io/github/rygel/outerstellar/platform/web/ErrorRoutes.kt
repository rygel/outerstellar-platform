package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.render
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class ErrorRoutes(private val errorPageFactory: ErrorPageFactory, private val renderer: TemplateRenderer) :
    ServerRoutes {
    private val kindPath = Path.string().of("kind")

    override val routes =
        listOf(
            "/errors" / kindPath meta
                {
                    summary = "Themed error page"
                } bindContract
                GET to
                { kind ->
                    { request: org.http4k.core.Request ->
                        if (!ErrorPageFactory.isSupportedKind(kind)) {
                            Response(Status.BAD_REQUEST).body("Unknown error page kind: $kind")
                        } else {
                            renderer.render(errorPageFactory.buildErrorPage(request.shellRenderer, kind))
                        }
                    }
                },
            "/errors/components/help" / kindPath meta
                {
                    summary = "Error help component"
                } bindContract
                GET to
                { kind ->
                    { request: org.http4k.core.Request ->
                        if (!ErrorPageFactory.isSupportedKind(kind)) {
                            Response(Status.BAD_REQUEST).body("Unknown error help kind: $kind")
                        } else {
                            val help = errorPageFactory.buildErrorHelp(request.shellRenderer, kind)
                            renderer.render(help)
                        }
                    }
                },
        )
}
