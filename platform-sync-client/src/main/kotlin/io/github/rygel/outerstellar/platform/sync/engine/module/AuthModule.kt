package io.github.rygel.outerstellar.platform.sync.engine.module

data class AuthState(val isLoggedIn: Boolean = false, val userName: String = "", val userRole: String? = null)

interface AuthListener {
    fun onAuthStateChanged(state: AuthState) {}

    fun onSessionExpired() {}
}

interface AuthModule {
    val authState: AuthState

    fun addListener(listener: AuthListener)

    fun removeListener(listener: AuthListener)

    fun login(username: String, password: String): Result<Unit>

    fun register(username: String, password: String): Result<Unit>

    fun logout()

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    fun requestPasswordReset(email: String): Result<Unit>

    fun resetPassword(token: String, newPassword: String): Result<Unit>

    fun resetState() {}
}
