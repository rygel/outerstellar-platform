package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.PUT
import org.http4k.core.Request
import org.http4k.core.Status
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for audit log generation (admin action tracking).
 *
 * Covers:
 * - PUT /api/v1/admin/users/{id}/enabled writes an audit entry
 * - PUT /api/v1/admin/users/{id}/role writes an audit entry
 * - Audit entries include actor and target user information
 * - Audit log grows by one entry per admin action
 * - Action names are meaningful strings (USER_ENABLED/DISABLED, USER_ROLE_CHANGED)
 */
class AuditLogIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var adminUser: User
    private lateinit var targetUser: User
    private lateinit var adminToken: String
    private lateinit var targetToken: String

    // Use direct SQL for audit log queries — avoids JooqAuditRepository.findRecent()
    // which fails in H2 due to IDENTITY column field resolution
    private val auditTable = DSL.table("plt_audit_log")
    private val actionCol = DSL.field("action", String::class.java)
    private val targetUsernameCol = DSL.field("target_username", String::class.java)

    private fun auditCount() = testDsl.fetchCount(testDsl.selectFrom(auditTable))

    private fun latestAction(): String? =
        testDsl
            .select(actionCol)
            .from(auditTable)
            .orderBy(DSL.field("created_at").desc())
            .limit(1)
            .fetchOne()
            ?.get(actionCol)

    private fun latestTargetUsername(): String? =
        testDsl
            .select(targetUsernameCol)
            .from(auditTable)
            .orderBy(DSL.field("created_at").desc())
            .limit(1)
            .fetchOne()
            ?.get(targetUsernameCol)

    @BeforeEach
    fun setupTest() {
        cleanup()
        val securityService =
            SecurityService(userRepository, encoder, auditRepository, sessionRepository = sessionRepository)

        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "auditadmin",
                email = "auditadmin@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.ADMIN,
            )
        targetUser =
            User(
                id = UUID.randomUUID(),
                username = "audittarget",
                email = "audittarget@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(adminUser)
        userRepository.save(targetUser)
        adminToken = securityService.createSession(adminUser.id)
        targetToken = securityService.createSession(targetUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun bearerHeader(user: User) = if (user == adminUser) "Bearer $adminToken" else "Bearer $targetToken"

    @Test
    fun `enable user action creates audit log entry`() {
        val countBefore = auditCount()

        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/enabled")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"enabled":true}""")
        )

        assertEquals(countBefore + 1, auditCount(), "One audit entry should be created")
    }

    @Test
    fun `enable user audit entry has correct action name`() {
        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/enabled")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"enabled":true}""")
        )

        val action = latestAction()
        assertTrue(
            action == "USER_ENABLED" || action == "USER_DISABLED",
            "Audit action should reflect enable/disable, got: $action",
        )
    }

    @Test
    fun `disable user audit entry records target username and action`() {
        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/enabled")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"enabled":false}""")
        )

        assertEquals(targetUser.username, latestTargetUsername(), "Target username should be recorded")
        assertEquals("USER_DISABLED", latestAction(), "Action should be USER_DISABLED")
    }

    @Test
    fun `change role action creates audit log entry`() {
        val countBefore = auditCount()

        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/role")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"role":"ADMIN"}""")
        )

        assertEquals(countBefore + 1, auditCount(), "One audit entry should be created for role change")
    }

    @Test
    fun `role change audit entry records action and target`() {
        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/role")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"role":"ADMIN"}""")
        )

        assertEquals("USER_ROLE_CHANGED", latestAction(), "Action should be USER_ROLE_CHANGED")
        assertEquals(targetUser.username, latestTargetUsername(), "Target username should be recorded")
    }

    @Test
    fun `two admin actions create two audit entries`() {
        val countBefore = auditCount()

        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/enabled")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"enabled":false}""")
        )
        app(
            Request(PUT, "/api/v1/admin/users/${targetUser.id}/role")
                .header("Authorization", bearerHeader(adminUser))
                .header("content-type", "application/json")
                .body("""{"role":"ADMIN"}""")
        )

        assertEquals(countBefore + 2, auditCount(), "Two audit entries should be created")
    }

    @Test
    fun `admin cannot disable themselves - no audit entry created`() {
        val countBefore = auditCount()

        val response =
            app(
                Request(PUT, "/api/v1/admin/users/${adminUser.id}/enabled")
                    .header("Authorization", bearerHeader(adminUser))
                    .header("content-type", "application/json")
                    .body("""{"enabled":false}""")
            )

        // InsufficientPermissionException → BAD_REQUEST in UserAdminApi
        assertEquals(Status.BAD_REQUEST, response.status)
        assertEquals(countBefore, auditCount(), "No audit entry for self-action")
    }
}
