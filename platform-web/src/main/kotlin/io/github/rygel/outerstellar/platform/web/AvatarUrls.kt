package io.github.rygel.outerstellar.platform.web

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

private const val GRAVATAR_CACHE_MAX_SIZE = 10_000L

private val gravatarCache: Cache<String, String> =
    Caffeine.newBuilder().maximumSize(GRAVATAR_CACHE_MAX_SIZE).expireAfterAccess(1, TimeUnit.HOURS).build()

fun gravatarUrl(email: String, customUrl: String?): String {
    if (!customUrl.isNullOrBlank()) return customUrl
    val normalized = email.trim().lowercase()
    return gravatarCache.get(normalized) {
        // MD5 is mandated by the Gravatar API spec — the avatar URL is defined as the MD5 hash of the
        // lowercased email (https://gravatar.com/site/implement/hash/). This is an identifier, not a
        // security primitive, so the use of MD5 here is intentional and safe.
        val md5 = java.security.MessageDigest.getInstance("MD5") // nosemgrep: kotlin.lang.security.use-of-md5.use-of-md5
        val hash = md5.digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) }
        "https://www.gravatar.com/avatar/$hash?d=identicon&s=80"
    }!!
}
