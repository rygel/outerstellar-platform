package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.SecurityRules
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.UserRepository
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.web.AuthApi
import dev.outerstellar.starter.web.AuthRoutes
import dev.outerstellar.starter.web.ComponentRoutes
import dev.outerstellar.starter.web.ContactsRoutes
import dev.outerstellar.starter.web.DevDashboardRoutes
import dev.outerstellar.starter.web.ErrorRoutes
import dev.outerstellar.starter.web.Filters
import dev.outerstellar.starter.web.HomeRoutes
import dev.outerstellar.starter.web.SyncApi
import dev.outerstellar.starter.web.SyncWebSocket
import dev.outerstellar.starter.web.UserAdminApi
import dev.outerstellar.starter.web.UserAdminRoutes
import dev.outerstellar.starter.web.WebPageFactory
import dev.outerstellar.starter.web.webContext
import java.util.UUID
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websocket.bind as wsBind
import org.http4k.routing.websockets
import org.http4k.server.PolyHandler
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.App")

@Suppress(
    "LongParameterList",
    "LongMethod",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "DEPRECATION",
)
fun app(
    messageService: MessageService,
    contactService: dev.outerstellar.starter.service.ContactService,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
): PolyHandler {
    logger.info("Initializing Outerstellar application")

    val bearerAuthFilter = Filter { next ->
        { req ->
            val token = req.header("Authorization")?.removePrefix("Bearer ")
            val user =
                token?.let {
                    try {
                        val id = UUID.fromString(token)
                        userRepository.findById(id)?.takeIf { it.enabled }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        null
                    }
                }
            if (user != null) {
                next(req.with(SecurityRules.USER_KEY of user))
            } else {
                Response(Status.UNAUTHORIZED).body("API token required")
            }
        }
    }

    // 1. Data/Sync API (JSON)
    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Sync API", "v1.0"), Jackson)
        descriptionPath = "/api/openapi.json"
        routes += AuthApi(securityService).routes

        // SyncContract is a RoutingHttpHandler, but we bind it separately below
        routes += emptyList<org.http4k.contract.ContractRoute>()
    }

    // 2. Main UI (Full HTML Pages)
    val uiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar UI", "v1.0"), Jackson)
        descriptionPath = "/ui/openapi.json"
        routes += HomeRoutes(messageService, pageFactory, jteRenderer).routes
        routes += ContactsRoutes(pageFactory, jteRenderer).routes
        routes +=
            AuthRoutes(pageFactory, jteRenderer, securityService, config.sessionCookieSecure).routes
        routes += ErrorRoutes(pageFactory, jteRenderer).routes

        // Global Logout
        routes +=
            ("/logout" bindContract POST).to { request: org.http4k.core.Request ->
                Response(Status.FOUND)
                    .header("location", request.webContext.url("/"))
                    .header(
                        "Set-Cookie",
                        dev.outerstellar.starter.web.SessionCookie.clear(config.sessionCookieSecure),
                    )
            }
    }

    // 3. Protected Admin Routes
    val adminContract = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Admin", "v1.0"), Jackson)
        descriptionPath = "/admin/openapi.json"
        security =
            object : org.http4k.security.Security {
                override val filter = Filter { next ->
                    SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
                }
            }
        routes +=
            DevDashboardRoutes(
                    outboxRepository,
                    cache,
                    pageFactory,
                    jteRenderer,
                    config.devDashboardEnabled,
                )
                .routes
        routes += UserAdminRoutes(pageFactory, jteRenderer, securityService).routes
    }

    val bearerSecurity =
        object : org.http4k.security.Security {
            override val filter = bearerAuthFilter
        }

    val bearerAdminSecurity =
        object : org.http4k.security.Security {
            override val filter = Filter { next ->
                bearerAuthFilter.then(
                    Filter { inner -> SecurityRules.hasRole(UserRole.ADMIN, inner) }
                )(next)
            }
        }

    val syncContract = contract {
        renderer = OpenApi3(ApiInfo("Sync", "v1.0"), Jackson)
        descriptionPath = "/api/v1/sync/openapi.json"
        security = bearerSecurity
        routes += SyncApi(messageService, contactService).routes
        routes += AuthApi(securityService).bearerRoutes
    }

    // Bearer + Admin protected API routes (user admin)
    val bearerAdminApiContract = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Admin API", "v1.0"), Jackson)
        descriptionPath = "/api/v1/admin/api-openapi.json"
        security = bearerAdminSecurity
        routes += UserAdminApi(securityService).routes
    }

    // 4. HTMX Components (HTML Fragments)
    val componentRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Components", "v1.0"), Jackson)
        descriptionPath = "/components/openapi.json"
        routes += ComponentRoutes(pageFactory, jteRenderer).routes
    }

    // Filtered admin handler
    val filteredAdminHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then(adminContract)

    val metricsHandler =
        Filter { next -> SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next)) }
            .then {
                Response(Status.OK).body(dev.outerstellar.starter.web.Metrics.registry.scrape())
            }

    val baseApp: HttpHandler =
        routes(
            static(ResourceLoader.Classpath("static")),
            bearerAdminApiContract,
            apiRoutes,
            syncContract,
            uiRoutes,
            componentRoutes,
            "/" bind filteredAdminHandler,
            "/health" bind GET to { Response(Status.OK).body("ok") },
            "/metrics" bind GET to metricsHandler,
        )

    val httpHandler =
        Filters.telemetry
            .then(Filters.devAutoLogin(config.devMode, userRepository))
            .then(Filters.stateFilter(config.devDashboardEnabled, userRepository))
            .then(
                Filters.sessionTimeout(
                    config.sessionTimeoutMinutes,
                    userRepository,
                    config.sessionCookieSecure,
                )
            )
            .then(Filters.securityFilter)
            .then(Filters.requestLogging)
            .then(Filters.serverMetrics)
            .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
            .then(baseApp)

    val wsHandler = websockets("/ws/sync" wsBind SyncWebSocket.handler)

    return PolyHandler(httpHandler, wsHandler)
}
