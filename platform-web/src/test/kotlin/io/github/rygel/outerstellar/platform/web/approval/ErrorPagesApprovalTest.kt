package io.github.rygel.outerstellar.platform.web.approval

import io.github.rygel.outerstellar.platform.web.WebTest
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ApprovalTest::class)
class ErrorPagesApprovalTest : WebTest() {

    private val app by lazy { buildApp() }

    private val csrfTokenPattern = Regex("""name="csrf-token" content="[^"]+"""")
    private val cacheBusterPattern = Regex("""\?v=\d+""")

    private fun normalize(response: Response): Response {
        val normalized =
            response
                .bodyString()
                .replace(csrfTokenPattern, """name="csrf-token" content="[CSRF-TOKEN]"""")
                .replace(cacheBusterPattern, "?v=[CACHE-BUSTER]")
        return response.body(normalized)
    }

    @Test
    fun `not-found error page`(approver: Approver) {
        approver.assertApproved(normalize(app(Request(GET, "/errors/not-found"))))
    }

    @Test
    fun `server-error page`(approver: Approver) {
        approver.assertApproved(normalize(app(Request(GET, "/errors/server-error"))))
    }

    @Test
    fun `forbidden page`(approver: Approver) {
        approver.assertApproved(normalize(app(Request(GET, "/errors/forbidden"))))
    }
}
