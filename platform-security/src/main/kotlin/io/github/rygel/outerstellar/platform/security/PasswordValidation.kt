package io.github.rygel.outerstellar.platform.security

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 128

fun validatePassword(password: String): String? {
    val trimmed = password.trim()
    if (trimmed.length < MIN_PASSWORD_LENGTH) {
        return "Password must be at least $MIN_PASSWORD_LENGTH characters"
    }
    if (trimmed.length > MAX_PASSWORD_LENGTH) {
        return "Password must be at most $MAX_PASSWORD_LENGTH characters"
    }
    if (trimmed.none { it.isUpperCase() }) {
        return "Password must contain at least one uppercase letter"
    }
    if (trimmed.none { it.isLowerCase() }) {
        return "Password must contain at least one lowercase letter"
    }
    if (trimmed.none { it.isDigit() }) {
        return "Password must contain at least one digit"
    }
    if (trimmed.none { !it.isLetterOrDigit() }) {
        return "Password must contain at least one special character"
    }
    return null
}
