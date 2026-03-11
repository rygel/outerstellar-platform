package dev.outerstellar.starter.web

internal object SessionCookie {
    fun create(userId: String, secure: Boolean): String {
        val securePart = if (secure) "; Secure" else ""
        return "${WebContext.SESSION_COOKIE}=$userId; Path=/; HttpOnly; SameSite=Lax$securePart"
    }

    fun clear(secure: Boolean): String {
        val securePart = if (secure) "; Secure" else ""
        return "${WebContext.SESSION_COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax$securePart"
    }
}
