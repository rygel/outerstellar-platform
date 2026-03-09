package dev.outerstellar.starter.web

import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.div
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer
) : ServerRoutes {
    private val htmlContentType = ContentType.TEXT_HTML.toHeaderValue()
    private val modePath = Path.string().of("mode")

    override val routes = listOf(
        "/auth" meta {
            summary = "Auth page"
        } bindContract GET to { request ->
            val ctx = WebContext(request)
            htmlResponse(Status.OK, renderer(pageFactory.buildAuthPage(ctx)))
        },
        "/auth/components/forms" / modePath meta {
            summary = "Auth form component"
        } bindContract GET to { mode ->
            { request ->
                val ctx = WebContext(request)
                htmlResponse(Status.OK, renderer(pageFactory.buildAuthForm(ctx, mode)))
            }
        },
        "/auth/components/result" meta {
            summary = "Auth result component"
        } bindContract POST to { request ->
            val ctx = WebContext(request)
            val parameters = request.form().toParametersMap()
            htmlResponse(
                Status.OK,
                renderer(
                    pageFactory.buildAuthResult(
                        ctx,
                        mapOf(
                            "mode" to parameters.getFirst("mode"),
                            "email" to parameters.getFirst("email"),
                            "password" to parameters.getFirst("password"),
                            "confirmPassword" to parameters.getFirst("confirmPassword"),
                        ),
                    )
                ),
            )
        }
    )

    private fun htmlResponse(status: Status, body: String): Response =
        Response(status).header("content-type", htmlContentType).body(body)
}
