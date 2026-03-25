package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityRule
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiCommunityRuleRepositoryTest : H2StellarTest() {

    private lateinit var ruleRepo: JdbiCommunityRuleRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var communityId: UUID
    private lateinit var userId: UUID

    @BeforeEach
    fun setUp() {
        ruleRepo = JdbiCommunityRuleRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("testuser")

        val community =
            Community(
                id = UUID.randomUUID(),
                name = "testrules",
                displayName = "Test Rules Community",
                description = "A community for testing rules",
                creatorId = userId,
            )
        communityRepo.create(community)
        communityId = community.id
    }

    private fun makeRule(
        title: String = "No spam",
        description: String = "Do not post spam",
        position: Int = 0,
    ): CommunityRule =
        CommunityRule(
            id = UUID.randomUUID(),
            communityId = communityId,
            title = title,
            description = description,
            position = position,
        )

    @Test
    fun `create and listByCommunity round-trip`() {
        val rule = makeRule()
        ruleRepo.create(rule)

        val rules = ruleRepo.listByCommunity(communityId)
        assertEquals(1, rules.size)
        assertEquals(rule.id, rules[0].id)
        assertEquals("No spam", rules[0].title)
        assertEquals("Do not post spam", rules[0].description)
        assertEquals(0, rules[0].position)
    }

    @Test
    fun `listByCommunity returns empty for unknown community`() {
        val rules = ruleRepo.listByCommunity(UUID.randomUUID())
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `listByCommunity returns rules ordered by position`() {
        ruleRepo.create(makeRule(title = "Rule C", position = 2))
        ruleRepo.create(makeRule(title = "Rule A", position = 0))
        ruleRepo.create(makeRule(title = "Rule B", position = 1))

        val rules = ruleRepo.listByCommunity(communityId)
        assertEquals(3, rules.size)
        assertEquals("Rule A", rules[0].title)
        assertEquals("Rule B", rules[1].title)
        assertEquals("Rule C", rules[2].title)
    }

    @Test
    fun `update modifies title and description`() {
        val rule = makeRule()
        ruleRepo.create(rule)

        val updated = rule.copy(title = "Updated title", description = "Updated description")
        ruleRepo.update(updated)

        val rules = ruleRepo.listByCommunity(communityId)
        assertEquals(1, rules.size)
        assertEquals("Updated title", rules[0].title)
        assertEquals("Updated description", rules[0].description)
    }

    @Test
    fun `delete removes rule`() {
        val rule = makeRule()
        ruleRepo.create(rule)
        assertEquals(1, ruleRepo.listByCommunity(communityId).size)

        ruleRepo.delete(rule.id)
        assertTrue(ruleRepo.listByCommunity(communityId).isEmpty())
    }

    @Test
    fun `reorder changes position values`() {
        val ruleA = makeRule(title = "Rule A", position = 0)
        val ruleB = makeRule(title = "Rule B", position = 1)
        val ruleC = makeRule(title = "Rule C", position = 2)
        ruleRepo.create(ruleA)
        ruleRepo.create(ruleB)
        ruleRepo.create(ruleC)

        // Reverse the order
        ruleRepo.reorder(communityId, listOf(ruleC.id, ruleB.id, ruleA.id))

        val rules = ruleRepo.listByCommunity(communityId)
        assertEquals(3, rules.size)
        assertEquals("Rule C", rules[0].title)
        assertEquals(0, rules[0].position)
        assertEquals("Rule B", rules[1].title)
        assertEquals(1, rules[1].position)
        assertEquals("Rule A", rules[2].title)
        assertEquals(2, rules[2].position)
    }

    @Test
    fun `multiple communities have independent rules`() {
        val otherUser = createTestUser("otheruser")
        val otherCommunity =
            Community(
                id = UUID.randomUUID(),
                name = "other",
                displayName = "Other Community",
                description = "Another community",
                creatorId = otherUser,
            )
        communityRepo.create(otherCommunity)

        ruleRepo.create(makeRule(title = "Rule for first"))
        ruleRepo.create(
            CommunityRule(
                id = UUID.randomUUID(),
                communityId = otherCommunity.id,
                title = "Rule for second",
                position = 0,
            )
        )

        assertEquals(1, ruleRepo.listByCommunity(communityId).size)
        assertEquals(1, ruleRepo.listByCommunity(otherCommunity.id).size)
        assertEquals("Rule for first", ruleRepo.listByCommunity(communityId)[0].title)
        assertEquals("Rule for second", ruleRepo.listByCommunity(otherCommunity.id)[0].title)
    }
}
