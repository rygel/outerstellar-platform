package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiCommunityRepositoryTest : H2StellarTest() {

    private lateinit var repo: JdbiCommunityRepository
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        repo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("testuser")
    }

    private fun makeCommunity(
        name: String = "kotlin",
        displayName: String = "Kotlin Programming",
        subscriberCount: Long = 0,
    ): Community =
        Community(
            id = UUID.randomUUID(),
            name = name,
            displayName = displayName,
            description = "A community about $name",
            sidebar = "Welcome to $name",
            creatorId = userId,
            subscriberCount = subscriberCount,
        )

    @Test
    fun `create and findById round-trip`() {
        val community = makeCommunity()
        repo.create(community)

        val found = repo.findById(community.id)
        assertNotNull(found)
        assertEquals(community.id, found.id)
        assertEquals("kotlin", found.name)
        assertEquals("Kotlin Programming", found.displayName)
        assertEquals("A community about kotlin", found.description)
        assertEquals("Welcome to kotlin", found.sidebar)
        assertEquals(userId, found.creatorId)
    }

    @Test
    fun `findByName case-insensitive`() {
        val community = makeCommunity(name = "kotlin")
        repo.create(community)

        val found = repo.findByName("kotlin")
        assertNotNull(found)
        assertEquals(community.id, found.id)
    }

    @Test
    fun `findByName returns null for unknown`() {
        val found = repo.findByName("nonexistent")
        assertNull(found)
    }

    @Test
    fun `listPopular returns ordered by subscriber count`() {
        repo.create(makeCommunity(name = "small", displayName = "Small", subscriberCount = 10))
        repo.create(makeCommunity(name = "large", displayName = "Large", subscriberCount = 1000))
        repo.create(makeCommunity(name = "medium", displayName = "Medium", subscriberCount = 100))

        val popular = repo.listPopular(limit = 10, offset = 0)
        assertEquals(3, popular.size)
        assertEquals("large", popular[0].name)
        assertEquals("medium", popular[1].name)
        assertEquals("small", popular[2].name)
    }

    @Test
    fun `search by name`() {
        repo.create(makeCommunity(name = "kotlin", displayName = "Kotlin Programming"))
        repo.create(makeCommunity(name = "java", displayName = "Java Programming"))
        repo.create(makeCommunity(name = "rust", displayName = "Rust Programming"))

        val results = repo.search("kotlin", limit = 10, offset = 0)
        assertEquals(1, results.size)
        assertEquals("kotlin", results[0].name)
    }

    @Test
    fun `incrementSubscriberCount`() {
        val community = makeCommunity(subscriberCount = 10)
        repo.create(community)

        repo.incrementSubscriberCount(community.id, 5)
        val found = repo.findById(community.id)
        assertNotNull(found)
        assertEquals(15, found.subscriberCount)

        repo.incrementSubscriberCount(community.id, -3)
        val updated = repo.findById(community.id)
        assertNotNull(updated)
        assertEquals(12, updated.subscriberCount)
    }

    @Test
    fun `update modifies fields`() {
        val community = makeCommunity()
        repo.create(community)

        val updated =
            community.copy(
                displayName = "Updated Display Name",
                description = "Updated description",
                sidebar = "Updated sidebar",
                isNsfw = true,
            )
        repo.update(updated)

        val found = repo.findById(community.id)
        assertNotNull(found)
        assertEquals("Updated Display Name", found.displayName)
        assertEquals("Updated description", found.description)
        assertEquals("Updated sidebar", found.sidebar)
        assertTrue(found.isNsfw)
    }

    @Test
    fun `delete removes community`() {
        val community = makeCommunity()
        repo.create(community)
        assertNotNull(repo.findById(community.id))

        repo.delete(community.id)
        assertNull(repo.findById(community.id))
    }

    @Test
    fun `countAll`() {
        assertEquals(0, repo.countAll())

        repo.create(makeCommunity(name = "one", displayName = "One"))
        repo.create(makeCommunity(name = "two", displayName = "Two"))

        assertEquals(2, repo.countAll())
    }
}
