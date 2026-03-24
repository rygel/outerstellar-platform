package io.github.rygel.outerstellar.platform.security

/**
 * Wildcard permission following the `domain:action:instance` pattern.
 *
 * Each part can be `*` (wildcard) to match any value. Permissions are compared via [implies]: a permission with
 * wildcards implies more specific permissions.
 *
 * Examples:
 * - `Permission("*", "*")` implies everything (superuser)
 * - `Permission("message", "*")` implies `Permission("message", "read")` and `Permission("message", "write")`
 * - `Permission("message", "read", "123")` implies only that exact instance
 */
data class Permission(val domain: String, val action: String = "*", val instance: String = "*") {

    /** Returns true if this permission grants access that [other] requires. */
    fun implies(other: Permission): Boolean =
        (domain == "*" || domain == other.domain) &&
            (action == "*" || action == other.action) &&
            (instance == "*" || instance == other.instance)

    override fun toString(): String =
        if (instance == "*") {
            if (action == "*") domain else "$domain:$action"
        } else {
            "$domain:$action:$instance"
        }

    companion object {
        /**
         * Parse a colon-separated permission string. Missing parts default to `*`.
         * - `"admin"` → `Permission("admin", "*", "*")`
         * - `"message:read"` → `Permission("message", "read", "*")`
         * - `"message:read:123"` → `Permission("message", "read", "123")`
         */
        fun parse(s: String): Permission {
            val parts = s.split(":", limit = 3)
            return Permission(
                domain = parts[0],
                action = parts.getOrElse(1) { "*" },
                instance = parts.getOrElse(2) { "*" },
            )
        }
    }
}

/**
 * Resolves the set of permissions granted to a [User].
 *
 * Implementations can be role-based (mapping roles to static permission sets), database-backed (per-user permission
 * rows), or any combination.
 */
fun interface PermissionResolver {
    fun permissionsFor(user: User): Set<Permission>
}

/**
 * Default resolver that maps [UserRole] values to permission sets. Admins get full wildcard access; regular users get a
 * configurable default set.
 */
class RoleBasedPermissionResolver(
    private val adminPermissions: Set<Permission> = setOf(Permission("*", "*")),
    private val userPermissions: Set<Permission> =
        setOf(
            Permission("message", "*"),
            Permission("profile", "*"),
            Permission("notification", "*"),
            Permission("contact", "*"),
        ),
) : PermissionResolver {

    override fun permissionsFor(user: User): Set<Permission> =
        when (user.role) {
            UserRole.ADMIN -> adminPermissions
            UserRole.USER -> userPermissions
        }
}
