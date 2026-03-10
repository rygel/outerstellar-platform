package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
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
    private val modePath = Path.string().of("mode")

    override val routes = listOf(
        "/auth" meta {
            summary = "Auth page"
        } bindContract GET to { request: org.http4k.core.Request ->
            renderer.render(pageFactory.buildAuthPage(request.webContext))
        },
        "/auth/components/forms" / modePath meta {
            summary = "Auth form component"
        } bindContract GET to { mode ->
            { request: org.http4k.core.Request ->
                renderer.render(pageFactory.buildAuthForm(request.webContext, mode))
            }
        },
        "/auth/components/result" meta {
            summary = "Auth result component"
        } bindContract POST to { request: org.http4k.core.Request ->
            val parameters = request.form().toParametersMap()
            renderer.render(
                pageFactory.buildAuthResult(
                    request.webContext,
                    mapOf(
                        "mode" to parameters.getFirst("mode"),
                        "email" to parameters.getFirst("email"),
                        "password" to parameters.getFirst("password"),
                        "confirmPassword" to parameters.getFirst("confirmPassword"),
                    ),
                )
            )
        }
    )
}
