package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then

class MaxBodySizeFilterTest {
    // A trivial downstream handler that echoes the declared content length, proving the request reached it.
    private val downstream = { req: Request -> Response(Status.OK).body(req.header("Content-Length") ?: "0") }

    @Test
    fun `passes requests with no Content-Length through unchanged`() {
        val app = Filters.maxBodySize(100L).then(downstream)
        val resp = app(Request(POST, "/auth"))
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `buffers an allowed streamed body and preserves it for the handler`() {
        val app = Filters.maxBodySize(100L).then { request -> Response(Status.OK).body(request.bodyString()) }
        val resp = app(Request(POST, "/auth").header("Transfer-Encoding", "chunked").body("streamed-body"))
        assertEquals(Status.OK, resp.status)
        assertEquals("streamed-body", resp.bodyString())
    }

    @Test
    fun `rejects oversized chunked body without Content-Length`() {
        val app = Filters.maxBodySize(100L).then { Response(Status.OK).body("downstream-called") }
        val resp = app(Request(POST, "/auth").header("Transfer-Encoding", "chunked").body("x".repeat(101)))
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE, resp.status)
        assert(resp.bodyString().contains("exceeds the limit"))
    }

    @Test
    fun `passes requests at or under the limit`() {
        val app = Filters.maxBodySize(100L).then(downstream)
        val resp = app(Request(POST, "/auth").header("Content-Length", "100").body("x".repeat(100)))
        assertEquals(Status.OK, resp.status)
    }

    @Test
    fun `rejects oversized requests with 413 before reaching the handler`() {
        val app = Filters.maxBodySize(100L).then(downstream)
        val resp = app(Request(POST, "/auth").header("Content-Length", "101").body("x".repeat(101)))
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE, resp.status)
        assert(resp.bodyString().contains("exceeds the limit")) { "Expected limit message, got: ${resp.body}" }
    }

    @Test
    fun `rejects vastly oversized requests without allocating the body`() {
        // The DoS scenario from issue #516: a 500 MiB declared body. The filter must short-circuit on the
        // Content-Length header alone — note we declare 500 MiB but send a tiny body, simulating a hostile
        // client that lies about length. The 413 is returned without the handler ever touching the body.
        val fiveHundredMiB = 500L * 1024 * 1024
        val app = Filters.maxBodySize(2L * 1024 * 1024).then(downstream)
        val resp = app(Request(POST, "/auth").header("Content-Length", fiveHundredMiB.toString()).body("tiny"))
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE, resp.status)
    }

    @Test
    fun `rejects a malformed Content-Length header`() {
        val app = Filters.maxBodySize(100L).then(downstream)
        val resp = app(Request(POST, "/auth").header("Content-Length", "not-a-number").body("ok"))
        assertEquals(Status.BAD_REQUEST, resp.status)
        assert(resp.bodyString().contains("Invalid Content-Length"))
    }

    @Test
    fun `rejects a body larger than a smaller declared Content-Length`() {
        val app = Filters.maxBodySize(100L).then(downstream)
        val resp = app(Request(POST, "/auth").header("Content-Length", "1").body("x".repeat(101)))
        assertEquals(Status.REQUEST_ENTITY_TOO_LARGE, resp.status)
    }

    @Test
    fun `rejects body limits that cannot be safely buffered`() {
        val error = assertFailsWith<IllegalArgumentException> { Filters.maxBodySize(Int.MAX_VALUE.toLong()) }
        assert(error.message?.contains("MAX_REQUEST_BODY_BYTES") == true)
    }
}
