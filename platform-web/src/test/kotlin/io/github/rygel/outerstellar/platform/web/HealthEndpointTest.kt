package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import kotlin.test.Test
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus

class HealthEndpointTest : WebTest() {
    // Health endpoints are localhost-only; tests run on loopback so they pass the filter.

    @Test
    fun `health live returns 200 with status UP`() {
        val app = buildApp()
        val response = app(Request(GET, "/health/live"))
        assertThat(response, hasStatus(Status.OK))
        assertThat(response.bodyString(), containsSubstring("\"status\":\"UP\""))
    }

    @Test
    fun `health live does not probe the database`() {
        // Liveness must not depend on the DB (a transient DB blip must not cause a restart loop).
        val app = buildApp()
        val response = app(Request(GET, "/health/live"))
        assertThat(response, hasStatus(Status.OK))
        assert(!response.bodyString().contains("database")) { "Liveness must not probe DB: ${response.body}" }
    }

    @Test
    fun `health ready returns 200 and reports database status when DB is up`() {
        val app = buildApp()
        val response = app(Request(GET, "/health/ready"))
        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("database"))
        assertThat(body, containsSubstring("\"status\":\"UP\""))
    }

    @Test
    fun `legacy health endpoint still works as readiness alias`() {
        val app = buildApp()
        val response = app(Request(GET, "/health"))
        // Backward compat: /health behaves as readiness (probes DB).
        assertThat(response, hasStatus(Status.OK))
        assertThat(response.bodyString(), containsSubstring("database"))
    }
}
