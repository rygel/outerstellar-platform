package dev.outerstellar.starter.web

import org.http4k.template.ViewModel

interface WebComponent<T : ViewModel> {
    fun build(ctx: WebContext, vararg args: Any?): T
}
