package dev.outerstellar.starter.web

import dev.outerstellar.starter.infra.render
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.contract.div
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import java.util.UUID

class AuthRoutes(
    private val pageFactory: WebPageFactory,
    private val renderer: TemplateRenderer,
    private val securityService: SecurityService,
    private val userRepository: UserRepository,
    private val passwordEncoder: dev.outerstellar.starter.security.PasswordEncoder
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
            val ctx = request.webContext
            val i18n = ctx.i18n
            val parameters = request.form().toParametersMap()
            val mode = parameters.getFirst("mode") ?: "sign-in"
            val returnTo = request.query("returnTo") ?: "/"
            
            when (mode) {
                "sign-in" -> {
                    val username = parameters.getFirst("email") ?: ""
                    val password = parameters.getFirst("password") ?: ""
                    val user = securityService.authenticate(username, password)
                    
                    if (user != null) {
                        val result = AuthResultFragment(
                            title = i18n.translate("web.auth.result.success.title"),
                            message = "Welcome back, ${user.username}! Redirecting...",
                            toneClass = "panel-success"
                        )
                        Response(Status.OK)
                            .header("content-type", "text/html; charset=utf-8")
                            .header("HX-Redirect", returnTo) // Redirect HTMX to target
                            .cookie(Cookie("app_session", user.id.toString(), path = "/", httpOnly = true))
                            .body(renderer(result))
                    } else {
                        val result = AuthResultFragment(
                            title = i18n.translate("web.auth.result.error.title"),
                            message = "Invalid username or password.",
                            toneClass = "panel-danger"
                        )
                        renderer.render(result, Status.UNAUTHORIZED)
                    }
                }
                "register" -> {
                    val username = parameters.getFirst("name") ?: ""
                    val email = parameters.getFirst("email") ?: ""
                    val password = parameters.getFirst("password") ?: ""
                    val confirm = parameters.getFirst("confirmPassword") ?: ""
                    
                    val errors = mutableListOf<String>()
                    if (username.isBlank()) errors += "Name is required."
                    if (email.isBlank()) errors += "Email is required."
                    if (password.length < 8) errors += "Password must be at least 8 characters."
                    if (password != confirm) errors += "Passwords do not match."
                    if (userRepository.findByUsername(username) != null) errors += "Username already taken."
                    
                    if (errors.isEmpty()) {
                        val newUser = User(
                            id = UUID.randomUUID(),
                            username = username,
                            email = email,
                            passwordHash = passwordEncoder.encode(password),
                            role = UserRole.USER
                        )
                        userRepository.save(newUser)
                        
                        val result = AuthResultFragment(
                            title = "Account Created",
                            message = "Registration successful! You can now sign in.",
                            toneClass = "panel-success"
                        )
                        renderer.render(result)
                    } else {
                        val result = AuthResultFragment(
                            title = "Registration Failed",
                            message = errors.joinToString(" "),
                            toneClass = "panel-danger"
                        )
                        renderer.render(result, Status.BAD_REQUEST)
                    }
                }
                else -> {
                    renderer.render(
                        pageFactory.buildAuthResult(
                            ctx,
                            mapOf(
                                "mode" to mode,
                                "email" to parameters.getFirst("email"),
                                "password" to parameters.getFirst("password"),
                                "confirmPassword" to parameters.getFirst("confirmPassword"),
                            ),
                        )
                    )
                }
            }
        }
    )
}
