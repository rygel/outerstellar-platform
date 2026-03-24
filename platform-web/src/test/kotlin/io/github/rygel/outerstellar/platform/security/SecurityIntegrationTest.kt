package io.github.rygel.outerstellar.platform.security

import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.web.H2WebTest
import io.github.rygel.outerstellar.platform.web.testPassword
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SecurityIntegrationTest : H2WebTest() {

    private lateinit var userRepository: JooqUserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var securityService: SecurityService

    @BeforeEach
    fun setupTest() {
        userRepository = JooqUserRepository(testDsl)
        passwordEncoder = BCryptPasswordEncoder(logRounds = 4) // Fast for tests
        securityService = SecurityService(userRepository, passwordEncoder)
    }

    @AfterEach
    fun teardown() {
        cleanup()
    }

    @Test
    fun `should register and then authenticate user correctly`() {
        val username = "testuser"
        val password = testPassword()

        // 1. Register
        val newUser =
            User(
                id = UUID.randomUUID(),
                username = username,
                email = "test@example.com",
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        userRepository.save(newUser)

        // 2. Authenticate
        val authenticatedUser = securityService.authenticate(username, password)

        assertNotNull(authenticatedUser)
        assertEquals(username, authenticatedUser?.username)
        assertEquals(UserRole.USER, authenticatedUser?.role)
    }

    @Test
    fun `should fail authentication with wrong password`() {
        val username = "secureuser"
        val password = "correctpassword"

        userRepository.save(
            User(
                id = UUID.randomUUID(),
                username = username,
                email = "secure@example.com",
                passwordHash = passwordEncoder.encode(password),
                role = UserRole.USER,
            )
        )

        val result = securityService.authenticate(username, "wrongpassword")

        assertNull(result)
    }

    @Test
    fun `should fail authentication for non-existent user`() {
        val result = securityService.authenticate("ghost", "anypassword")
        assertNull(result)
    }
}
