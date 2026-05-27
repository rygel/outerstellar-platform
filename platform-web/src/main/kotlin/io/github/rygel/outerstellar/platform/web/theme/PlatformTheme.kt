package io.github.rygel.outerstellar.platform.web.theme

import org.http4k.core.Request

interface PlatformTheme {
    val id: String

    fun templateOverrides(): Set<String> = emptySet()

    fun headInjections(request: Request): List<String> = emptyList()

    fun bodyInjections(request: Request): List<String> = emptyList()
}
