package io.github.rygel.outerstellar.platform.security

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 128

fun validatePassword(password: String): String? {
    val trimmed = password.trim()
    val error =
        when {
            trimmed.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
            trimmed.length > MAX_PASSWORD_LENGTH -> "Password must be at most $MAX_PASSWORD_LENGTH characters"
            trimmed.none { it.isUpperCase() } -> "Password must contain at least one uppercase letter"
            trimmed.none { it.isLowerCase() } -> "Password must contain at least one lowercase letter"
            trimmed.none { it.isDigit() } -> "Password must contain at least one digit"
            trimmed.none { !it.isLetterOrDigit() } -> "Password must contain at least one special character"
            else -> null
        }
    return error
}
