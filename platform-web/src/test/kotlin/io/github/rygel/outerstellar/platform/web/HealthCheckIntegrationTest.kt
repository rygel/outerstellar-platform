package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import io.github.rygel.outerstellar.platform.extension.ExtensionContributionContext
import io.github.rygel.outerstellar.platform.extension.PlatformExtension
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus

class HealthCheckIntegrationTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `GET health returns 200`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET health returns JSON content-type`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasContentType("application/json"))
    }

    @Test
    fun `GET health contains status UP`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, bodyContains("\"status\""))
        assertThat(response, bodyContains("UP"))
    }

    @Test
    fun `GET health contains database object`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, bodyContains("database"))
    }

    @Test
    fun `GET health database status is UP`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, bodyContains("database"))
        val body = response.bodyString()
        val databaseIdx = body.indexOf("database")
        val dbSection = body.substring(databaseIdx)
        assertThat(dbSection, containsSubstring("UP"))
    }

    @Test
    fun `GET health contains timestamp field`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, bodyContains("timestamp"))
    }

    @Test
    fun `GET health does not require authentication`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET health does not expose internal details`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasBody(!containsSubstring("jdbc:")))
        assertThat(response, hasBody(!containsSubstring("users")))
        assertThat(response, hasBody(!containsSubstring("Exception")))
    }

    @Test
    fun `GET health exposes optional extension readiness without failing`() {
        val response = buildApp(extension = readinessExtension(requiredDown = false))(Request(GET, "/health"))

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, bodyContains("extensions"))
        assertThat(response, bodyContains("preview-cache"))
        assertThat(response, bodyContains("Preview cache is disabled"))
    }

    @Test
    fun `GET health fails when required extension readiness is down`() {
        val response = buildApp(extension = readinessExtension(requiredDown = true))(Request(GET, "/health"))

        assertThat(response, hasStatus(Status.SERVICE_UNAVAILABLE))
        assertThat(response, bodyContains("\"status\":\"DOWN\""))
        assertThat(response, bodyContains("content-dir"))
        assertThat(response, bodyContains("Set CONTENT_DIR to an existing directory"))
    }

    private fun readinessExtension(requiredDown: Boolean): PlatformExtension =
        object : PlatformExtension {
            override val id = "reports"
            override val appLabel = "Reports"

            override fun contribute(context: ExtensionContributionContext) {
                if (requiredDown) {
                    context.readiness.down("content-dir", "Set CONTENT_DIR to an existing directory")
                } else {
                    context.readiness.warn("preview-cache", "Preview cache is disabled")
                }
            }
        }
}
