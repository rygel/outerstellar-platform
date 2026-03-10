package dev.outerstellar.starter.security

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
import dev.outerstellar.starter.persistence.JooqUserRepository
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
class SecurityIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine").apply {
        withDatabaseName("securitytestdb")
        withUsername("test")
        withPassword("test")
    }

    private lateinit var userRepository: JooqUserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var securityService: SecurityService
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setup() {
        val dataSource = createDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrate(dataSource)
        
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES)
        userRepository = JooqUserRepository(dsl)
        passwordEncoder = BCryptPasswordEncoder(logRounds = 4) // Fast for tests
        securityService = SecurityService(userRepository, passwordEncoder)
    }

    @Test
    fun `should register and then authenticate user correctly`() {
        val username = "testuser"
        val password = "secretpassword"
        
        // 1. Register
        val newUser = User(
            id = UUID.randomUUID(),
            username = username,
            email = "test@example.com",
            passwordHash = passwordEncoder.encode(password),
            role = UserRole.USER
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
        
        userRepository.save(User(
            id = UUID.randomUUID(),
            username = username,
            email = "secure@example.com",
            passwordHash = passwordEncoder.encode(password),
            role = UserRole.USER
        ))
        
        val result = securityService.authenticate(username, "wrongpassword")
        
        assertNull(result)
    }

    @Test
    fun `should fail authentication for non-existent user`() {
        val result = securityService.authenticate("ghost", "anypassword")
        assertNull(result)
    }
}
