package io.github.rygel.outerstellar.platform.model

sealed class SessionLookup {
    data class Active(val user: User) : SessionLookup()

    data object Expired : SessionLookup()

    data object NotFound : SessionLookup()
}
