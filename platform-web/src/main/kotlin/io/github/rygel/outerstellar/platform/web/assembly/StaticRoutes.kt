package io.github.rygel.outerstellar.platform.web.assembly

import io.github.rygel.outerstellar.platform.persistence.UserRepository
import java.time.Instant
import java.time.LocalDate
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.format.KotlinxSerialization

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
    fun buildHealthResponse(userRepository: UserRepository): Response {
        val checks = mutableMapOf<String, Any>("status" to "UP")
        try {
            userRepository.countAll()
            checks["database"] = mapOf("status" to "UP")
        } catch (_: Exception) {
            checks["status"] = "DOWN"
            checks["database"] = mapOf("status" to "DOWN", "error" to "Database connection failed")
        }
        checks["timestamp"] = Instant.now().toString()
        val status = if (checks["status"] == "UP") Status.OK else Status.SERVICE_UNAVAILABLE
        return Response(status)
            .header("content-type", "application/json; charset=utf-8")
            .body(KotlinxSerialization.asJsonObject(checks).toString())
    }
}

private fun String.isLocalhostHost(): Boolean =
    startsWith("localhost") || startsWith("127.0.0.1") || startsWith("[::1]")
