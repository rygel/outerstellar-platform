# http4k Testing Overhaul Design

## Problem

The platform-web test suite (602 tests) has two categories of issues:

1. **Waste** — BCrypt hashing the same password ~60 times (~3s), `lateinit var` + `@BeforeEach` boilerplate, SecurityService 7-line construction repeated across 20+ files
2. **Weak assertions** — OR-chained `body.contains("Invalid") || body.contains("invalid")` that pass on unrelated text, smoke-test-level HTML checks (`contains("brand name")` on an entire rendered page), zero use of http4k testing modules

## Design

Four independent layers, each landing as a separate commit. Stop at any layer if it doesn't prove valuable.

### Layer 1 — Eliminate Waste (no new dependencies)

**BCrypt hash caching**
- Pre-compute `"$2a$04$..."` hash for the standard test password in `WebTest` companion object
- Replace per-test `encoder.encode(testPassword())` with `val testPasswordHash = WebTest.testPasswordHash`
- Saves ~50ms × 60+ tests = ~3s per test class

**`val app by lazy` instead of `lateinit var` + `@BeforeEach`**
- Replace `private lateinit var app: HttpHandler` + `app = buildApp()` in `@BeforeEach` with `private val app by lazy { buildApp() }`
- `buildApp()` is cheap (~1ms), so sharing within a test class is safe — cleanup is row-level DELETE, not app reconstruction
- Applies to ~30 test classes that don't need per-test app variation

**Extract helper methods to WebTest**
- `createSecurityService()` — the 6-parameter constructor repeated in 20+ files
- `withAuthenticatedUser(role = UserRole.USER): String` — creates user, saves to DB, creates session, returns the session cookie value string. Eliminates 15-line setup blocks
- `formPost(url, pairs)` — `Request(POST, url).header("content-type", "application/x-www-form-urlencoded").body(formBody(pairs))`

**Files changed:** WebTest.kt, Stubs.kt, ~30 test classes
**Risk:** Very low — pure refactoring, no new deps

### Layer 2 — hamkrest matchers (`http4k-testing-hamkrest`)

**Add dependency:** `http4k-testing-hamkrest` (test scope) to `platform-web/pom.xml`

**Core matchers used:**
- `hasStatus(OK)` — replaces `assertEquals(Status.OK, response.status)`
- `hasHeader("Location", "/path")` — replaces `assertEquals("/path", response.header("location"))`
- `hasBody("expected")` — for exact body matches
- Composable: `assertThat(response, hasStatus(OK).and(hasHeader("Content-Type", "text/html")))`

**Custom matchers in a new `HttpMatchers.kt` test utility:**
- `hasSessionCookie()` — checks Set-Cookie contains session token pattern
- `hasRedirect(path)` — checks status is FOUND and Location matches
- `hasCsrfToken()` — checks body contains `_csrf` hidden field
- `bodyContains(text)` — replaces `assertTrue(body.contains(...))` with a composable matcher

**Migration strategy:** Convert test classes incrementally. Old assertions still work — no forced migration.

**Example before/after:**
```kotlin
// Before
assertEquals(Status.OK, response.status)
val body = response.bodyString()
assertTrue(body.contains("contacts"))
assertTrue(body.contains("Page 1"))

// After
assertThat(response, hasStatus(OK).and(bodyContains("contacts")).and(bodyContains("Page 1")))
```

**Files changed:** platform-web/pom.xml, new HttpMatchers.kt, ~30 test classes converted
**Risk:** Low — additive, hamkrest is a thin assertion layer

### Layer 3 — Approval testing (`http4k-testing-approval`)

**Add dependency:** `http4k-testing-approval` (test scope) to `platform-web/pom.xml`

**What it does:** Snapshot HTML responses to `.approved` files. On test run, compares actual output against approved golden file. If they differ, writes `.actual` file for review. To accept a change, copy `.actual` to `.approved` and commit.

**Target tests (highest value):**

| Test | Current assertion | What snapshot catches |
|------|-------------------|-----------------------|
| HomePageEndToEndTest | `contains("Outerstellar Platform")` | Sidebar, nav, messages, layout, theme, a11y attributes |
| ContactsPaginationIntegrationTest | `contains("ri-arrow-left-s-line")` | Card grid, pagination controls, CSS classes, offsets |
| ErrorPagesIntegrationTest | `contains("<!DOCTYPE html>")` | Error page layout, theme, branding, links |
| AuthHtmlFlowIntegrationTest | `contains("password")` | Form fields, labels, actions, CSRF tokens |
| ComponentFragmentIntegrationTest | `split("<option").size - 1` | Selector options, HTMX attributes |

**Pattern:**
```kotlin
@ExtendWith(ApprovalTest::class)
class HomePageApprovalTest : WebTest() {
    @Test
    fun `home page matches approved snapshot`(approver: Approver) {
        val response = app(Request(GET, "/").cookie(sessionCookie()))
        approver.assertApproved(response, OK)
    }
}
```

**Approval files location:** `src/test/resources/approval/` alongside test classes.

**Important:** Approval tests are ADDITIONAL — they don't replace the existing integration tests. The existing tests keep their explicit assertions for specific behaviors (status codes, redirects, session handling). Approval tests catch template rendering regressions that the existing tests miss.

**Files changed:** platform-web/pom.xml, 5-8 new approval test classes, ~10 `.approved` golden files
**Risk:** Medium — new test type, requires understanding golden file workflow. But additive — existing tests untouched.

### Layer 4 — WebDriver + Chaos (`http4k-testing-webdriver`, `http4k-testing-chaos`)

**Add dependencies:** `http4k-testing-webdriver`, `http4k-testing-chaos` (test scope)

#### WebDriver

**What it gives:** JSoup-backed WebDriver — navigate HTML pages, click links, submit forms, read DOM — all in-memory, no browser needed.

**Target:** HTMX flow testing. Currently we test HTMX responses by checking HX-Redirect headers and fragment body strings. WebDriver lets us:
- Navigate to a page
- Find a form element
- Submit it
- Assert the rendered result

**Example:**
```kotlin
val driver = Http4kWebDriver(app)
driver.navigate().to("http://test/contacts")
driver.findElement(By.name("name")).sendKeys("Alice")
driver.findElement(By.tagName("form")).submit()
val result = driver.pageSource
// Assert the new contact appears in the rendered list
```

**Limitation:** No JavaScript execution. HTMX attributes (`hx-get`, `hx-post`) won't fire — we test the HTML structure and form actions, not the client-side interactivity. This is still valuable for catching broken forms, missing fields, and template regressions.

**Scope for this layer:** 2-3 WebDriver tests for the most critical flows (auth, contact CRUD). Prove the pattern works before expanding.

#### Chaos

**What it gives:** Inject failures (latency, exceptions, error status codes) into any `HttpHandler`.

**Target:** Error page rendering under failure conditions.
```kotlin
val chaosEngine = ChaosEngine(ReturnStatus(INTERNAL_SERVER_ERROR).appliedWhen(Always))
val chaoticApp = chaosEngine.then(app)
val response = chaoticApp(Request(GET, "/"))
// Assert custom error page renders correctly when backend fails
```

**Scope for this layer:** 1-2 chaos tests proving the app renders error pages correctly when downstream services fail.

**Files changed:** platform-web/pom.xml, 3-5 new test classes
**Risk:** Medium — new patterns, but isolated in new test files. No existing tests changed.

## What We're NOT Doing

- **Not replacing existing tests** — all layers are additive
- **Not adding Servirtium/TracerBullet** — too heavy for current needs
- **Not moving CleanupTables to test sources** — deferred (cross-module visibility issue documented in existing design docs)
- **Not changing test execution parallelism** — platform-web must stay sequential due to shared DB state
- **Not adding Strikt matchers** — hamkrest is the standard http4k matcher library

## Dependency Additions

All use the existing `http4k.version` property (6.47.0.0):

| Dependency | Scope | Layer | Size |
|------------|-------|-------|------|
| `http4k-testing-hamkrest` | test | 2 | ~31KB |
| `http4k-testing-approval` | test | 3 | ~55KB |
| `http4k-testing-webdriver` | test | 4 | ~59KB |
| `http4k-testing-chaos` | test | 4 | ~140KB |

hamkrest brings `org.hamcrest:hamcrest-all` as a transitive dependency. Approval testing brings JUnit 5 extensions. WebDriver brings JSoup. Chaos is self-contained.

## Success Metrics

- **Layer 1:** ~3s faster test execution per class with BCrypt. ~200 fewer lines of boilerplate.
- **Layer 2:** All status/header assertions use hamkrest matchers. Zero raw `assertEquals(Status.XXX, ...)` in test classes.
- **Layer 3:** 5+ HTML page templates have golden files catching any rendering regression.
- **Layer 4:** At least 1 WebDriver test and 1 chaos test proving the pattern works.

## Implementation Order

Layer 1 → Layer 2 → Layer 3 → Layer 4. Each layer is a single commit that compiles and passes all tests independently.
