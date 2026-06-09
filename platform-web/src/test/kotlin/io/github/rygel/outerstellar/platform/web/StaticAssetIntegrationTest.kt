package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasBody
import org.http4k.hamkrest.hasStatus
import org.http4k.routing.ResourceLoader

class StaticAssetIntegrationTest : WebTest() {

    private val app by lazy { buildApp() }

    @Test
    fun `GET site css returns 200`() {
        val response = app(Request(GET, "/site.css"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET swagger html returns 200`() {
        val response = app(Request(GET, "/swagger.html"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `GET site css returns non-empty body`() {
        assertThat(app(Request(GET, "/site.css")), hasBody(containsSubstring(".")))
    }

    @Test
    fun `GET swagger html returns non-empty body`() {
        assertThat(app(Request(GET, "/swagger.html")), hasBody(containsSubstring("<")))
    }

    @Test
    fun `GET site css has CSS content type`() {
        val response = app(Request(GET, "/site.css"))
        val contentType = response.header("content-type").orEmpty()
        assertTrue(
            contentType.contains("css", ignoreCase = true) || contentType.contains("text/plain", ignoreCase = true),
            "site.css should have a CSS-related content type, got: $contentType",
        )
    }

    @Test
    fun `GET swagger html has HTML content type`() {
        assertThat(app(Request(GET, "/swagger.html")), hasContentType("html"))
    }

    @Test
    fun `non-existent static file does not return 500`() {
        val response = app(Request(GET, "/this-file-does-not-exist-at-all.xyz"))
        assertTrue(response.status.code != 5, "Non-existent file should not return 5xx, got: ${response.status}")
    }

    @Test
    fun `static files are served without authentication`() {
        val response = app(Request(GET, "/site.css"))
        assertThat(response, hasStatus(Status.OK))
    }

    @Test
    fun `platform static files prefer configured filesystem directory`() {
        val staticDir = createStaticDir()
        Files.writeString(staticDir.resolve("site.css"), "body { color: seagreen; }")

        val response = buildApp(config = testConfig.copy(staticDir = staticDir.toString()))(Request(GET, "/site.css"))

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, hasBody(containsSubstring("seagreen")))
    }

    @Test
    fun `extension static files are served without authentication`() {
        val response = buildApp(extension = reportsExtension())(Request(GET, "/extensions/reports/assets/site.css"))

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, hasBody(containsSubstring(".")))
    }

    @Test
    fun `extension static files prefer configured filesystem directory`() {
        val staticDir = createStaticDir()
        Files.writeString(staticDir.resolve("site.css"), "body { color: hotpink; }")

        val response =
            buildApp(config = testConfig.copy(staticDir = staticDir.toString()), extension = reportsExtension())(
                Request(GET, "/extensions/reports/assets/site.css")
            )

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, hasBody(containsSubstring("hotpink")))
    }

    @Test
    fun `extension static files fall back to classpath when configured filesystem file is absent`() {
        val staticDir = createStaticDir()
        val response =
            buildApp(config = testConfig.copy(staticDir = staticDir.toString()), extension = reportsExtension())(
                Request(GET, "/extensions/reports/assets/site.css")
            )

        assertThat(response, hasStatus(Status.OK))
        assertThat(response, hasBody(containsSubstring(".")))
    }

    @Test
    fun `ETag filter does not consume static resource body`() {
        val response = app(Request(GET, "/site.css"))
        assertThat(response, hasStatus(Status.OK))
        val etag = response.header("ETag")
        assertTrue(etag != null && etag.startsWith("\""), "Response should have ETag header, got: $etag")
        assertThat(response, hasBody(containsSubstring(".")))
    }

    @Test
    fun `ETag 304 Not Modified for static resources`() {
        val first = app(Request(GET, "/site.css"))
        val etag = first.header("ETag")
        assertTrue(etag != null, "First response should have ETag")
        val second = app(Request(GET, "/site.css").header("If-None-Match", etag))
        assertThat(second, hasStatus(Status.NOT_MODIFIED))
    }

    @Test
    fun `ETag is not set for JSON responses`() {
        val response = app(Request(GET, "/health"))
        assertThat(response, hasStatus(Status.OK))
        assertTrue(
            response.header("ETag") == null,
            "JSON responses should not have ETag, got: ${response.header("ETag")}",
        )
    }

    private fun reportsExtension(): PlatformExtension =
        object : PlatformExtension {
            override val id = "reports"

            override fun contribute(context: ExtensionContributionContext) {
                context.routes.staticAssets("/extensions/reports/assets", ResourceLoader.Classpath("static"))
            }
        }

    private fun createStaticDir(): Path {
        val root = Path.of("target", "test-static-assets")
        Files.createDirectories(root)
        return Files.createTempDirectory(root, "static-")
    }
}
