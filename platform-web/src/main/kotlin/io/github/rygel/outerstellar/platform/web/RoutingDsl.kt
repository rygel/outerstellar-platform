package io.github.rygel.outerstellar.platform.web

import org.http4k.core.Request

val Request.requestContext: RequestContext
    get() = RequestContext.KEY(this)

val Request.shellRenderer: ShellRenderer
    get() = ShellRenderer.KEY(this)
