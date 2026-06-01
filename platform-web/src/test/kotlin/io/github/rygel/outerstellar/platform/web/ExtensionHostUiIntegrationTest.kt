package io.github.rygel.outerstellar.platform.web

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.containsSubstring
import io.github.rygel.outerstellar.platform.composition.PlatformMode
import io.github.rygel.outerstellar.platform.composition.RouteGroup
import io.github.rygel.outerstellar.platform.extension.ExtensionHostContext
import io.github.rygel.outerstellar.platform.extension.ExtensionRouteRegistration
import io.github.rygel.outerstellar.platform.web.composition.PlatformPageSets
import kotlin.test.Test
import org.http4k.contract.bindContract
import org.http4k.contract.meta
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.hamkrest.hasStatus

class ExtensionHostUiIntegrationTest : WebTest() {
    private fun repoQualityExtension(
        includedPages: Set<PlatformPageSets> = emptySet(),
        homePath: String = "/repoquality",
    ): PlatformExtension =
        object : PlatformExtension {
            override val id: String = "repoquality"
            override val appLabel: String = "RepoQuality"
            override val mode: PlatformMode = PlatformMode.ExtensionHost

            override fun includePlatformPages(): Set<PlatformPageSets> = includedPages

            override fun routeRegistrations(context: ExtensionHostContext): List<ExtensionRouteRegistration> {
                val route =
                    homePath meta
                        {
                            summary = "RepoQuality home"
                        } bindContract
                        GET to
                        { request ->
                            val shell = request.shellRenderer.shell("RepoQuality", homePath)
                            Response(Status.OK)
                                .header("content-type", "text/html; charset=utf-8")
                                .body(context.renderShell(shell, """<main id="extension-home">RepoQuality</main>"""))
                        }
                return listOf(
                    ExtensionRouteRegistration(
                        route = route,
                        group = RouteGroup.ProtectedUi,
                        description = "RepoQuality home",
                        pathPattern = homePath,
                    )
                )
            }
        }

    @Test
    fun `extension hosted mode can own root ui by default`() {
        val app = buildApp(extension = repoQualityExtension(homePath = "/"))
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val authenticated = Cookie(RequestContext.SESSION_COOKIE, token)

        val response =
            app(Request(GET, "/").cookie(authenticated).cookie(Cookie(RequestContext.SHELL_COOKIE, "topbar")))

        assertThat(response, hasStatus(Status.OK))
        assertThat(app(Request(GET, "/repoquality").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        val body = response.bodyString()
        assertThat(body, containsSubstring("""id="extension-home""""))
        assertThat(body, containsSubstring("""href="/""""))
        assertThat(body, !containsSubstring("""href="/repoquality""""))
    }

    @Test
    fun `extension hosted mode does not mount default platform ui by default`() {
        val app = buildApp(extension = repoQualityExtension())
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val authenticated = Cookie(RequestContext.SESSION_COOKIE, token)

        assertThat(app(Request(GET, "/repoquality").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/settings").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/contacts").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/search").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/notifications").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/messages/trash").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/auth/profile").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/admin/users").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/admin/dev").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
    }

    @Test
    fun `extension hosted shell removes default platform chrome links`() {
        val app = buildApp(extension = repoQualityExtension())
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val response =
            app(
                Request(GET, "/repoquality")
                    .cookie(Cookie(RequestContext.SESSION_COOKIE, token))
                    .cookie(Cookie(RequestContext.SHELL_COOKIE, "topbar"))
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("""id="extension-home""""))
        assertThat(body, containsSubstring("RepoQuality"))
        assertThat(body, containsSubstring("""href="/repoquality""""))
        assertThat(body, !containsSubstring("""href="/settings""""))
        assertThat(body, !containsSubstring("""href="/contacts""""))
        assertThat(body, !containsSubstring("""href="/search""""))
        assertThat(body, !containsSubstring("""action="/search""""))
        assertThat(body, !containsSubstring("""href="/notifications""""))
        assertThat(body, !containsSubstring("""href="/auth/profile""""))
        assertThat(body, !containsSubstring("""href="/auth/change-password""""))
        assertThat(body, !containsSubstring("""href="/admin/users""""))
    }

    @Test
    fun `extension hosted shell only exposes opted in platform links`() {
        val app =
            buildApp(
                extension =
                    repoQualityExtension(
                        includedPages =
                            setOf(
                                PlatformPageSets.HOME,
                                PlatformPageSets.SEARCH,
                                PlatformPageSets.SETTINGS,
                                PlatformPageSets.NOTIFICATIONS,
                                PlatformPageSets.PROFILE,
                                PlatformPageSets.ADMIN,
                            )
                    )
            )
        val (token, _, _) = withAuthenticatedUser(role = "ADMIN")
        val authenticated = Cookie(RequestContext.SESSION_COOKIE, token)

        val response =
            app(
                Request(GET, "/repoquality").cookie(authenticated).cookie(Cookie(RequestContext.SHELL_COOKIE, "topbar"))
            )

        assertThat(response, hasStatus(Status.OK))
        val body = response.bodyString()
        assertThat(body, containsSubstring("href=\"/\""))
        assertThat(body, containsSubstring("""href="/messages/trash""""))
        assertThat(body, containsSubstring("""href="/search""""))
        assertThat(body, containsSubstring("""action="/search""""))
        assertThat(body, containsSubstring("""href="/settings""""))
        assertThat(body, containsSubstring("""id="notification-bell""""))
        assertThat(body, containsSubstring("""hx-get="/components/notification-bell""""))
        assertThat(body, containsSubstring("""href="/auth/profile""""))
        assertThat(body, containsSubstring("""href="/admin/users""""))
        assertThat(body, !containsSubstring("""href="/contacts""""))
        assertThat(app(Request(GET, "/settings").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/search").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/notifications").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/auth/profile").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/admin/users").cookie(authenticated)), hasStatus(Status.OK))
        assertThat(app(Request(GET, "/contacts").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
        assertThat(app(Request(GET, "/admin/dev").cookie(authenticated)), hasStatus(Status.NOT_FOUND))
    }
}
