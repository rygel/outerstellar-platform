package io.github.rygel.outerstellar.platform.composition

data class RegisteredRoute(
    val httpRoute: Any?,
    val owner: RouteOwner,
    val group: RouteGroup,
    val pathPattern: String,
    val method: String,
    val description: String,
)
