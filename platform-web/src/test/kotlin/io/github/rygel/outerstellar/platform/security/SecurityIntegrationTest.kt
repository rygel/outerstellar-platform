package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.web.WebTest
import io.github.rygel.outerstellar.platform.web.testPassword
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SecurityIntegrationTest : WebTest() {

    private lateinit var localUserRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authService: AuthService

    @BeforeEach
    fun setupTest() {
        localUserRepository = userRepository
        passwordEncoder = BCryptPasswordEncoder(logRounds = 4)
        authService =
            AuthService(
                userRepository = localUserRepository,
                passwordEncoder = passwordEncoder,
                config = SecurityConfig(),
                totpService = TOTPService(),
            )
    }

    @Test
    fun `should register and then authenticate user correctly`() {
        val username = "testuser"
        val password = testPassword()

        val newUser =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = "test@example.com",
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        localUserRepository.save(newUser)

        val result = authService.authenticate(username, password)

        assertTrue(result is AuthResult.Authenticated, "Should authenticate successfully")
        val auth = result as AuthResult.Authenticated
        assertEquals(username, auth.user.username)
        assertEquals(UserRole.USER, auth.user.role)
    }

    @Test
    fun `should fail authentication with wrong password`() {
        val username = "secureuser"
        val password = "correctpassword"

        localUserRepository.save(
            User(
                id = UUID.randomUUID(),
                username = username,
                email = "secure@example.com",
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        )

        val result = authService.authenticate(username, "wrongpassword")

        assertNull(result)
    }

    @Test
    fun `should fail authentication for non-existent user`() {
        val result = authService.authenticate("ghost", "anypassword")
        assertNull(result)
    }
}
