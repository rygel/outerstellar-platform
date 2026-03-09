package dev.outerstellar.starter.security

enum class Role {
    USER, ADMIN
}

data class User(val id: String, val email: String, val roles: Set<Role>)

data class UserPrincipal(val user: User)
