package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class JooqUserRepositoryStatsTest : JooqTest() {

    private val repo by lazy { JooqUserRepository(dsl) }

    private fun insertUser(username: String, createdAt: LocalDateTime) {
        val id = UUID.randomUUID()
        repo.save(
            User(
                id = id,
                username = username,
                email = "$username@example.com",
                passwordHash = "hash_$username",
                role = UserRole.USER,
            )
        )
        dsl.execute(
            "UPDATE plt_users SET created_at = ? WHERE id = ?",
            java.sql.Timestamp.from(createdAt.toInstant(ZoneOffset.UTC)),
            id,
        )
    }

    @Test
    fun `countUsersSince returns only users created after cutoff`() {
        val oldDate = LocalDateTime.of(2024, 1, 1, 0, 0)
        val recentDate = LocalDateTime.of(2025, 5, 1, 0, 0)
        val cutoff = LocalDateTime.of(2025, 4, 1, 0, 0)

        insertUser("stats_old1", oldDate)
        insertUser("stats_old2", oldDate.plusDays(10))
        insertUser("stats_recent1", recentDate)
        insertUser("stats_recent2", recentDate.plusDays(5))

        assertEquals(2L, repo.countUsersSince(cutoff))
    }

    @Test
    fun `countUsersSince returns zero when all users are older`() {
        insertUser("stats_ancient", LocalDateTime.of(2020, 1, 1, 0, 0))

        assertEquals(0L, repo.countUsersSince(LocalDateTime.of(2025, 1, 1, 0, 0)))
    }

    @Test
    fun `countUsersSince returns all users when cutoff is in the past`() {
        insertUser("stats_user1", LocalDateTime.of(2024, 6, 1, 0, 0))
        insertUser("stats_user2", LocalDateTime.of(2024, 8, 1, 0, 0))

        assertEquals(2L, repo.countUsersSince(LocalDateTime.of(2023, 1, 1, 0, 0)))
    }

    @Test
    fun `countUsersSince with empty table returns zero`() {
        assertEquals(0L, repo.countUsersSince(LocalDateTime.of(2020, 1, 1, 0, 0)))
    }

    @Test
    fun `countUsersSince includes user created exactly at cutoff`() {
        val cutoff = LocalDateTime.of(2025, 5, 1, 12, 0)
        insertUser("stats_exact", cutoff)

        assertEquals(1L, repo.countUsersSince(cutoff))
    }

    @Test
    fun `countAll returns total user count`() {
        insertUser("stats_count1", LocalDateTime.of(2024, 1, 1, 0, 0))
        insertUser("stats_count2", LocalDateTime.of(2025, 1, 1, 0, 0))
        insertUser("stats_count3", LocalDateTime.of(2025, 5, 1, 0, 0))

        assertEquals(3L, repo.countAll())
    }
}
