package dev.outerstellar.platform

import dev.outerstellar.platform.infra.createRenderer
import dev.outerstellar.platform.web.FooterStatusFragment
import kotlin.test.Test
import kotlin.test.assertTrue

class TestRender {
    @Test
    fun `can render footer status fragment`() {
        val renderer = createRenderer()
        val fragment = FooterStatusFragment("Everything is fine!")
        val result = renderer(fragment)

        val expectedPart = "Everything is fine!"
        assertTrue(result.contains(expectedPart), "Result should contain: $expectedPart")
    }
}
