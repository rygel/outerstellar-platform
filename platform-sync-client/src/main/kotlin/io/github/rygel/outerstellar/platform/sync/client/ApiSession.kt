package io.github.rygel.outerstellar.platform.sync.client

class ApiSession {
    @Volatile
    var apiToken: String? = null
        internal set

    @Volatile
    var userRole: String? = null
        internal set

    fun clear() {
        apiToken = null
        userRole = null
    }
}
