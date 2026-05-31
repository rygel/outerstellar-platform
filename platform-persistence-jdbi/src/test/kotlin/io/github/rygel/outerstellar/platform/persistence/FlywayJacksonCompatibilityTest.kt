package io.github.rygel.outerstellar.platform.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class FlywayJacksonCompatibilityTest {

    @Test
    fun `flyway dependencies stay compatible with jackson consumers`() {
        val toolsObjectMapperClass = runCatching { Class.forName("tools.jackson.databind.ObjectMapper") }.getOrNull()

        if (toolsObjectMapperClass != null) {
            assertDoesNotThrow { toolsObjectMapperClass.getDeclaredConstructor().newInstance() }
        }

        assertDoesNotThrow { ObjectMapper().writeValueAsString(mapOf("status" to "ok")) }
    }
}
