package dev.outerstellar.starter.security

import java.util.UUID

enum class UserRole {
    USER, ADMIN
}

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val enabled: Boolean = true
)

interface UserRepository {
    fun findById(id: UUID): User?
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun save(user: User)
}
