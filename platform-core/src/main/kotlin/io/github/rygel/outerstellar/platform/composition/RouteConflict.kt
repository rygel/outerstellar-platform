package io.github.rygel.outerstellar.platform.composition

data class RouteConflict(
    val pathPattern: String,
    val method: String,
    val existing: RouteOwner,
    val challenger: RouteOwner,
)
