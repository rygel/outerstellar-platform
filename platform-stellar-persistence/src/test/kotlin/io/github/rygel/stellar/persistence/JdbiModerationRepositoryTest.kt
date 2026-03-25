package io.github.rygel.stellar.persistence

import io.github.rygel.stellar.model.Community
import io.github.rygel.stellar.model.CommunityBan
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.SavedItemType
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JdbiModerationRepositoryTest : H2StellarTest() {

    private lateinit var memberRepo: JdbiCommunityMemberRepository
    private lateinit var savedRepo: JdbiSavedItemRepository
    private lateinit var karmaRepo: JdbiKarmaRepository
    private lateinit var communityRepo: JdbiCommunityRepository
    private lateinit var userId: UUID
    private lateinit var communityId: UUID

    @BeforeEach
    fun setUp() {
        memberRepo = JdbiCommunityMemberRepository(jdbi)
        savedRepo = JdbiSavedItemRepository(jdbi)
        karmaRepo = JdbiKarmaRepository(jdbi)
        communityRepo = JdbiCommunityRepository(jdbi)
        userId = createTestUser("moderator")
        communityId = UUID.randomUUID()
        communityRepo.create(
            Community(id = communityId, name = "modcommunity", displayName = "Moderated Community", creatorId = userId)
        )
    }

    // -- CommunityMemberRepository tests --

    @Test
    fun `addMember and findMembership`() {
        memberRepo.addMember(userId, communityId, CommunityRole.MODERATOR)

        val membership = memberRepo.findMembership(userId, communityId)
        assertNotNull(membership)
        assertEquals(userId, membership.userId)
        assertEquals(communityId, membership.communityId)
        assertEquals(CommunityRole.MODERATOR, membership.role)
    }

    @Test
    fun `findMembership returns null when not a member`() {
        val found = memberRepo.findMembership(userId, communityId)
        assertNull(found)
    }

    @Test
    fun `listModerators`() {
        val user2 = createTestUser("admin_user")
        val user3 = createTestUser("regular_member")

        memberRepo.addMember(userId, communityId, CommunityRole.MODERATOR)
        memberRepo.addMember(user2, communityId, CommunityRole.ADMIN)
        memberRepo.addMember(user3, communityId, CommunityRole.MEMBER)

        val moderators = memberRepo.listModerators(communityId)
        assertEquals(2, moderators.size)
        val moderatorUserIds = moderators.map { it.userId }.toSet()
        assertTrue(moderatorUserIds.contains(userId))
        assertTrue(moderatorUserIds.contains(user2))
        assertFalse(moderatorUserIds.contains(user3))
    }

    @Test
    fun `removeMember`() {
        memberRepo.addMember(userId, communityId, CommunityRole.MODERATOR)
        assertNotNull(memberRepo.findMembership(userId, communityId))

        memberRepo.removeMember(userId, communityId)
        assertNull(memberRepo.findMembership(userId, communityId))
    }

    @Test
    fun `ban and isBanned`() {
        assertFalse(memberRepo.isBanned(userId, communityId))

        val banner = createTestUser("banner")
        memberRepo.ban(
            CommunityBan(userId = userId, communityId = communityId, reason = "Rule violation", bannedBy = banner)
        )

        assertTrue(memberRepo.isBanned(userId, communityId))
    }

    @Test
    fun `unban`() {
        val banner = createTestUser("banner2")
        memberRepo.ban(CommunityBan(userId = userId, communityId = communityId, reason = "Spam", bannedBy = banner))
        assertTrue(memberRepo.isBanned(userId, communityId))

        memberRepo.unban(userId, communityId)
        assertFalse(memberRepo.isBanned(userId, communityId))
    }

    // -- SavedItemRepository tests --

    @Test
    fun `save and isSaved`() {
        val targetId = UUID.randomUUID()
        assertFalse(savedRepo.isSaved(userId, targetId, SavedItemType.POST))

        savedRepo.save(userId, targetId, SavedItemType.POST)

        assertTrue(savedRepo.isSaved(userId, targetId, SavedItemType.POST))
    }

    @Test
    fun `unsave`() {
        val targetId = UUID.randomUUID()
        savedRepo.save(userId, targetId, SavedItemType.POST)
        assertTrue(savedRepo.isSaved(userId, targetId, SavedItemType.POST))

        savedRepo.unsave(userId, targetId, SavedItemType.POST)
        assertFalse(savedRepo.isSaved(userId, targetId, SavedItemType.POST))
    }

    @Test
    fun `listSaved`() {
        val target1 = UUID.randomUUID()
        val target2 = UUID.randomUUID()

        savedRepo.save(userId, target1, SavedItemType.POST)
        savedRepo.save(userId, target2, SavedItemType.COMMENT)

        val saved = savedRepo.listSaved(userId, limit = 10, offset = 0)
        assertEquals(2, saved.size)
    }

    @Test
    fun `findSavedIds`() {
        val target1 = UUID.randomUUID()
        val target2 = UUID.randomUUID()
        val target3 = UUID.randomUUID()

        savedRepo.save(userId, target1, SavedItemType.POST)
        savedRepo.save(userId, target2, SavedItemType.POST)

        val savedIds = savedRepo.findSavedIds(userId, listOf(target1, target2, target3), SavedItemType.POST)
        assertEquals(2, savedIds.size)
        assertTrue(savedIds.contains(target1))
        assertTrue(savedIds.contains(target2))
        assertFalse(savedIds.contains(target3))
    }

    @Test
    fun `findSavedIds with empty list returns empty set`() {
        val savedIds = savedRepo.findSavedIds(userId, emptyList(), SavedItemType.POST)
        assertTrue(savedIds.isEmpty())
    }

    // -- KarmaRepository tests --

    @Test
    fun `getKarma returns defaults for new user`() {
        val karma = karmaRepo.getKarma(userId)
        assertEquals(userId, karma.userId)
        assertEquals(0, karma.postKarma)
        assertEquals(0, karma.commentKarma)
    }

    @Test
    fun `ensureExists creates row`() {
        karmaRepo.ensureExists(userId)

        val karma = karmaRepo.getKarma(userId)
        assertEquals(userId, karma.userId)
        assertEquals(0, karma.postKarma)
        assertEquals(0, karma.commentKarma)
    }

    @Test
    fun `updatePostKarma`() {
        karmaRepo.updatePostKarma(userId, 10)

        val karma = karmaRepo.getKarma(userId)
        assertEquals(10, karma.postKarma)
        assertEquals(0, karma.commentKarma)

        karmaRepo.updatePostKarma(userId, -3)
        val updated = karmaRepo.getKarma(userId)
        assertEquals(7, updated.postKarma)
    }

    @Test
    fun `updateCommentKarma`() {
        karmaRepo.updateCommentKarma(userId, 5)

        val karma = karmaRepo.getKarma(userId)
        assertEquals(0, karma.postKarma)
        assertEquals(5, karma.commentKarma)

        karmaRepo.updateCommentKarma(userId, 3)
        val updated = karmaRepo.getKarma(userId)
        assertEquals(8, updated.commentKarma)
    }
}
