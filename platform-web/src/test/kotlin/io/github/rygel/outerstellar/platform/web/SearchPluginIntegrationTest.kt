package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import io.github.rygel.outerstellar.platform.PluginMigrationSource
import io.github.rygel.outerstellar.platform.search.SearchProvider
import io.github.rygel.outerstellar.platform.search.SearchResult
import kotlin.test.Test
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus

class SearchPluginIntegrationTest : WebTest() {

    private class StubSearchProvider(override val type: String, private val results: List<SearchResult>) :
        SearchProvider {
        override fun search(query: String, limit: Int): List<SearchResult> = results
    }

    private class TestSearchPlugin(private val providers: List<SearchProvider>) :
        PlatformPlugin, PluginMigrationSource {
        override val id: String = "test-search-plugin"

        fun searchProviders(context: PluginContext): List<SearchProvider> = providers
    }

    private val pluginResult =
        SearchResult(
            id = "plugin-item-1",
            title = "Plugin Document",
            subtitle = "Found by plugin",
            url = "/plugin/page/1",
            type = "plugin-doc",
            score = 0.95,
        )

    @Test
    fun `plugin search results appear on search page`() {
        val provider = StubSearchProvider("plugin-doc", listOf(pluginResult))
        val plugin = TestSearchPlugin(listOf(provider))
        val app = buildApp(plugin = plugin)

        val (token, _, _) = withAuthenticatedUser()
        val response = app(Request(Method.GET, "/search?q=plugin").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("Plugin Document"))
        assertThat(body, containsSubstring("/plugin/page/1"))
    }

    @Test
    fun `plugin search results appear in JSON API`() {
        val provider = StubSearchProvider("plugin-doc", listOf(pluginResult))
        val plugin = TestSearchPlugin(listOf(provider))
        val app = buildApp(plugin = plugin)

        val (token, _, _) = withAuthenticatedUser()
        val response =
            app(Request(Method.GET, "/api/v1/search?q=plugin").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("Plugin Document"))
        assertThat(body, containsSubstring("plugin-doc"))
        assertThat(body, containsSubstring("/plugin/page/1"))
    }

    @Test
    fun `plugin search results merge with built-in providers`() {
        val provider = StubSearchProvider("plugin-doc", listOf(pluginResult))
        val plugin = TestSearchPlugin(listOf(provider))
        val app = buildApp(plugin = plugin)

        val (token, _, _) = withAuthenticatedUser()
        val response =
            app(Request(Method.GET, "/api/v1/search?q=test").cookie(Cookie(RequestContext.SESSION_COOKIE, token)))

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("Plugin Document"))
    }

    @Test
    fun `search returns unauthorized for unauthenticated API request`() {
        val provider = StubSearchProvider("plugin-doc", listOf(pluginResult))
        val plugin = TestSearchPlugin(listOf(provider))
        val app = buildApp(plugin = plugin)

        val response = app(Request(Method.GET, "/api/v1/search?q=plugin"))

        assertThat(response, hasStatus(Status.UNAUTHORIZED))
    }
}
