package dev.outerstellar.starter

import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.web.FooterStatusFragment
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
