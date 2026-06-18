package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.search.SearchProvider
import io.github.rygel.outerstellar.platform.search.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.http4k.core.Method.GET
import org.http4k.core.Request

class SearchPageFactoryTest {
    @Test
    fun `safeHref preserves http and https URLs`() {
        assertEquals("http://example.com/a", SearchPageFactory.safeHref("http://example.com/a"))
        assertEquals("HTTPS://Example.com/a", SearchPageFactory.safeHref("HTTPS://Example.com/a"))
    }

    @Test
    fun `safeHref preserves root-relative and fragment URLs`() {
        assertEquals("/contacts?q=x", SearchPageFactory.safeHref("/contacts?q=x"))
        assertEquals("#section", SearchPageFactory.safeHref("#section"))
    }

    @Test
    fun `safeHref neutralises executable schemes`() {
        assertEquals("#", SearchPageFactory.safeHref("javascript:alert(document.cookie)"))
        assertEquals("#", SearchPageFactory.safeHref("JaVaScRiPt:alert(1)"))
        assertEquals("#", SearchPageFactory.safeHref("data:text/html,<script>alert(1)</script>"))
        assertEquals("#", SearchPageFactory.safeHref("vbscript:msgbox(1)"))
    }

    @Test
    fun `safeHref neutralises executable schemes with surrounding whitespace`() {
        // Browsers strip leading/control whitespace before resolving a scheme.
        assertEquals("#", SearchPageFactory.safeHref("  javascript:alert(1)"))
        assertEquals("#", SearchPageFactory.safeHref("\tjavascript:alert(1)"))
    }

    @Test
    fun `safeHref returns placeholder for empty url`() {
        assertEquals("#", SearchPageFactory.safeHref(""))
        assertEquals("#", SearchPageFactory.safeHref("   "))
    }

    @Test
    fun `safeHref does not mistake a colon in the path for a scheme`() {
        assertEquals("/path/with:colon", SearchPageFactory.safeHref("/path/with:colon"))
        assertEquals("relative/path:colon", SearchPageFactory.safeHref("relative/path:colon"))
    }

    @Test
    fun `buildSearchPage neutralises a malicious provider's javascript url`() {
        // Regression guard for issue #517: a SearchProvider returning a javascript: URL must not reach the
        // rendered href unneutralised, even though the built-in providers currently emit only safe paths.
        val maliciousProvider =
            object : SearchProvider {
                override val type = "evil"

                override fun search(query: String, limit: Int): List<SearchResult> =
                    listOf(
                        SearchResult(
                            id = "1",
                            title = "click me",
                            subtitle = "",
                            url = "javascript:fetch('/api/v1/session')",
                            type = "evil",
                        )
                    )
            }
        val factory = SearchPageFactory()
        val page =
            factory.buildSearchPage(
                shellRenderer = shellRenderer(),
                query = "click",
                providers = listOf(maliciousProvider),
            )

        assertEquals(1, page.data.results.size)
        val renderedUrl = page.data.results.single().url
        assertEquals("#", renderedUrl, "javascript: URL must be neutralised before reaching the view model")
        assertFalse(renderedUrl.contains("javascript"), "Rendered URL must not contain 'javascript': $renderedUrl")
    }

    @Test
    fun `buildSearchPage preserves a safe https url from a provider`() {
        val provider =
            object : SearchProvider {
                override val type = "links"

                override fun search(query: String, limit: Int): List<SearchResult> =
                    listOf(SearchResult("1", "doc", "", "https://docs.example.com/guide", "links"))
            }
        val factory = SearchPageFactory()
        val page = factory.buildSearchPage(shellRenderer(), "doc", listOf(provider))

        assertEquals("https://docs.example.com/guide", page.data.results.single().url)
    }

    private fun shellRenderer(): ShellRenderer =
        // Minimal anonymous context: search rendering only needs i18n + the shell title/path, not a session.
        ShellRenderer(RequestContext(Request(GET, "/search")))
}
