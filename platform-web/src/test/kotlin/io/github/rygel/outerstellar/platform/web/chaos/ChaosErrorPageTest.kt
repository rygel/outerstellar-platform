package io.github.rygel.outerstellar.platform.web.chaos

import io.github.rygel.outerstellar.platform.web.WebTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.http4k.chaos.ChaosBehaviours
import org.http4k.chaos.ChaosEngine
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.then

class ChaosErrorPageTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `chaos engine injects 500 when enabled`() {
        val behaviour = ChaosBehaviours.ReturnStatus(Status.INTERNAL_SERVER_ERROR)
        val engine = ChaosEngine()
        engine.enable(behaviour)
        val chaoticApp = engine.then(app)

        val response = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
    }

    @Test
    fun `chaos engine with no active behaviour passes through`() {
        val chaoticApp = ChaosEngine().then(app)

        val response = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `disabled chaos engine passes through then injects when enabled`() {
        val behaviour = ChaosBehaviours.ReturnStatus(Status.INTERNAL_SERVER_ERROR)
        val engine = ChaosEngine()
        val chaoticApp = engine.then(app)

        val normalResponse = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.OK, normalResponse.status)

        engine.enable(behaviour)

        val chaosResponse = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.INTERNAL_SERVER_ERROR, chaosResponse.status)
    }

    @Test
    fun `chaos engine can be disabled after enabling`() {
        val behaviour = ChaosBehaviours.ReturnStatus(Status.INTERNAL_SERVER_ERROR)
        val engine = ChaosEngine()
        engine.enable(behaviour)
        val chaoticApp = engine.then(app)

        val chaosResponse = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.INTERNAL_SERVER_ERROR, chaosResponse.status)

        engine.disable()

        val normalResponse = chaoticApp(Request(GET, "/health"))
        assertEquals(Status.OK, normalResponse.status)
    }
}
