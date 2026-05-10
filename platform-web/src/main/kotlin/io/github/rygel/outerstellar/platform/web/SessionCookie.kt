package io.github.rygel.outerstellar.platform.web

internal object SessionCookie {
    fun create(value: String, secure: Boolean, maxAgeSeconds: Long? = null): String {
        val securePart = if (secure) "; Secure" else ""
        val maxAgePart = maxAgeSeconds?.let { "; Max-Age=$it" } ?: ""
        return "${WebContext.SESSION_COOKIE}=$value; Path=/; HttpOnly; SameSite=Strict$securePart$maxAgePart"
    }

    fun clear(secure: Boolean): String {
        val securePart = if (secure) "; Secure" else ""
        return "${WebContext.SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict$securePart"
    }
}
