package dev.outerstellar.starter

import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.security.PasswordEncoder
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
import dev.outerstellar.starter.web.WebContext
import dev.outerstellar.starter.web.WebPageFactory
import dev.outerstellar.starter.web.webContext
import org.http4k.contract.bindContract
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.core.with
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.routing.websockets
import org.http4k.server.PolyHandler
import org.http4k.template.TemplateRenderer
import org.slf4j.LoggerFactory
import java.util.UUID
import org.http4k.routing.websocket.bind as wsBind

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.App")

@Suppress("LongParameterList", "LongMethod", "TooGenericExceptionCaught", "SwallowedException")
fun app(
    messageService: MessageService,
    contactService: dev.outerstellar.starter.service.ContactService,
    repository: MessageRepository,
    outboxRepository: OutboxRepository,
    cache: MessageCache,
    jteRenderer: TemplateRenderer,
    pageFactory: WebPageFactory,
    config: AppConfig,
    securityService: SecurityService,
    userRepository: UserRepository,
    passwordEncoder: PasswordEncoder
): org.http4k.server.PolyHandler {
    logger.info("Initializing Outerstellar application")

    // 1. Data/Sync API (JSON)
    val apiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Sync API", "v1.0"), Jackson)
        descriptionPath = "/api/openapi.json"
        routes += AuthApi(securityService).routes

        // Apply Bearer Auth to Sync routes via security filter
        val syncAuthFilter = Filter { next ->
            {
                    req ->
                val token = req.header("Authorization")?.removePrefix("Bearer ")
                val user = token?.let {
                    try {
                        userRepository.findByUsername("admin")?.takeIf { it.id == UUID.fromString(token) }
                    } catch (e: Exception) { null }
                }
                if (user != null) {
                    next(req.with(SecurityRules.USER_KEY of user))
                } else {
                    Response(Status.UNAUTHORIZED).body("API token required")
                }
            }
        }

        // Create a sub-contract for sync routes to apply security
        val syncContract = contract {
            renderer = OpenApi3(ApiInfo("Sync", "v1.0"), Jackson)
            security = object : org.http4k.contract.security.Security {
                override val filter = syncAuthFilter
            }
            routes += SyncApi(messageService, contactService).routes
        }

        // SyncContract is a RoutingHttpHandler, but we bind it separately below
        routes += emptyList<org.http4k.contract.ContractRoute>()
    }

    // 2. Main UI (Full HTML Pages)
    val uiRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar UI", "v1.0"), Jackson)
        descriptionPath = "/ui/openapi.json"
        routes += HomeRoutes(messageService, repository, pageFactory, jteRenderer).routes
        routes += ContactsRoutes(pageFactory, jteRenderer).routes
        routes += AuthRoutes(
            pageFactory,
            jteRenderer,
            securityService,
            userRepository,
            passwordEncoder,
            config.sessionCookieSecure,
        ).routes
        routes += ErrorRoutes(pageFactory, jteRenderer).routes

        // Global Logout
        routes += ("/logout" bindContract GET).to { request: org.http4k.core.Request ->
            Response(Status.FOUND)
                .header("location", request.webContext.url("/"))
                .header("Set-Cookie", dev.outerstellar.starter.web.SessionCookie.clear(config.sessionCookieSecure))
        }
    }

    // 3. Protected Admin Routes
    val adminContract = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Admin", "v1.0"), Jackson)
        descriptionPath = "/admin/openapi.json"
        security = object : org.http4k.contract.security.Security {
            override val filter = Filter { next ->
                SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
            }
        }
        routes += DevDashboardRoutes(
            outboxRepository,
            cache,
            pageFactory,
            jteRenderer,
            config.devDashboardEnabled,
        ).routes
    }

    // Define Sync sub-contract separately to apply security
    val bearerAuthFilter = Filter { next ->
        {
                req ->
            val token = req.header("Authorization")?.removePrefix("Bearer ")
            val user = token?.let {
                try {
                    userRepository.findByUsername("admin")?.takeIf { it.id == UUID.fromString(token) }
                } catch (e: Exception) { null }
            }
            if (user != null) {
                next(req.with(SecurityRules.USER_KEY of user))
            } else {
                Response(Status.UNAUTHORIZED).body("API token required")
            }
        }
    }

    val syncContract = contract {
        renderer = OpenApi3(ApiInfo("Sync", "v1.0"), Jackson)
        descriptionPath = "/api/v1/sync/openapi.json"
        security = object : org.http4k.contract.security.Security {
            override val filter = bearerAuthFilter
        }
        routes += SyncApi(messageService, contactService).routes
    }

    // 4. HTMX Components (HTML Fragments)
    val componentRoutes = contract {
        renderer = OpenApi3(ApiInfo("Outerstellar Components", "v1.0"), Jackson)
        descriptionPath = "/components/openapi.json"
        routes += ComponentRoutes(pageFactory, jteRenderer).routes
    }

    // Filtered admin handler
    val filteredAdminHandler = Filter { next ->
        SecurityRules.authenticated(SecurityRules.hasRole(UserRole.ADMIN, next))
    }
        .then(adminContract)

    val baseApp: HttpHandler = routes(
        static(ResourceLoader.Classpath("static")),
        apiRoutes,
        syncContract,
        uiRoutes,
        componentRoutes,
        "/" bind filteredAdminHandler,
        "/health" bind GET to { Response(Status.OK).body("ok") },
        "/metrics" bind GET to { Response(Status.OK).body(dev.outerstellar.starter.web.Metrics.registry.scrape()) }
    )

    val httpHandler = Filters.telemetry
        .then(ServerFilters.InitialiseRequestContext(WebContext.contexts))
        .then(Filters.stateFilter(config.devDashboardEnabled, userRepository))
        .then(Filters.securityFilter)
        .then(Filters.requestLogging)
        .then(Filters.serverMetrics)
        .then(Filters.globalErrorHandler(pageFactory, jteRenderer))
        .then(baseApp)

    val wsHandler = websockets(
        "/ws/sync" wsBind SyncWebSocket.handler
    )

    return org.http4k.server.PolyHandler(httpHandler, wsHandler)
}
