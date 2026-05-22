package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
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
}
