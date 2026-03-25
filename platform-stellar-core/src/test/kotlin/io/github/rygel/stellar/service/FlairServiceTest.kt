package io.github.rygel.stellar.service

import io.github.rygel.stellar.model.Flair
import io.github.rygel.stellar.model.FlairType
import io.github.rygel.stellar.persistence.FlairRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FlairServiceTest {

    private lateinit var flairRepo: FlairRepository
    private lateinit var service: FlairService

    @BeforeEach
    fun setUp() {
        flairRepo = mockk()
        service = FlairService(flairRepo)
    }

    @Test
    fun `listPostFlairs delegates to repository with POST type`() {
        val communityId = UUID.randomUUID()
        val flairs = listOf(flair(communityId = communityId, type = FlairType.POST))
        every { flairRepo.listByCommunity(communityId, FlairType.POST) } returns flairs

        val result = service.listPostFlairs(communityId)

        assertEquals(1, result.size)
        assertEquals(FlairType.POST, result[0].type)
        verify { flairRepo.listByCommunity(communityId, FlairType.POST) }
    }

    @Test
    fun `listUserFlairs delegates to repository with USER type`() {
        val communityId = UUID.randomUUID()
        val flairs = listOf(flair(communityId = communityId, type = FlairType.USER))
        every { flairRepo.listByCommunity(communityId, FlairType.USER) } returns flairs

        val result = service.listUserFlairs(communityId)

        assertEquals(1, result.size)
        assertEquals(FlairType.USER, result[0].type)
        verify { flairRepo.listByCommunity(communityId, FlairType.USER) }
    }

    @Test
    fun `createFlair creates flair with correct fields`() {
        val communityId = UUID.randomUUID()
        val flairSlot = slot<Flair>()
        every { flairRepo.create(capture(flairSlot)) } answers { flairSlot.captured }

        val result = service.createFlair(communityId, "Discussion", "#ff5722", FlairType.POST)

        assertNotNull(result)
        assertEquals(communityId, result.communityId)
        assertEquals("Discussion", result.name)
        assertEquals("#ff5722", result.color)
        assertEquals(FlairType.POST, result.type)
        verify { flairRepo.create(any()) }
    }

    @Test
    fun `createFlair trims name and color`() {
        val communityId = UUID.randomUUID()
        val flairSlot = slot<Flair>()
        every { flairRepo.create(capture(flairSlot)) } answers { flairSlot.captured }

        val result = service.createFlair(communityId, "  Question  ", "  #0000ff  ", FlairType.USER)

        assertEquals("Question", result.name)
        assertEquals("#0000ff", result.color)
    }

    @Test
    fun `deleteFlair delegates to repository`() {
        val flairId = UUID.randomUUID()
        every { flairRepo.delete(flairId) } just runs

        service.deleteFlair(flairId)

        verify { flairRepo.delete(flairId) }
    }

    @Test
    fun `setUserFlair delegates to repository`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()
        val flairId = UUID.randomUUID()
        every { flairRepo.setUserFlair(userId, communityId, flairId) } just runs

        service.setUserFlair(userId, communityId, flairId)

        verify { flairRepo.setUserFlair(userId, communityId, flairId) }
    }

    @Test
    fun `getUserFlair returns flair when assigned`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()
        val expected = flair(communityId = communityId, type = FlairType.USER)
        every { flairRepo.getUserFlair(userId, communityId) } returns expected

        val result = service.getUserFlair(userId, communityId)

        assertNotNull(result)
        assertEquals(expected.id, result!!.id)
    }

    @Test
    fun `getUserFlair returns null when not assigned`() {
        val userId = UUID.randomUUID()
        val communityId = UUID.randomUUID()
        every { flairRepo.getUserFlair(userId, communityId) } returns null

        val result = service.getUserFlair(userId, communityId)

        assertNull(result)
    }

    private fun flair(
        id: UUID = UUID.randomUUID(),
        communityId: UUID = UUID.randomUUID(),
        name: String = "Test Flair",
        color: String = "#666666",
        type: FlairType = FlairType.POST,
    ) = Flair(id = id, communityId = communityId, name = name, color = color, type = type)
}
