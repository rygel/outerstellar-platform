package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import dev.outerstellar.starter.security.SecurityService
import org.http4k.contract.bindContract
import org.http4k.contract.div
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.form
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer

class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val securityService: SecurityService,
    private val sessionCookieSecure: Boolean,
) : ServerRoutes {
    private val modePath = Path.string().of("mode")

    override val routes =
        listOf(
            "/auth" meta
                {
                    summary = "Auth landing page"
                } bindContract
                GET to
                { request: org.http4k.core.Request ->
                    renderer.render(pageFactory.buildAuthPage(request.webContext))
                },
            "/auth/components/forms" / modePath meta
                {
                    summary = "Auth form fragment"
                } bindContract
                GET to
                { mode ->
                    { request: org.http4k.core.Request ->
                        renderer.render(pageFactory.buildAuthForm(request.webContext, mode))
                    }
                },
            "/auth/components/result" meta
                {
                    summary = "Process auth and show result"
                } bindContract
                POST to
                { request: org.http4k.core.Request ->
                    val mode = request.form("mode") ?: "sign-in"
                    val email = request.form("email").orEmpty()
                    val password = request.form("password").orEmpty()
                    val returnTo =
                        safeReturnTo(request.query("returnTo") ?: request.form("returnTo"))

                    if (mode == "sign-in") {
                        val user = securityService.authenticate(email, password)
                        if (user != null) {
                            Response(Status.FOUND)
                                .header("location", request.webContext.url(returnTo))
                                .header(
                                    "Set-Cookie",
                                    SessionCookie.create(user.id.toString(), sessionCookieSecure),
                                )
                        } else {
                            val errorValues = mapOf("error" to "Invalid credentials")
                            renderer.render(
                                pageFactory.buildAuthResult(request.webContext, errorValues)
                            )
                        }
                    } else if (mode == "register") {
                        try {
                            securityService.register(email, password)
                            val target = request.webContext.url("/auth?registered=true")
                            Response(Status.FOUND).header("location", target)
                        } catch (e: IllegalArgumentException) {
                            val errorValues = mapOf("error" to (e.message ?: "Registration failed"))
                            renderer.render(
                                pageFactory.buildAuthResult(request.webContext, errorValues)
                            )
                        }
                    } else {
                        val formValues = request.form().associate { it.first to it.second }
                        renderer.render(pageFactory.buildAuthResult(request.webContext, formValues))
                    }
                },
        )

    private fun safeReturnTo(returnTo: String?): String {
        return when {
            returnTo.isNullOrBlank() -> "/"
            !returnTo.startsWith("/") -> "/"
            returnTo.startsWith("//") -> "/"
            else -> returnTo
        }
    }
}
