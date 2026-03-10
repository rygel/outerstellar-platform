package dev.outerstellar.starter.web

import org.http4k.core.Request

/**
 * Helper to get the context safely from a request.
 * It expects that stateFilter has already injected the context into the RequestContext.
 */
val Request.webContext: WebContext get() = WebContext.KEY(this)
