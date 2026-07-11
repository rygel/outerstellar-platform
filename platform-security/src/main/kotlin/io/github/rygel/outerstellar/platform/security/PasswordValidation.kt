package io.github.rygel.outerstellar.platform.security

private const val MIN_PASSWORD_LENGTH = 8
private const val MAX_PASSWORD_LENGTH = 128

fun validatePassword(password: String): String? {
    val error =
        when {
            password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters"
            password.length > MAX_PASSWORD_LENGTH -> "Password must be at most $MAX_PASSWORD_LENGTH characters"
            password.none { it.isUpperCase() } -> "Password must contain at least one uppercase letter"
            password.none { it.isLowerCase() } -> "Password must contain at least one lowercase letter"
            password.none { it.isDigit() } -> "Password must contain at least one digit"
            password.none { !it.isLetterOrDigit() } -> "Password must contain at least one special character"
            else -> null
        }
    return error
}
