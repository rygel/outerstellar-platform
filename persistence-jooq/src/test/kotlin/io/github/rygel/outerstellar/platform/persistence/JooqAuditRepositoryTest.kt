package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.AuditEntry
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JooqAuditRepositoryTest : H2JooqTest() {

    private val repo by lazy { JooqAuditRepository(dsl) }

    private fun entry(action: String = "LOGIN", actorUsername: String = "admin", detail: String = "") =
        AuditEntry(
            actorId = UUID.randomUUID().toString(),
            actorUsername = actorUsername,
            targetId = UUID.randomUUID().toString(),
            targetUsername = "target",
            action = action,
            detail = detail,
        )

    @Test
    fun `log and findRecent round-trips`() {
        repo.log(entry("LOGIN", "alice"))
        val recent = repo.findRecent(10)
        assertEquals(1, recent.size)
        assertEquals("LOGIN", recent[0].action)
        assertEquals("alice", recent[0].actorUsername)
    }

    @Test
    fun `findRecent respects limit`() {
        repeat(5) { repo.log(entry("ACTION_$it")) }
        assertEquals(3, repo.findRecent(3).size)
    }

    @Test
    fun `findRecent returns newest first`() {
        repo.log(entry("FIRST"))
        Thread.sleep(10)
        repo.log(entry("SECOND"))
        val recent = repo.findRecent(10)
        assertEquals("SECOND", recent[0].action)
        assertEquals("FIRST", recent[1].action)
    }

    @Test
    fun `findRecent returns empty list when no entries`() {
        assertTrue(repo.findRecent(10).isEmpty())
    }

    @Test
    fun `log stores optional fields correctly`() {
        repo.log(entry("ACTION", detail = "some detail"))
        val found = repo.findRecent(1)[0]
        assertEquals("some detail", found.detail)
        assertNotNull(found.actorId)
        assertNotNull(found.targetId)
    }

    @Test
    fun `log handles null actor and target`() {
        repo.log(
            AuditEntry(
                actorId = null,
                actorUsername = "system",
                targetId = null,
                targetUsername = null,
                action = "SYSTEM_EVENT",
                detail = "",
            )
        )
        val found = repo.findRecent(1)[0]
        assertNull(found.actorId)
        assertNull(found.targetId)
        assertEquals("SYSTEM_EVENT", found.action)
    }
}
