package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.composition.RegisteredRoute
import io.github.rygel.outerstellar.platform.composition.RouteRegistry
import io.github.rygel.outerstellar.platform.extension.ExtensionContribution
import io.github.rygel.outerstellar.platform.extension.ExtensionReadinessStatus
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.time.Instant
import java.time.LocalDate
import org.http4k.contract.ContractRoute
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization
import org.http4k.routing.RoutingHttpHandler

internal object StaticRoutes {
    val localhostOnly = Filter { next ->
        { request ->
            val host = request.header("Host")
            if (host == null || host.isLocalhostHost()) {
                next(request)
            } else {
                Response(Status.FORBIDDEN)
            }
        }
    }

    fun buildRobotsTxtResponse(): Response =
        Response(Status.OK)
            .header("content-type", "text/plain; charset=utf-8")
            .body(
                """
                User-agent: *
                Allow: /
                Allow: /contacts
                Allow: /search
                Disallow: /api/
                Disallow: /admin/
                Disallow: /ws/
                Disallow: /auth/
                Disallow: /errors/
                Disallow: /components/
                Disallow: /messages/
                Disallow: /notifications/
                Disallow: /settings/

                Sitemap: /sitemap.xml
                """
                    .trimIndent() + "\n"
            )

    fun buildSitemapResponse(appBaseUrl: String): Response {
        val base = appBaseUrl.ifBlank { "http://localhost:8080" }
        val today = LocalDate.now().toString()
        return Response(Status.OK)
            .header("content-type", "application/xml; charset=utf-8")
            .body(
                """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
    <url>
        <loc>${base}/</loc>
        <lastmod>$today</lastmod>
        <changefreq>weekly</changefreq>
        <priority>1.0</priority>
    </url>
    <url>
        <loc>${base}/auth</loc>
        <lastmod>$today</lastmod>
        <changefreq>monthly</changefreq>
        <priority>0.5</priority>
    </url>
    <url>
        <loc>${base}/search</loc>
        <lastmod>$today</lastmod>
        <changefreq>weekly</changefreq>
        <priority>0.8</priority>
    </url>
</urlset>"""
                    .trimIndent() + "\n"
            )
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun buildHealthResponse(userRepository: UserRepository, extensionContribution: ExtensionContribution): Response {
        val checks = mutableMapOf<String, Any>("status" to "UP")
        try {
            userRepository.countAll()
            checks["database"] = mapOf("status" to "UP")
        } catch (_: Exception) {
            checks["status"] = "DOWN"
            checks["database"] = mapOf("status" to "DOWN", "error" to "Database connection failed")
        }
        val readiness = extensionReadiness(extensionContribution)
        if (readiness.isNotEmpty()) {
            checks["extensions"] = readiness
            if (
                extensionContribution.readinessChecks.any { it.required && it.status == ExtensionReadinessStatus.DOWN }
            ) {
                checks["status"] = "DOWN"
            }
        }
        checks["timestamp"] = Instant.now().toString()
        val status = if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
        return Response(status)
            .header("content-type", "application/json; charset=utf-8")
            .body(KotlinxSerialization.asJsonObject(checks).toString())
    }

    fun buildRouteDiagnostics(registry: RouteRegistry, extensionContribution: ExtensionContribution): Response {
        val payload =
            mapOf(
                "routes" to registry.all().map(::routeDiagnostic),
                "excludedPageSets" to registry.excludedPageSets(),
                "extensionReadiness" to extensionReadiness(extensionContribution),
                "timestamp" to Instant.now().toString(),
            )
        return Response(Status.OK)
            .header("content-type", "application/json; charset=utf-8")
            .body(KotlinxSerialization.asJsonObject(payload).toString())
    }

    private fun routeDiagnostic(route: RegisteredRoute): Map<String, String> =
        mapOf(
            "owner" to route.owner.name,
            "group" to route.group.name,
            "method" to route.method,
            "pathPattern" to route.pathPattern,
            "description" to route.description,
            "handlerKind" to route.handlerKind(),
        )

    private fun RegisteredRoute.handlerKind(): String =
        when (httpRoute) {
            null -> "metadata"
            is ContractRoute -> "contract"
            is RoutingHttpHandler -> "routing"
            else -> httpRoute!!::class.simpleName ?: "unknown"
        }

    private fun extensionReadiness(extensionContribution: ExtensionContribution): List<Map<String, Any>> {
        if (extensionContribution.readinessChecks.isEmpty()) return emptyList()
        return listOf(
            mapOf(
                "extensionId" to (extensionContribution.manifest?.id ?: "platform"),
                "checks" to
                    extensionContribution.readinessChecks.map { check ->
                        mapOf(
                            "name" to check.name,
                            "status" to check.status.name,
                            "message" to check.message,
                            "required" to check.required,
                        )
                    },
            )
        )
    }
}

private fun String.isLocalhostHost(): Boolean =
    startsWith("localhost") || startsWith("127.0.0.1") || startsWith("[::1]")
