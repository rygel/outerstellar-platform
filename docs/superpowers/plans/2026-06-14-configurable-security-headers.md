# Configurable Security Response Headers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all hard-coded security response headers (Permissions-Policy, X-Frame-Options, Referrer-Policy, X-Content-Type-Options, Strict-Transport-Security) configurable via YAML/env vars, with per-route overrides using Ant-style glob patterns — fixes #501.

**Architecture:** Add `SecurityHeadersConfig` data class to `AppConfig` in `platform-core`. Add `PathPatternMatcher` utility to `platform-web`. Update `Filters.securityHeaders` and `Filters.cors` to accept the config and resolve per-route overrides inside the filter (position 5 in the chain, so per-route always wins over extensions). Zero breaking changes — defaults match current hard-coded values.

**Tech Stack:** Kotlin, JUnit 5, http4k, SnakeYAML engine, Maven

---

## File Map

| File | Change |
|------|--------|
| `platform-web/src/main/kotlin/.../web/PathPatternMatcher.kt` | NEW — Ant-style glob path matcher |
| `platform-web/src/test/kotlin/.../web/PathPatternMatcherTest.kt` | NEW — exhaustive matching tests |
| `platform-core/src/main/kotlin/.../AppConfig.kt` | Add `SecurityHeadersConfig`, `RouteHeaderOverride`, `buildSecurityHeadersConfig`, env var parsing |
| `platform-core/src/test/kotlin/.../AppConfigTest.kt` | New env var parsing tests |
| `platform-web/src/main/kotlin/.../web/Filters.kt` | Update `securityHeaders` and `cors` signatures + per-route resolution |
| `platform-web/src/main/kotlin/.../web/assembly/FilterChainFactory.kt` | Pass `config.securityHeaders` to filters |
| `platform-web/src/test/kotlin/.../web/SecurityHeadersIntegrationTest.kt` | Add per-route override tests |
| `docs/configuration/security-headers.md` | NEW — all config options documented |
| `CHANGELOG.md` | Add entry under [Unreleased] |

---

## Task 1: PathPatternMatcher — Ant-style glob matching

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcher.kt`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcherTest.kt`

- [ ] **Step 1: Write failing tests for PathPatternMatcher**

Create `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcherTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the test to confirm it fails**

```powershell
mvn -pl platform-web compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: compilation failure — `PathPatternMatcher` does not yet exist.

- [ ] **Step 3: Implement PathPatternMatcher**

Create `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcher.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.web

object PathPatternMatcher {

    private val regexSpecial = setOf('.', '+', '?', '^', '$', '{', '}', '[', ']', '(', ')', '|', '\\', '-', '/')

    fun matches(pattern: String, path: String): Boolean {
        val patternSegments = pattern.split("/").filter { it.isNotEmpty() }
        val pathSegments = path.split("/").filter { it.isNotEmpty() }
        return matchSegments(patternSegments, 0, pathSegments, 0)
    }

    private fun matchSegments(pattern: List<String>, pi: Int, path: List<String>, si: Int): Boolean {
        if (pi >= pattern.size) return si >= path.size

        val segment = pattern[pi]

        if (segment == "**") {
            if (pi == pattern.size - 1) return true
            for (skip in si..path.size) {
                if (matchSegments(pattern, pi + 1, path, skip)) return true
            }
            return false
        }

        if (si >= path.size) return false

        if (matchSegment(segment, path[si])) {
            return matchSegments(pattern, pi + 1, path, si + 1)
        }

        return false
    }

    private fun matchSegment(pattern: String, segment: String): Boolean {
        if (pattern == "*") return true
        if (!pattern.contains("*")) return pattern == segment
        val regex = StringBuilder()
        for (c in pattern) {
            when {
                c == '*' -> regex.append(".*")
                c in regexSpecial -> regex.append("\\").append(c)
                else -> regex.append(c)
            }
        }
        return regex.toString().toRegex().matches(segment)
    }
}
```

- [ ] **Step 4: Run the tests to confirm they pass**

```powershell
mvn -pl platform-web test -Dtest=PathPatternMatcherTest "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`, all 12 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcher.kt `
        platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/PathPatternMatcherTest.kt
git commit -m "feat: add PathPatternMatcher for Ant-style glob path matching (#501)"
```

---

## Task 2: SecurityHeadersConfig + RouteHeaderOverride data classes

**Files:**
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`

- [ ] **Step 1: Add SecurityHeadersConfig and RouteHeaderOverride data classes**

In `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt`, add these two data classes AFTER the existing `PushNotificationConfig` class (around line 59) and BEFORE the `AppConfig` class:

```kotlin
data class SecurityHeadersConfig(
    val permissionsPolicy: String = DEFAULT_PERMISSIONS_POLICY,
    val referrerPolicy: String = "strict-origin-when-cross-origin",
    val xFrameOptions: String = "DENY",
    val xContentTypeOptions: String = "nosniff",
    val strictTransportSecurity: String = "max-age=31536000; includeSubDomains",
    val perRouteOverrides: List<RouteHeaderOverride> = emptyList(),
)

data class RouteHeaderOverride(
    val pattern: String,
    val permissionsPolicy: String? = null,
    val referrerPolicy: String? = null,
    val xFrameOptions: String? = null,
    val xContentTypeOptions: String? = null,
    val strictTransportSecurity: String? = null,
    val csp: String? = null,
    val corsAllowedOrigins: List<String>? = null,
)
```

Add the default constant near the existing `DEFAULT_CSP_POLICY` constant (around line 19):

```kotlin
const val DEFAULT_PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()"
```

Make it `const val` outside the class (top-level or in companion — follow the pattern of `DEFAULT_CSP_POLICY` which is top-level private at line 19). Since `SecurityHeadersConfig` needs to reference it as a default parameter, make it a top-level constant:

At the top of the file near line 19, add:

```kotlin
const val DEFAULT_PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()"
```

- [ ] **Step 2: Add securityHeaders field to AppConfig**

In the `AppConfig` data class constructor (around line 86, after `pushNotifications`), add:

```kotlin
    val securityHeaders: SecurityHeadersConfig = SecurityHeadersConfig(),
```

- [ ] **Step 3: Add buildSecurityHeadersConfig parser**

In the companion object of `AppConfig`, after the existing `buildPushNotificationConfig` method (around line 242), add:

```kotlin
        private fun buildSecurityHeadersConfig(
            yaml: Map<String, Any>?,
            env: Map<String, String>,
        ): SecurityHeadersConfig {
            if (yaml == null) return SecurityHeadersConfig()
            return SecurityHeadersConfig(
                permissionsPolicy = yaml.str(
                    "permissionsPolicy", env, "PERMISSIONS_POLICY", DEFAULT_PERMISSIONS_POLICY,
                ),
                referrerPolicy = yaml.str(
                    "referrerPolicy", env, "REFERRER_POLICY", "strict-origin-when-cross-origin",
                ),
                xFrameOptions = yaml.str("xFrameOptions", env, "X_FRAME_OPTIONS", "DENY"),
                xContentTypeOptions = yaml.str(
                    "xContentTypeOptions", env, "X_CONTENT_TYPE_OPTIONS", "nosniff",
                ),
                strictTransportSecurity = yaml.str(
                    "strictTransportSecurity",
                    env,
                    "STRICT_TRANSPORT_SECURITY",
                    "max-age=31536000; includeSubDomains",
                ),
                perRouteOverrides = buildPerRouteOverrides(
                    yaml["perRouteOverrides"],
                ),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun buildPerRouteOverrides(raw: Any?): List<RouteHeaderOverride> {
            val list = raw as? List<*> ?: return emptyList()
            return list.mapNotNull { entry ->
                val map = entry as? Map<String, Any> ?: return@mapNotNull null
                RouteHeaderOverride(
                    pattern = map["pattern"] as? String ?: "",
                    permissionsPolicy = map["permissionsPolicy"] as? String,
                    referrerPolicy = map["referrerPolicy"] as? String,
                    xFrameOptions = map["xFrameOptions"] as? String,
                    xContentTypeOptions = map["xContentTypeOptions"] as? String,
                    strictTransportSecurity = map["strictTransportSecurity"] as? String,
                    csp = map["csp"] as? String,
                    corsAllowedOrigins = (map["corsAllowedOrigins"] as? List<*>)?.map { it.toString() },
                )
            }
        }
```

Note: The `@Suppress("UNCHECKED_CAST")` annotation is needed because SnakeYAML returns `List<Any>` for YAML lists, and we cast entries to `Map<String, Any>`.

- [ ] **Step 4: Wire buildSecurityHeadersConfig into buildFromYaml**

In the `buildFromYaml` method, add the `securityHeaders` parameter to the `AppConfig(...)` constructor call (around line 178, after `pushNotifications = ...`):

```kotlin
                securityHeaders = buildSecurityHeadersConfig(
                    yaml["securityHeaders"] as? Map<String, Any>, env,
                ),
```

- [ ] **Step 5: Compile to verify no errors**

```powershell
mvn -pl platform-core compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```powershell
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/AppConfig.kt
git commit -m "feat: add SecurityHeadersConfig with per-route overrides to AppConfig (#501)"
```

---

## Task 3: AppConfig env var parsing tests

**Files:**
- Modify: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/AppConfigTest.kt`

- [ ] **Step 1: Write tests for new env var parsing**

Add these tests inside `class AppConfigTest`:

```kotlin
    @Test
    fun `permissions policy is set from PERMISSIONS_POLICY env var`() {
        val config = AppConfig.fromEnvironment(mapOf("PERMISSIONS_POLICY" to "geolocation=(self)"))
        assert(config.securityHeaders.permissionsPolicy == "geolocation=(self)") {
            "Expected geolocation=(self) but was ${config.securityHeaders.permissionsPolicy}"
        }
    }

    @Test
    fun `permissions policy defaults to camera microphone geolocation`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assert(config.securityHeaders.permissionsPolicy == "camera=(), microphone=(), geolocation=()")
    }

    @Test
    fun `x frame options is set from X_FRAME_OPTIONS env var`() {
        val config = AppConfig.fromEnvironment(mapOf("X_FRAME_OPTIONS" to "SAMEORIGIN"))
        assert(config.securityHeaders.xFrameOptions == "SAMEORIGIN")
    }

    @Test
    fun `referrer policy is set from REFERRER_POLICY env var`() {
        val config = AppConfig.fromEnvironment(mapOf("REFERRER_POLICY" to "no-referrer"))
        assert(config.securityHeaders.referrerPolicy == "no-referrer")
    }

    @Test
    fun `strict transport security is set from env var`() {
        val config = AppConfig.fromEnvironment(mapOf("STRICT_TRANSPORT_SECURITY" to "max-age=999"))
        assert(config.securityHeaders.strictTransportSecurity == "max-age=999")
    }

    @Test
    fun `security headers defaults match current hard-coded values`() {
        val config = AppConfig.fromEnvironment(emptyMap())
        assert(config.securityHeaders.xContentTypeOptions == "nosniff")
        assert(config.securityHeaders.xFrameOptions == "DENY")
        assert(config.securityHeaders.referrerPolicy == "strict-origin-when-cross-origin")
        assert(config.securityHeaders.strictTransportSecurity == "max-age=31536000; includeSubDomains")
        assert(config.securityHeaders.perRouteOverrides.isEmpty())
    }
```

- [ ] **Step 2: Run the tests**

```powershell
mvn -pl platform-core test -Dtest=AppConfigTest "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`, all new tests pass.

- [ ] **Step 3: Commit**

```powershell
git add platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/AppConfigTest.kt
git commit -m "test: add SecurityHeadersConfig env var parsing tests (#501)"
```

---

## Task 4: Update Filters.securityHeaders for configurable headers + per-route overrides

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt`

- [ ] **Step 1: Update the securityHeaders filter signature and implementation**

In `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt`, replace the entire `securityHeaders` function (lines 194-212) with:

```kotlin
    fun securityHeaders(
        cspPolicy: String = DEFAULT_CSP_POLICY,
        headerConfig: SecurityHeadersConfig = SecurityHeadersConfig(),
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val cspNonce = generateCspNonce()
            val requestWithNonce = request.with(CSP_NONCE_KEY of cspNonce)
            val response = next(requestWithNonce)
            val path = request.uri.path
            val override = headerConfig.findOverride(path)
            val effectivePermissionsPolicy = override?.permissionsPolicy ?: headerConfig.permissionsPolicy
            val effectiveReferrerPolicy = override?.referrerPolicy ?: headerConfig.referrerPolicy
            val effectiveFrameOptions = override?.xFrameOptions ?: headerConfig.xFrameOptions
            val effectiveContentTypeOptions = override?.xContentTypeOptions ?: headerConfig.xContentTypeOptions
            val effectiveHsts = override?.strictTransportSecurity ?: headerConfig.strictTransportSecurity
            response
                .header("X-Content-Type-Options", effectiveContentTypeOptions)
                .header("X-Frame-Options", effectiveFrameOptions)
                .header("Referrer-Policy", effectiveReferrerPolicy)
                .header("Permissions-Policy", effectivePermissionsPolicy)
                .let { resp ->
                    if (effectiveHsts.isNotBlank()) {
                        resp.header("Strict-Transport-Security", effectiveHsts)
                    } else {
                        resp
                    }
                }.let { resp ->
                    if (!path.startsWith("/api/")) {
                        val effectiveCsp = override?.csp ?: cspPolicy
                        resp.header("Content-Security-Policy", effectiveCsp.withCspNonce(cspNonce))
                    } else {
                        resp
                    }
                }
        }
    }
```

- [ ] **Step 2: Add the import for SecurityHeadersConfig**

At the top of `Filters.kt`, the import for `SecurityHeadersConfig` is not needed because it's in the same package (`io.github.rygel.outerstellar.platform` is in platform-core, and `Filters.kt` is in `io.github.rygel.outerstellar.platform.web`). Add this import:

```kotlin
import io.github.rygel.outerstellar.platform.SecurityHeadersConfig
import io.github.rygel.outerstellar.platform.RouteHeaderOverride
```

These go alongside the existing imports from `io.github.rygel.outerstellar.platform.*` (lines 3-7).

- [ ] **Step 3: Add findOverride extension function**

Add this function at the file level (after the `Filters` object or before it, near the other private functions):

```kotlin
fun SecurityHeadersConfig.findOverride(path: String): RouteHeaderOverride? =
    perRouteOverrides.firstOrNull { PathPatternMatcher.matches(it.pattern, path) }
```

- [ ] **Step 4: Compile to verify**

```powershell
mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```powershell
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git commit -m "feat: make securityHeaders filter accept SecurityHeadersConfig with per-route overrides (#501)"
```

---

## Task 5: Update Filters.cors for per-route CORS overrides

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt`

- [ ] **Step 1: Update the cors filter to support per-route origins**

In `Filters.kt`, replace the `cors` function (lines 176-192) with:

```kotlin
    fun cors(
        allowedOrigins: String,
        headerConfig: SecurityHeadersConfig = SecurityHeadersConfig(),
    ): Filter = Filter { next: HttpHandler ->
        { request ->
            val path = request.uri.path
            val override = headerConfig.findOverride(path)
            val effectiveOrigins = override?.corsAllowedOrigins?.joinToString(", ") ?: allowedOrigins
            if (effectiveOrigins.isBlank()) return@Filter next(request)
            if (request.method == org.http4k.core.Method.OPTIONS) {
                Response(Status.NO_CONTENT)
                    .header("Access-Control-Allow-Origin", effectiveOrigins)
                    .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id")
                    .header("Access-Control-Max-Age", "3600")
            } else {
                val response = next(request)
                response
                    .header("Access-Control-Allow-Origin", effectiveOrigins)
                    .header("Access-Control-Expose-Headers", "X-Request-Id, X-Session-Expired")
            }
        }
    }
```

- [ ] **Step 2: Compile to verify**

```powershell
mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/Filters.kt
git commit -m "feat: make cors filter support per-route CORS origin overrides (#501)"
```

---

## Task 6: Update FilterChainFactory to pass SecurityHeadersConfig

**Files:**
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/assembly/FilterChainFactory.kt`

- [ ] **Step 1: Update the two filter calls in build()**

In `FilterChainFactory.kt`, change line 31 from:

```kotlin
                .then(Filters.cors(config.corsOrigins))
```

to:

```kotlin
                .then(Filters.cors(config.corsOrigins, config.securityHeaders))
```

And change line 34 from:

```kotlin
                .then(Filters.securityHeaders(config.cspPolicy))
```

to:

```kotlin
                .then(Filters.securityHeaders(config.cspPolicy, config.securityHeaders))
```

- [ ] **Step 2: Compile and run existing security header tests**

```powershell
mvn -pl platform-web -am compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```powershell
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/assembly/FilterChainFactory.kt
git commit -m "feat: wire SecurityHeadersConfig into filter chain (#501)"
```

---

## Task 7: Integration tests for per-route overrides

**Files:**
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SecurityHeadersIntegrationTest.kt`

- [ ] **Step 1: Write failing tests for per-route header overrides**

Add these tests to `SecurityHeadersIntegrationTest.kt` inside the class, before the closing brace:

```kotlin
    // ---- Per-route overrides ----

    @Test
    fun `per-route override changes Permissions-Policy for matching path`() {
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                perRouteOverrides = listOf(
                    RouteHeaderOverride(
                        pattern = "/map/**",
                        permissionsPolicy = "geolocation=(self), camera=(), microphone=()",
                    ),
                ),
            ),
        )
        val mapApp = buildApp(config = config)

        val response = mapApp(Request(GET, "/map/europe"))
        val header = response.header("Permissions-Policy")
        assertNotNull(header, "Permissions-Policy should be present")
        assertTrue(header.contains("geolocation=(self)"), "Expected geolocation=(self) but was $header")
    }

    @Test
    fun `per-route override does not affect non-matching path`() {
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                perRouteOverrides = listOf(
                    RouteHeaderOverride(
                        pattern = "/map/**",
                        permissionsPolicy = "geolocation=(self), camera=(), microphone=()",
                    ),
                ),
            ),
        )
        val mapApp = buildApp(config = config)

        val response = mapApp(Request(GET, "/auth"))
        val header = response.header("Permissions-Policy")
        assertNotNull(header)
        assertTrue(header.contains("geolocation=()"), "Non-matching path should have default geolocation=() but was $header")
    }

    @Test
    fun `per-route override changes CSP and nonce still works`() {
        val mapCsp = "default-src 'self'; script-src 'self' {nonce}; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.tile.openstreetmap.org"
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                perRouteOverrides = listOf(
                    RouteHeaderOverride(
                        pattern = "/map/**",
                        csp = mapCsp,
                    ),
                ),
            ),
        )
        val mapApp = buildApp(config = config)

        val response = mapApp(Request(GET, "/map/europe"))
        val csp = response.header("Content-Security-Policy")
        assertNotNull(csp, "CSP should be present on /map/ routes")
        assertTrue(csp.contains("tile.openstreetmap.org"), "CSP should allow OSM tiles but was: $csp")
        assertTrue(csp.contains("'nonce-"), "CSP should contain a nonce but was: $csp")
    }

    @Test
    fun `blank HSTS suppresses the header`() {
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                strictTransportSecurity = "",
            ),
        )
        val hstsApp = buildApp(config = config)

        val response = hstsApp(Request(GET, "/health"))
        assertNull(response.header("Strict-Transport-Security"), "HSTS should be absent when blank")
    }

    @Test
    fun `per-route override for X-Frame-Options on embed path`() {
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                perRouteOverrides = listOf(
                    RouteHeaderOverride(
                        pattern = "/embed/*",
                        xFrameOptions = "ALLOW-FROM https://example.com",
                    ),
                ),
            ),
        )
        val embedApp = buildApp(config = config)

        val response = embedApp(Request(GET, "/embed/video"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Frame-Options", "ALLOW-FROM https://example.com"))
    }

    @Test
    fun `first matching per-route pattern wins`() {
        val config = testConfig.copy(
            securityHeaders = testConfig.securityHeaders.copy(
                perRouteOverrides = listOf(
                    RouteHeaderOverride(
                        pattern = "/**",
                        xFrameOptions = "SAMEORIGIN",
                    ),
                    RouteHeaderOverride(
                        pattern = "/embed/**",
                        xFrameOptions = "ALLOW-FROM https://example.com",
                    ),
                ),
            ),
        )
        val orderedApp = buildApp(config = config)

        val response = orderedApp(Request(GET, "/embed/video"))
        assertThat(response, org.http4k.hamkrest.hasHeader("X-Frame-Options", "SAMEORIGIN"))
    }
```

Also add this import at the top of the file:

```kotlin
import io.github.rygel.outerstellar.platform.RouteHeaderOverride
```

- [ ] **Step 2: Run the tests**

Ensure Podman is running, then:

```powershell
mvn -pl platform-web -am test -Dtest=SecurityHeadersIntegrationTest "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: `BUILD SUCCESS`, all existing + new tests pass. If the per-route tests fail, re-check `findOverride` and `PathPatternMatcher.matches`.

- [ ] **Step 3: Commit**

```powershell
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/SecurityHeadersIntegrationTest.kt
git commit -m "test: add per-route security header override integration tests (#501)"
```

---

## Task 8: Documentation

**Files:**
- Create: `docs/configuration/security-headers.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Create security headers documentation**

Create `docs/configuration/security-headers.md`:

```markdown
# Security Response Headers Configuration

The platform applies security response headers on every HTTP response. All headers are configurable globally via YAML or environment variables, and can be overridden per-route using Ant-style glob patterns.

## Global configuration

### YAML

```yaml
securityHeaders:
  permissionsPolicy: "camera=(), microphone=(), geolocation=()"
  referrerPolicy: "strict-origin-when-cross-origin"
  xFrameOptions: "DENY"
  xContentTypeOptions: "nosniff"
  strictTransportSecurity: "max-age=31536000; includeSubDomains"
```

### Environment variables

| YAML Key | Env Var | Default |
|---|---|---|
| `securityHeaders.permissionsPolicy` | `PERMISSIONS_POLICY` | `camera=(), microphone=(), geolocation=()` |
| `securityHeaders.referrerPolicy` | `REFERRER_POLICY` | `strict-origin-when-cross-origin` |
| `securityHeaders.xFrameOptions` | `X_FRAME_OPTIONS` | `DENY` |
| `securityHeaders.xContentTypeOptions` | `X_CONTENT_TYPE_OPTIONS` | `nosniff` |
| `securityHeaders.strictTransportSecurity` | `STRICT_TRANSPORT_SECURITY` | `max-age=31536000; includeSubDomains` |

Set `STRICT_TRANSPORT_SECURITY` to an empty string to suppress HSTS (useful in dev mode).

CSP is configured separately via `cspPolicy` / `CSP_POLICY` (existing, unchanged).
CORS is configured separately via `corsOrigins` / `CORSORIGINS` (existing, unchanged).

## Per-route overrides

Per-route overrides allow specific security headers to be changed for matching URL paths. Overrides use Ant-style glob patterns.

### Pattern syntax

| Pattern | Matches | Does NOT match |
|---|---|---|
| `/map/**` | `/map`, `/map/europe`, `/map/europe/france` | `/api/map` |
| `/api/*/users` | `/api/v1/users` | `/api/v1/admin/users` |
| `/static/*.css` | `/static/site.css` | `/static/sub/site.css` |
| `/login` | `/login` only | `/login/callback` |

- `*` matches one path segment (no slashes)
- `**` matches any number of segments
- Plain string without wildcards is exact match
- First matching pattern in the list wins

### YAML example

```yaml
securityHeaders:
  perRouteOverrides:
    - pattern: "/map/**"
      permissionsPolicy: "geolocation=(self), camera=(), microphone=()"
      csp: "default-src 'self'; script-src 'self' {nonce}; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.tile.openstreetmap.org; base-uri 'self'; form-action 'self'"
    - pattern: "/api/external/**"
      corsAllowedOrigins:
        - "*"
    - pattern: "/embed/*"
      xFrameOptions: "ALLOW-FROM https://example.com"
```

### Fields

Each per-route override has a `pattern` (required) and any combination of optional header overrides. Fields not set in the override fall back to the global config value.

| Field | Overrides |
|---|---|
| `permissionsPolicy` | `Permissions-Policy` header |
| `referrerPolicy` | `Referrer-Policy` header |
| `xFrameOptions` | `X-Frame-Options` header |
| `xContentTypeOptions` | `X-Content-Type-Options` header |
| `strictTransportSecurity` | `Strict-Transport-Security` header (blank = suppress) |
| `csp` | `Content-Security-Policy` header (`{nonce}` is substituted) |
| `corsAllowedOrigins` | CORS `Access-Control-Allow-Origin` (list of strings) |

### Filter chain order

Per-route overrides are resolved inside the `securityHeaders` filter (position 5 in the chain), which sits above extension filters. On the response path, `securityHeaders` applies headers after extensions have modified the response. This means per-route overrides always win — an extension cannot accidentally regress a declared security policy.
```

- [ ] **Step 2: Add CHANGELOG entry**

In `CHANGELOG.md`, under `## [Unreleased]`, add:

```markdown
### Added

- **Configurable security headers** — all response headers (`Permissions-Policy`, `X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`, `Strict-Transport-Security`) are now configurable via YAML/env vars with per-route overrides using Ant-style glob patterns. Extensions can no longer be blocked by hard-coded security headers (#501).
```

- [ ] **Step 3: Commit**

```powershell
git add docs/configuration/security-headers.md CHANGELOG.md
git commit -m "docs: document configurable security headers with per-route overrides (#501)"
```

---

## Final Verification

- [ ] **Run the full quality-gated build for affected modules**

```powershell
mvn -pl platform-core,platform-web -am install -DskipTests "-Djacoco.skip=true" "-Denforcer.skip=true"
```

This runs Detekt, SpotBugs, Checkstyle, PMD/CPD, Spotless, and i18n validation. Must pass before pushing.

- [ ] **Run the full test suite for affected modules**

Ensure Podman is running, then:

```powershell
mvn -pl platform-core,platform-web -am test
```

Expected: `BUILD SUCCESS`, all tests pass.

- [ ] **Verify backward compatibility**

```powershell
mvn -pl platform-web -am test -Dtest=SecurityHeadersIntegrationTest
```

All existing tests must still pass with no changes to the test config (defaults match previous hard-coded values).
