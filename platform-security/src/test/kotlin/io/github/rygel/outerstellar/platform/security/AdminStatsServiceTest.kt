package io.github.rygel.outerstellar.platform.security

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach

class AdminStatsServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var service: AdminStatsService

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        service = AdminStatsService(userRepository)
    }

    @Test
    fun `newUsersLast30Days delegates to countUsersSince with 30-day cutoff`() {
        every { userRepository.countUsersSince(any()) } returns 5L

        val result = service.newUsersLast30Days()

        assertEquals(5L, result)
        verify {
            userRepository.countUsersSince(
                match { cutoff ->
                    val expectedCutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(30)
                    cutoff.isAfter(expectedCutoff.minusSeconds(2)) && cutoff.isBefore(expectedCutoff.plusSeconds(2))
                }
            )
        }
    }

    @Test
    fun `newUsersLast30Days returns zero when no recent users`() {
        every { userRepository.countUsersSince(any()) } returns 0L

        assertEquals(0L, service.newUsersLast30Days())
    }

    @Test
    fun `totalUsers delegates to countAll`() {
        every { userRepository.countAll() } returns 42L

        val result = service.totalUsers()

        assertEquals(42L, result)
        verify { userRepository.countAll() }
    }

    @Test
    fun `totalUsers returns zero when no users exist`() {
        every { userRepository.countAll() } returns 0L

        assertEquals(0L, service.totalUsers())
    }
}
