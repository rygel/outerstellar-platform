package io.github.rygel.outerstellar.platform.web

import org.http4k.template.ViewModel

interface WebComponent<T : ViewModel> {
    fun build(ctx: RequestContext, shellRenderer: ShellRenderer, vararg args: Any?): T
}
