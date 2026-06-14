package io.github.rygel.outerstellar.platform.web

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathPatternMatcherTest {

    @Test
    fun `double star matches zero segments`() {
        assertTrue(PathPatternMatcher.matches("/map/**", "/map"))
    }

    @Test
    fun `double star matches one segment`() {
        assertTrue(PathPatternMatcher.matches("/map/**", "/map/europe"))
    }

    @Test
    fun `double star matches multiple segments`() {
        assertTrue(PathPatternMatcher.matches("/map/**", "/map/europe/france/paris"))
    }

    @Test
    fun `single star matches exactly one segment`() {
        assertTrue(PathPatternMatcher.matches("/api/*/users", "/api/v1/users"))
    }

    @Test
    fun `single star does not match multiple segments`() {
        assertFalse(PathPatternMatcher.matches("/api/*/users", "/api/v1/admin/users"))
    }

    @Test
    fun `star within segment matches characters`() {
        assertTrue(PathPatternMatcher.matches("/static/*.css", "/static/site.css"))
    }

    @Test
    fun `star within segment does not match across slash`() {
        assertFalse(PathPatternMatcher.matches("/static/*.css", "/static/sub/site.css"))
    }

    @Test
    fun `exact match without wildcards`() {
        assertTrue(PathPatternMatcher.matches("/login", "/login"))
    }

    @Test
    fun `exact match does not match longer path`() {
        assertFalse(PathPatternMatcher.matches("/login", "/login/callback"))
    }

    @Test
    fun `pattern does not match different prefix`() {
        assertFalse(PathPatternMatcher.matches("/map/**", "/api/map"))
    }

    @Test
    fun `root double star matches everything`() {
        assertTrue(PathPatternMatcher.matches("/**", "/anything/deep/path"))
    }

    @Test
    fun `case sensitive matching`() {
        assertFalse(PathPatternMatcher.matches("/Map/**", "/map"))
    }
}
