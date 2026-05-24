package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.UsernameAlreadyExistsException
import io.github.rygel.outerstellar.platform.model.WeakPasswordException
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.SessionRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows

class AccountServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var auditRepository: AuditRepository
    private lateinit var accountService: AccountService

    private val testUser =
        User(
            id = UUID.randomUUID(),
            username = "testuser",
            email = "testuser@test.com",
            passwordHash = "hashed_password",
            role = UserRole.USER,
        )

    private val adminUser =
        User(
            id = UUID.randomUUID(),
            username = "admin",
            email = "admin@test.com",
            passwordHash = "hashed_admin",
            role = UserRole.ADMIN,
        )

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        passwordEncoder = mockk(relaxed = true)
        auditRepository = mockk(relaxed = true)
        accountService =
            AccountService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                auditRepository = auditRepository,
            )
    }

    @Test
    fun `changePassword throws on wrong current password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("wrongcurrent", testUser.passwordHash) } returns false

        assertThrows<WeakPasswordException> { accountService.changePassword(testUser.id, "wrongcurrent", "Newp@ss1") }
    }

    @Test
    fun `changePassword updates hash on success`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("Newp@ss1") } returns "new_hash"

        accountService.changePassword(testUser.id, "currentpass", "Newp@ss1")

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("new_hash", userSlot.captured.passwordHash)
    }

    @Test
    fun `changePassword invalidates all sessions for the user`() {
        val sessionRepository: SessionRepository = mockk(relaxed = true)
        val serviceWithSessions =
            AccountService(
                userRepository = userRepository,
                passwordEncoder = passwordEncoder,
                sessionRepository = sessionRepository,
                auditRepository = auditRepository,
            )
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("Newp@ss1") } returns "new_hash"

        serviceWithSessions.changePassword(testUser.id, "currentpass", "Newp@ss1")

        verify { sessionRepository.deleteByUserId(testUser.id) }
    }

    @Test
    fun `changePassword works without session repository`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true
        every { passwordEncoder.encode("Newp@ss1") } returns "new_hash"

        accountService.changePassword(testUser.id, "currentpass", "Newp@ss1")

        val userSlot = slot<User>()
        verify { userRepository.save(capture(userSlot)) }
        assertEquals("new_hash", userSlot.captured.passwordHash)
    }

    @Test
    fun `changePassword throws on short new password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true

        assertThrows<WeakPasswordException> { accountService.changePassword(testUser.id, "currentpass", "short") }
    }

    @Test
    fun `changePassword throws on long new password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("currentpass", testUser.passwordHash) } returns true

        assertThrows<WeakPasswordException> {
            accountService.changePassword(testUser.id, "currentpass", "A".repeat(129))
        }
    }

    @Test
    fun `updateProfile updates email`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail("new@test.com") } returns null

        accountService.updateProfile(testUser.id, "new@test.com")

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertEquals("new@test.com", saved.captured.email)
    }

    @Test
    fun `updateProfile updates username when different`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null
        every { userRepository.findByUsername("newname") } returns null

        accountService.updateProfile(testUser.id, testUser.email, newUsername = "newname")

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertEquals("newname", saved.captured.username)
    }

    @Test
    fun `updateProfile throws when new username is already taken`() {
        val other = testUser.copy(id = UUID.randomUUID(), username = "taken")
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null
        every { userRepository.findByUsername("taken") } returns other

        assertThrows<UsernameAlreadyExistsException> {
            accountService.updateProfile(testUser.id, testUser.email, newUsername = "taken")
        }
    }

    @Test
    fun `updateProfile skips username update when same as current`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null

        accountService.updateProfile(testUser.id, testUser.email, newUsername = testUser.username)

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertEquals(testUser.username, saved.captured.username)
    }

    @Test
    fun `updateProfile updates avatar URL when changed`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { userRepository.findByEmail(testUser.email) } returns null

        accountService.updateProfile(testUser.id, testUser.email, newAvatarUrl = "https://example.com/avatar.png")

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertEquals("https://example.com/avatar.png", saved.captured.avatarUrl)
    }

    @Test
    fun `updateProfile clears avatar URL when blank`() {
        val userWithAvatar = testUser.copy(avatarUrl = "https://old.example.com/avatar.png")
        every { userRepository.findById(testUser.id) } returns userWithAvatar
        every { userRepository.findByEmail(testUser.email) } returns null

        accountService.updateProfile(testUser.id, testUser.email, newAvatarUrl = "")

        val saved = slot<User>()
        verify { userRepository.save(capture(saved)) }
        assertNull(saved.captured.avatarUrl)
    }

    @Test
    fun `deleteAccount removes the user`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("correctpass", testUser.passwordHash) } returns true

        accountService.deleteAccount(testUser.id, "correctpass")

        verify { userRepository.deleteById(testUser.id) }
    }

    @Test
    fun `deleteAccount blocks deleting the only admin`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { passwordEncoder.matches("correctpass", adminUser.passwordHash) } returns true
        every { userRepository.countByRole(UserRole.ADMIN) } returns 1L

        assertThrows<io.github.rygel.outerstellar.platform.model.InsufficientPermissionException> {
            accountService.deleteAccount(adminUser.id, "correctpass")
        }
        verify(exactly = 0) { userRepository.deleteById(any()) }
    }

    @Test
    fun `deleteAccount rejects wrong password`() {
        every { userRepository.findById(testUser.id) } returns testUser
        every { passwordEncoder.matches("wrongpass", testUser.passwordHash) } returns false

        assertThrows<io.github.rygel.outerstellar.platform.model.WeakPasswordException> {
            accountService.deleteAccount(testUser.id, "wrongpass")
        }
        verify(exactly = 0) { userRepository.deleteById(any()) }
    }

    @Test
    fun `deleteAccount allows admin deletion when another admin exists`() {
        every { userRepository.findById(adminUser.id) } returns adminUser
        every { passwordEncoder.matches("correctpass", adminUser.passwordHash) } returns true
        every { userRepository.countByRole(UserRole.ADMIN) } returns 2L

        accountService.deleteAccount(adminUser.id, "correctpass")

        verify { userRepository.deleteById(adminUser.id) }
    }

    @Test
    fun `updateNotificationPreferences persists both flags`() {
        every { userRepository.findById(testUser.id) } returns testUser

        accountService.updateNotificationPreferences(testUser.id, emailEnabled = false, pushEnabled = true)

        verify { userRepository.updateNotificationPreferences(testUser.id, false, true) }
    }

    @Test
    fun `updateNotificationPreferences disables all notifications`() {
        every { userRepository.findById(testUser.id) } returns testUser

        accountService.updateNotificationPreferences(testUser.id, emailEnabled = false, pushEnabled = false)

        verify { userRepository.updateNotificationPreferences(testUser.id, false, false) }
    }
}
