package io.github.rygel.stellar.web

import io.github.rygel.stellar.model.StellarException
import java.util.UUID
import org.http4k.core.Response
import org.http4k.core.Status

fun String.toUUIDOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

inline fun handleNotFound(block: () -> Response): Response =
    try {
        block()
    } catch (e: StellarException) {
        Response(Status.NOT_FOUND).body(e.message ?: "Not found")
    }
