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
import org.http4k.core.cookie.cookies
import org.http4k.core.body.form
import org.http4k.core.getFirst
import org.http4k.core.toParametersMap
import org.http4k.lens.Path
import org.http4k.lens.string
import org.http4k.template.TemplateRenderer
import java.util.UUID

private const val REMEMBER_ME_EXPIRY_SECONDS = 31536000L // 365 days
private const val MIN_REG_PASSWORD_LENGTH = 8

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
            handleAuthAction(request)
        }
    )

    private fun handleAuthAction(request: org.http4k.core.Request): Response {
        val ctx = request.webContext
        val i18n = ctx.i18n
        val parameters = request.form().toParametersMap()
        val mode = parameters.getFirst("mode") ?: "sign-in"
        val returnTo = request.query("returnTo") ?: "/"
        
        return when (mode) {
            "sign-in" -> handleSignIn(parameters, returnTo, i18n)
            "register" -> handleRegister(parameters, i18n)
            else -> renderer.render(
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

    private fun handleSignIn(
        params: Map<String, List<String?>>, 
        returnTo: String, 
        i18n: com.outerstellar.i18n.I18nService
    ): Response {
        val username = params.getFirst("email") ?: ""
        val password = params.getFirst("password") ?: ""
        val user = securityService.authenticate(username, password)
        
        return if (user != null) {
            val result = AuthResultFragment(
                title = i18n.translate("web.auth.result.success.title"),
                message = "Welcome back, ${user.username}! Redirecting...",
                toneClass = "panel-success"
            )
            
            val remember = params.getFirst("remember") == "on"
            val maxAge = if (remember) REMEMBER_ME_EXPIRY_SECONDS else null
            
            Response(Status.OK)
                .header("content-type", "text/html; charset=utf-8")
                .header("HX-Redirect", returnTo)
                .cookie(Cookie("app_session", user.id.toString(), path = "/", httpOnly = true, maxAge = maxAge))
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

    private fun handleRegister(
        params: Map<String, List<String?>>, 
        i18n: com.outerstellar.i18n.I18nService
    ): Response {
        val username = params.getFirst("name") ?: ""
        val email = params.getFirst("email") ?: ""
        val password = params.getFirst("password") ?: ""
        val confirm = params.getFirst("confirmPassword") ?: ""
        
        val errors = validateRegistration(username, email, password, confirm)
        
        return if (errors.isEmpty()) {
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

    private fun validateRegistration(u: String, e: String, p: String, c: String): List<String> {
        val errors = mutableListOf<String>()
        if (u.isBlank()) errors += "Name is required."
        if (e.isBlank()) errors += "Email is required."
        if (p.length < MIN_REG_PASSWORD_LENGTH) errors += "Password must be at least 8 characters."
        if (p != c) errors += "Passwords do not match."
        if (userRepository.findByUsername(u) != null) errors += "Username already taken."
        return errors
    }
}
