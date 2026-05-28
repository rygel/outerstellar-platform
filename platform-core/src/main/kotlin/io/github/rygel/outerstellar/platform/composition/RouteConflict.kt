package io.github.rygel.outerstellar.platform.composition

data class RouteConflict(
    val pathPattern: String,
    val method: String,
    val existing: RouteOwner,
    val challenger: RouteOwner,
    val existingRoute: RegisteredRoute? = null,
    val challengerRoute: RegisteredRoute? = null,
) {
    constructor(
        existingRoute: RegisteredRoute,
        challengerRoute: RegisteredRoute,
    ) : this(
        pathPattern = challengerRoute.pathPattern,
        method = challengerRoute.method,
        existing = existingRoute.owner,
        challenger = challengerRoute.owner,
        existingRoute = existingRoute,
        challengerRoute = challengerRoute,
    )
}
