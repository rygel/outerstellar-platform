package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Comment
import io.github.rygel.stellar.model.CommunityBan
import io.github.rygel.stellar.model.CommunityMember
import io.github.rygel.stellar.model.CommunityRole
import io.github.rygel.stellar.model.NotModeratorException
import io.github.rygel.stellar.model.Post
import io.github.rygel.stellar.model.PostNotFoundException
import io.github.rygel.stellar.model.Report
import io.github.rygel.stellar.model.ReportNotFoundException
import io.github.rygel.stellar.model.ReportStatus
import io.github.rygel.stellar.model.ReportTarget
import io.github.rygel.stellar.persistence.CommentRepository
import io.github.rygel.stellar.persistence.CommunityMemberRepository
import io.github.rygel.stellar.persistence.PostRepository
import io.github.rygel.stellar.persistence.ReportRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModerationServiceTest {

    private lateinit var postRepo: PostRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var memberRepo: CommunityMemberRepository
    private lateinit var reportRepo: ReportRepository
    private lateinit var service: ModerationService

    private val communityId = UUID.randomUUID()
    private val moderatorId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        postRepo = mockk()
        commentRepo = mockk()
        memberRepo = mockk()
        reportRepo = mockk()
        service = ModerationService(postRepo, commentRepo, memberRepo, reportRepo)
    }

    private fun stubModerator(uid: UUID = moderatorId, cid: UUID = communityId) {
        every { memberRepo.findMembership(uid, cid) } returns
            CommunityMember(userId = uid, communityId = cid, role = CommunityRole.MODERATOR)
    }

    private fun stubNotModerator(uid: UUID = moderatorId, cid: UUID = communityId) {
        every { memberRepo.findMembership(uid, cid) } returns null
    }

    // --- removePost ---

    @Test
    fun `removePost sets isRemoved to true`() {
        stubModerator()
        val postId = UUID.randomUUID()
        val post = post(id = postId)
        every { postRepo.findById(postId) } returns post
        every { postRepo.update(any()) } answers { firstArg() }

        val result = service.removePost(postId, moderatorId, communityId)

        assertTrue(result.isRemoved)
        verify { postRepo.update(match { it.isRemoved }) }
    }

    @Test
    fun `removePost throws NotModeratorException when not moderator`() {
        stubNotModerator()
        val postId = UUID.randomUUID()

        assertThrows<NotModeratorException> { service.removePost(postId, moderatorId, communityId) }
        verify(exactly = 0) { postRepo.update(any()) }
    }

    @Test
    fun `removePost throws PostNotFoundException when post does not exist`() {
        stubModerator()
        val postId = UUID.randomUUID()
        every { postRepo.findById(postId) } returns null

        assertThrows<PostNotFoundException> { service.removePost(postId, moderatorId, communityId) }
    }

    // --- lockPost ---

    @Test
    fun `lockPost sets isLocked to true`() {
        stubModerator()
        val postId = UUID.randomUUID()
        val post = post(id = postId)
        every { postRepo.findById(postId) } returns post
        every { postRepo.update(any()) } answers { firstArg() }

        val result = service.lockPost(postId, moderatorId, communityId)

        assertTrue(result.isLocked)
    }

    @Test
    fun `lockPost throws NotModeratorException when not moderator`() {
        stubNotModerator()

        assertThrows<NotModeratorException> { service.lockPost(UUID.randomUUID(), moderatorId, communityId) }
    }

    // --- pinPost ---

    @Test
    fun `pinPost toggles isPinned from false to true`() {
        stubModerator()
        val postId = UUID.randomUUID()
        val post = post(id = postId, isPinned = false)
        every { postRepo.findById(postId) } returns post
        every { postRepo.update(any()) } answers { firstArg() }

        val result = service.pinPost(postId, moderatorId, communityId)

        assertTrue(result.isPinned)
    }

    @Test
    fun `pinPost toggles isPinned from true to false`() {
        stubModerator()
        val postId = UUID.randomUUID()
        val post = post(id = postId, isPinned = true)
        every { postRepo.findById(postId) } returns post
        every { postRepo.update(any()) } answers { firstArg() }

        val result = service.pinPost(postId, moderatorId, communityId)

        assertFalse(result.isPinned)
    }

    @Test
    fun `pinPost throws NotModeratorException when not moderator`() {
        stubNotModerator()

        assertThrows<NotModeratorException> { service.pinPost(UUID.randomUUID(), moderatorId, communityId) }
    }

    // --- removeComment ---

    @Test
    fun `removeComment sets isRemoved to true`() {
        stubModerator()
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val comment = comment(id = commentId, postId = postId)
        val post = post(id = postId, communityId = communityId)
        every { commentRepo.findById(commentId) } returns comment
        every { postRepo.findById(postId) } returns post
        every { commentRepo.update(any()) } answers { firstArg() }

        val result = service.removeComment(commentId, moderatorId)

        assertTrue(result.isRemoved)
        verify { commentRepo.update(match { it.isRemoved }) }
    }

    @Test
    fun `removeComment throws NotModeratorException when not moderator`() {
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val comment = comment(id = commentId, postId = postId)
        val post = post(id = postId, communityId = communityId)
        every { commentRepo.findById(commentId) } returns comment
        every { postRepo.findById(postId) } returns post
        stubNotModerator()

        assertThrows<NotModeratorException> { service.removeComment(commentId, moderatorId) }
    }

    @Test
    fun `removeComment derives communityId from post and rejects cross-community mod`() {
        val otherCommunityId = UUID.randomUUID()
        val postId = UUID.randomUUID()
        val commentId = UUID.randomUUID()
        val comment = comment(id = commentId, postId = postId)
        val post = post(id = postId, communityId = otherCommunityId)
        every { commentRepo.findById(commentId) } returns comment
        every { postRepo.findById(postId) } returns post
        // moderator is a mod of communityId but NOT of otherCommunityId
        stubModerator(uid = moderatorId, cid = communityId)
        stubNotModerator(uid = moderatorId, cid = otherCommunityId)

        assertThrows<NotModeratorException> { service.removeComment(commentId, moderatorId) }
        verify(exactly = 0) { commentRepo.update(any()) }
    }

    // --- banUser ---

    @Test
    fun `banUser creates ban record`() {
        stubModerator()
        val banSlot = slot<CommunityBan>()
        every { memberRepo.ban(capture(banSlot)) } just runs

        service.banUser(userId, communityId, "spam", moderatorId)

        assertEquals(userId, banSlot.captured.userId)
        assertEquals(communityId, banSlot.captured.communityId)
        assertEquals("spam", banSlot.captured.reason)
        assertEquals(moderatorId, banSlot.captured.bannedBy)
    }

    @Test
    fun `banUser throws NotModeratorException when not moderator`() {
        stubNotModerator()

        assertThrows<NotModeratorException> { service.banUser(userId, communityId, "spam", moderatorId) }
    }

    // --- unbanUser ---

    @Test
    fun `unbanUser removes ban`() {
        stubModerator()
        every { memberRepo.unban(userId, communityId) } just runs

        service.unbanUser(userId, communityId, moderatorId)

        verify { memberRepo.unban(userId, communityId) }
    }

    @Test
    fun `unbanUser throws NotModeratorException when not moderator`() {
        stubNotModerator()

        assertThrows<NotModeratorException> { service.unbanUser(userId, communityId, moderatorId) }
    }

    // --- createReport ---

    @Test
    fun `createReport creates and returns report`() {
        every { reportRepo.create(any()) } answers { firstArg() }

        val targetId = UUID.randomUUID()
        val result = service.createReport(userId, targetId, ReportTarget.POST, "Rule violation")

        assertEquals(userId, result.reporterId)
        assertEquals(targetId, result.targetId)
        assertEquals(ReportTarget.POST, result.targetType)
        assertEquals("Rule violation", result.reason)
        assertEquals(ReportStatus.PENDING, result.status)
    }

    @Test
    fun `createReport trims reason`() {
        every { reportRepo.create(any()) } answers { firstArg() }

        val result = service.createReport(userId, UUID.randomUUID(), ReportTarget.COMMENT, "  spam  ")

        assertEquals("spam", result.reason)
    }

    // --- resolveReport ---

    @Test
    fun `resolveReport sets status to RESOLVED`() {
        val reportId = UUID.randomUUID()
        val report = report(id = reportId)
        every { reportRepo.findById(reportId) } returns report
        every { reportRepo.updateStatus(reportId, ReportStatus.RESOLVED, moderatorId) } just runs

        val result = service.resolveReport(reportId, moderatorId)

        assertEquals(ReportStatus.RESOLVED, result.status)
        assertEquals(moderatorId, result.resolvedBy)
    }

    @Test
    fun `resolveReport throws ReportNotFoundException when not found`() {
        val reportId = UUID.randomUUID()
        every { reportRepo.findById(reportId) } returns null

        assertThrows<ReportNotFoundException> { service.resolveReport(reportId, moderatorId) }
    }

    // --- dismissReport ---

    @Test
    fun `dismissReport sets status to DISMISSED`() {
        val reportId = UUID.randomUUID()
        val report = report(id = reportId)
        every { reportRepo.findById(reportId) } returns report
        every { reportRepo.updateStatus(reportId, ReportStatus.DISMISSED, moderatorId) } just runs

        val result = service.dismissReport(reportId, moderatorId)

        assertEquals(ReportStatus.DISMISSED, result.status)
        assertEquals(moderatorId, result.resolvedBy)
    }

    // --- getPendingReports ---

    @Test
    fun `getPendingReports returns pending reports for community`() {
        val reports = listOf(report(), report())
        every { reportRepo.listByCommunity(communityId, ReportStatus.PENDING) } returns reports

        val result = service.getPendingReports(communityId)

        assertEquals(2, result.size)
    }

    // --- isModerator ---

    @Test
    fun `isModerator returns true for MODERATOR role`() {
        every { memberRepo.findMembership(moderatorId, communityId) } returns
            CommunityMember(userId = moderatorId, communityId = communityId, role = CommunityRole.MODERATOR)

        assertTrue(service.isModerator(moderatorId, communityId))
    }

    @Test
    fun `isModerator returns true for ADMIN role`() {
        every { memberRepo.findMembership(moderatorId, communityId) } returns
            CommunityMember(userId = moderatorId, communityId = communityId, role = CommunityRole.ADMIN)

        assertTrue(service.isModerator(moderatorId, communityId))
    }

    @Test
    fun `isModerator returns false for MEMBER role`() {
        every { memberRepo.findMembership(userId, communityId) } returns
            CommunityMember(userId = userId, communityId = communityId, role = CommunityRole.MEMBER)

        assertFalse(service.isModerator(userId, communityId))
    }

    @Test
    fun `isModerator returns false when no membership`() {
        every { memberRepo.findMembership(userId, communityId) } returns null

        assertFalse(service.isModerator(userId, communityId))
    }

    // --- helpers ---

    private fun post(id: UUID = UUID.randomUUID(), communityId: UUID = this.communityId, isPinned: Boolean = false) =
        Post(id = id, communityId = communityId, authorId = UUID.randomUUID(), title = "Test Post", isPinned = isPinned)

    private fun comment(id: UUID = UUID.randomUUID(), postId: UUID = UUID.randomUUID()) =
        Comment(id = id, postId = postId, authorId = UUID.randomUUID(), body = "Test comment")

    private fun report(id: UUID = UUID.randomUUID()) =
        Report(
            id = id,
            reporterId = UUID.randomUUID(),
            targetId = UUID.randomUUID(),
            targetType = ReportTarget.POST,
            reason = "Test reason",
        )
}
