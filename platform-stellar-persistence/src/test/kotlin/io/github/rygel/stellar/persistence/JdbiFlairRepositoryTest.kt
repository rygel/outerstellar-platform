package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiFlairRepositoryTest : H2StellarTest() {

    private lateinit var repo: JdbiFlairRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var userId: UUID
    private lateinit var communityId: UUID

    @BeforeEach
    fun setUp() {
        repo = JdbiFlairRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("flairuser")
        communityId = createTestCommunity("testcommunity", userId)
    }

    private fun createTestCommunity(name: String, creatorId: UUID): UUID {
        val id = UUID.randomUUID()
        communityRepo.create(
            Community(id = id, name = name, displayName = name, description = "Test community", creatorId = creatorId)
        )
        return id
    }

    private fun makeFlair(
        name: String = "Discussion",
        color: String = "#ff5722",
        type: FlairType = FlairType.POST,
    ): Flair = Flair(id = UUID.randomUUID(), communityId = communityId, name = name, color = color, type = type)

    @Test
    fun `create and findById round-trip`() {
        val flair = makeFlair()
        repo.create(flair)

        val found = repo.findById(flair.id)
        assertNotNull(found)
        assertEquals(flair.id, found.id)
        assertEquals(communityId, found.communityId)
        assertEquals("Discussion", found.name)
        assertEquals("#ff5722", found.color)
        assertEquals(FlairType.POST, found.type)
    }

    @Test
    fun `findById returns null for unknown id`() {
        val found = repo.findById(UUID.randomUUID())
        assertNull(found)
    }

    @Test
    fun `listByCommunity returns all flairs for community`() {
        repo.create(makeFlair(name = "Discussion", type = FlairType.POST))
        repo.create(makeFlair(name = "Question", type = FlairType.POST))
        repo.create(makeFlair(name = "Veteran", type = FlairType.USER))

        val all = repo.listByCommunity(communityId)
        assertEquals(3, all.size)
    }

    @Test
    fun `listByCommunity filters by type`() {
        repo.create(makeFlair(name = "Discussion", type = FlairType.POST))
        repo.create(makeFlair(name = "Question", type = FlairType.POST))
        repo.create(makeFlair(name = "Veteran", type = FlairType.USER))

        val postFlairs = repo.listByCommunity(communityId, FlairType.POST)
        assertEquals(2, postFlairs.size)
        assertTrue(postFlairs.all { it.type == FlairType.POST })

        val userFlairs = repo.listByCommunity(communityId, FlairType.USER)
        assertEquals(1, userFlairs.size)
        assertEquals(FlairType.USER, userFlairs[0].type)
    }

    @Test
    fun `listByCommunity returns empty for unknown community`() {
        val result = repo.listByCommunity(UUID.randomUUID())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listByCommunity returns flairs ordered by name`() {
        repo.create(makeFlair(name = "Zzz", type = FlairType.POST))
        repo.create(makeFlair(name = "Aaa", type = FlairType.POST))
        repo.create(makeFlair(name = "Mmm", type = FlairType.POST))

        val flairs = repo.listByCommunity(communityId, FlairType.POST)
        assertEquals(listOf("Aaa", "Mmm", "Zzz"), flairs.map { it.name })
    }

    @Test
    fun `delete removes flair`() {
        val flair = makeFlair()
        repo.create(flair)
        assertNotNull(repo.findById(flair.id))

        repo.delete(flair.id)
        assertNull(repo.findById(flair.id))
    }

    @Test
    fun `setUserFlair and getUserFlair round-trip`() {
        val flair = makeFlair(name = "Veteran", type = FlairType.USER)
        repo.create(flair)

        repo.setUserFlair(userId, communityId, flair.id)

        val userFlair = repo.getUserFlair(userId, communityId)
        assertNotNull(userFlair)
        assertEquals(flair.id, userFlair.id)
        assertEquals("Veteran", userFlair.name)
    }

    @Test
    fun `getUserFlair returns null when no flair assigned`() {
        val result = repo.getUserFlair(userId, communityId)
        assertNull(result)
    }

    @Test
    fun `setUserFlair replaces existing flair`() {
        val flair1 = makeFlair(name = "Veteran", type = FlairType.USER)
        val flair2 = makeFlair(name = "Newbie", type = FlairType.USER)
        repo.create(flair1)
        repo.create(flair2)

        repo.setUserFlair(userId, communityId, flair1.id)
        assertEquals(flair1.id, repo.getUserFlair(userId, communityId)!!.id)

        repo.setUserFlair(userId, communityId, flair2.id)
        assertEquals(flair2.id, repo.getUserFlair(userId, communityId)!!.id)
    }

    @Test
    fun `removeUserFlair removes assigned flair`() {
        val flair = makeFlair(name = "Veteran", type = FlairType.USER)
        repo.create(flair)
        repo.setUserFlair(userId, communityId, flair.id)
        assertNotNull(repo.getUserFlair(userId, communityId))

        repo.removeUserFlair(userId, communityId)
        assertNull(repo.getUserFlair(userId, communityId))
    }

    @Test
    fun `removeUserFlair does nothing when no flair assigned`() {
        // Should not throw
        repo.removeUserFlair(userId, communityId)
        assertNull(repo.getUserFlair(userId, communityId))
    }

    @Test
    fun `deleting flair cascades to user flair assignments`() {
        val flair = makeFlair(name = "Veteran", type = FlairType.USER)
        repo.create(flair)
        repo.setUserFlair(userId, communityId, flair.id)
        assertNotNull(repo.getUserFlair(userId, communityId))

        repo.delete(flair.id)
        assertNull(repo.getUserFlair(userId, communityId))
    }
}
