package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionTest {

    // ---- implies ----

    @Test
    fun `wildcard domain implies everything`() {
        val superuser = Permission("*", "*")
        assertTrue(superuser.implies(Permission("message", "read")))
        assertTrue(superuser.implies(Permission("admin", "delete", "42")))
    }

    @Test
    fun `wildcard action implies all actions in domain`() {
        val perm = Permission("message", "*")
        assertTrue(perm.implies(Permission("message", "read")))
        assertTrue(perm.implies(Permission("message", "write")))
        assertTrue(perm.implies(Permission("message", "delete", "99")))
    }

    @Test
    fun `exact permission implies only itself`() {
        val perm = Permission("message", "read", "123")
        assertTrue(perm.implies(Permission("message", "read", "123")))
        assertFalse(perm.implies(Permission("message", "read", "456")))
        assertFalse(perm.implies(Permission("message", "write", "123")))
    }

    @Test
    fun `different domain does not imply`() {
        val perm = Permission("message", "*")
        assertFalse(perm.implies(Permission("admin", "read")))
    }

    @Test
    fun `wildcard instance implies any instance`() {
        val perm = Permission("message", "read")
        assertTrue(perm.implies(Permission("message", "read", "123")))
        assertTrue(perm.implies(Permission("message", "read", "456")))
    }

    @Test
    fun `specific instance does not imply wildcard`() {
        val perm = Permission("message", "read", "123")
        assertFalse(perm.implies(Permission("message", "read")))
    }

    // ---- parse ----

    @Test
    fun `parse single part defaults action and instance to wildcard`() {
        val perm = Permission.parse("admin")
        assertEquals(Permission("admin", "*", "*"), perm)
    }

    @Test
    fun `parse two parts defaults instance to wildcard`() {
        val perm = Permission.parse("message:read")
        assertEquals(Permission("message", "read", "*"), perm)
    }

    @Test
    fun `parse three parts fills all fields`() {
        val perm = Permission.parse("message:read:123")
        assertEquals(Permission("message", "read", "123"), perm)
    }

    // ---- toString ----

    @Test
    fun `toString omits wildcard parts`() {
        assertEquals("admin", Permission("admin", "*", "*").toString())
        assertEquals("message:read", Permission("message", "read", "*").toString())
        assertEquals("message:read:123", Permission("message", "read", "123").toString())
    }

    // ---- RoleBasedPermissionResolver ----

    private val resolver = RoleBasedPermissionResolver()

    private val regularUser =
        User(
            id = UUID.randomUUID(),
            username = "alice",
            email = "alice@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
        )

    private val adminUser =
        User(
            id = UUID.randomUUID(),
            username = "admin",
            email = "admin@example.com",
            passwordHash = "hash",
            role = UserRole.ADMIN,
        )

    @Test
    fun `admin gets wildcard permissions`() {
        val perms = resolver.permissionsFor(adminUser)
        assertTrue(perms.any { it.implies(Permission("anything", "anywhere")) })
    }

    @Test
    fun `regular user gets default permissions`() {
        val perms = resolver.permissionsFor(regularUser)
        assertTrue(perms.any { it.implies(Permission("message", "read")) })
        assertTrue(perms.any { it.implies(Permission("profile", "update")) })
        assertFalse(perms.any { it.implies(Permission("admin", "read")) })
    }

    @Test
    fun `custom resolver provides custom permission set`() {
        val custom = RoleBasedPermissionResolver(userPermissions = setOf(Permission("report", "view")))
        val perms = custom.permissionsFor(regularUser)
        assertTrue(perms.any { it.implies(Permission("report", "view")) })
        assertFalse(perms.any { it.implies(Permission("message", "read")) })
    }
}
