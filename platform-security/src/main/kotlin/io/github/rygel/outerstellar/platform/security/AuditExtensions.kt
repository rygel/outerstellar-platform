package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.persistence.AuditRepository

fun AuditRepository.logAction(
    action: String,
    actor: User? = null,
    target: User? = null,
    detail: String? = null,
    targetUsername: String? = null,
) {
    log(
        AuditEntry(
            actorId = actor?.id?.toString(),
            actorUsername = actor?.username,
            targetId = target?.id?.toString(),
            targetUsername = targetUsername ?: target?.username,
            action = action,
            detail = detail,
        )
    )
}

fun sanitize(value: String, maxLength: Int = MAX_LOG_ID_LENGTH): String =
    value.take(maxLength).replace('\n', ' ').replace('\r', ' ')

private const val MAX_LOG_ID_LENGTH = 80
