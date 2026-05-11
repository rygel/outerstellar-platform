# TODO

Architecture, security, and maintainability improvements identified during code review.

---

## High Priority

### Security

- [ ] **CORS defaults to `*` — restrict to specific origins**
  The `corsOrigins` config defaults to `"*"`, allowing any external origin to make authenticated requests.
  — `platform-core/.../AppConfig.kt:107`, `platform-web/.../web/Filters.kt:156-170`

- [ ] **Error messages leak internal details in API responses**
  Unhandled exceptions include `e.message` in JSON error responses, which can leak SQL syntax, file paths, and stack traces.
  — `platform-web/.../web/Filters.kt:472`

- [ ] **Password reset token exposed in URL query parameter**
  Token is sent as `?token=$tokenValue` in the reset link, leaking via Referer header, browser history, and server logs. Should use POST-based submission.
  — `platform-security/.../security/PasswordResetService.kt:38`

- [ ] **Session cookie `Secure` flag defaults to `false`**
  The `sessionCookieSecure` config defaults to `false`, transmitting session cookies over unencrypted HTTP. Should default to `true` for production profiles.
  — `platform-core/.../AppConfig.kt:49`

### Hardcoded Values

- [ ] **Seed data has hardcoded weak password `"password123"` in `src/main`**
  Four development users (admin, alice, bob, carol) all use the same weak hardcoded password. Move to env-var override or test-only.
  — `platform-seed/.../seed/SeedData.kt:67`

- [ ] **Default JDBC password `"outerstellar"` has no production guard**
  The default password is hardcoded in both YAML files (6 places) and Kotlin defaults. No startup assertion prevents deploying with defaults.
  — `platform-core/.../AppConfig.kt:44-46`, all `application.yaml` files

### Architecture

- [ ] **Refactor `SwingSyncApp.kt` God class (856 lines)**
  Contains main window, all navigation, menu items, translations, and ~50 fields. Extract `SyncWindow` into its own class; split navigation and menu logic.
  — `platform-desktop/.../swing/SwingSyncApp.kt`

- [ ] **Replace `sendAsync` in `SegmentAnalyticsService` with sync + background thread**
  `client.sendAsync()` violates the project's synchronous-only architectural principle. Use `client.send()` in a daemon thread instead.
  — `platform-core/.../analytics/SegmentAnalyticsService.kt:85`

- [ ] **Reduce generic `catch (Exception)` boilerplate in `DesktopSyncEngine`**
  22 methods duplicate the same try/catch pattern. Extract a higher-order function like `runCatching(operation, onSessionExpired, onError)`.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt` (22 instances)

---

## Medium Priority

### Security

- [ ] **CSP allows `'unsafe-inline'` on scripts and styles**
  This defeats CSP's primary XSS mitigation. Use `'strict-dynamic'` or nonce-based CSP; remove `'unsafe-inline'` for styles.
  — `platform-web/.../web/Filters.kt:43-44`

- [ ] **Missing HSTS header**
  `Strict-Transport-Security` is not set, leaving users vulnerable to SSL-stripping.
  — `platform-web/.../web/Filters.kt:173-188`

- [ ] **Weak password policy (length-only)**
  Only requires 8+ characters. No complexity, common-password, or breach checks.
  — `platform-security/.../security/SecurityService.kt:321`

- [ ] **No account lockout after failed logins**
  Unlimited password guesses per account. Only per-IP rate limiting exists.
  — `platform-security/.../security/SecurityService.kt:56-77`

- [ ] **SSRF protection gaps: IPv6, DNS rebinding, 0.0.0.0**
  The private host patterns miss IPv6 loopback (`::1`), link-local (`fe80::`), unique-local (`fd00::`), and DNS rebinding attacks.
  — `platform-security/.../security/SecurityService.kt:209-218`

- [ ] **Login rate limiting is per-IP, not per-account**
  Attacker can brute-force a single account through IP rotation.
  — `platform-web/.../web/RateLimiter.kt:79`

- [ ] **WebSocket upgrade response missing security headers**
  The `SyncWebSocket` handler doesn't apply CSP, HSTS, or other security headers to its upgrade response.
  — `platform-web/.../web/SyncWebSocket.kt`

### Architecture

- [ ] **Refactor `App.kt` (534 lines, 15-parameter function)**
  The `app()` function assembles the entire HTTP handler chain directly. `PluginContext` is constructed 4 times redundantly. Extract into smaller assembly functions.
  — `platform-web/.../App.kt`

- [ ] **Declare `platform-core` as explicit dependency in desktop modules**
  Both `platform-desktop` and `platform-desktop-javafx` get `platform-core` only transitively through `platform-persistence-jooq`. A refactoring of that module's dependencies could silently break them.
  — `platform-desktop/pom.xml`, `platform-desktop-javafx/pom.xml`

### Hardcoded Values

- [ ] **Replace `"ADMIN"` / `"USER"` string comparisons with `UserRole` enum**
  Role checks use raw string comparisons in 5 production files. Use the `UserRole` enum for type safety.
  — `UserAdminRoutes.kt:106`, `SyncViewModel.kt:328`, `SwingSyncApp.kt:641`, JavaFX controllers

- [ ] **Centralize `appBaseUrl = "http://localhost:8080"`**
  Hardcoded in 4 separate production files. Should be injected from a single config source.
  — `SecurityService.kt:24`, `PasswordResetService.kt:18`, `SecurityModule.kt:21`, `DesktopAppConfig.kt:7`

---

## Low Priority

### Security

- [ ] **JWT secret defaults to empty string with no startup validation**
  If `enabled=true` is set without a non-empty `secret`, HMAC would use an empty key. Add a startup assertion.
  — `platform-core/.../AppConfig.kt:26`, `platform-security/.../security/JwtService.kt`

- [ ] **Health endpoint is unauthenticated, leaks DB status**
  `/health` returns whether the database is UP or DOWN with no authentication.
  — `platform-web/.../App.kt:464-478`

- [ ] **No input size validation on contact/message content fields**
  Message content, author, and contact fields have no server-side maximum length limits.
  — `platform-web/.../web/HomeRoutes.kt:66-67`, `ContactsRoutes.kt:60-67`

### Architecture

- [ ] **Extract SSRF validation from `SecurityService.updateProfile()` into a separate `UrlValidator`**
  The avatar URL SSRF checks are inlined in `SecurityService`. This logic is duplicated in principle and should be a reusable validator.
  — `platform-security/.../security/SecurityService.kt:196-220`

- [ ] **Split `WebPageFactory.kt` (582 lines) into domain-specific factories**
  Annotated `@Suppress("TooManyFunctions")` with ~20 builder methods. Extract contact, admin, settings pages.
  — `platform-web/.../web/WebPageFactory.kt`

- [ ] **Add `DesktopSyncEngine` interface for testability**
  Currently a concrete class; `SyncViewModel` and JavaFX controllers depend on it directly.
  — `platform-sync-client/.../engine/DesktopSyncEngine.kt`

- [ ] **Dead scaffolding: `AppleOAuthProvider` and `PushNotificationService`**
  Both contain TODO placeholders and are non-functional. Either wire to config or remove.
  — `platform-security/.../security/AppleOAuthProvider.kt`, `platform-core/.../service/PushNotificationService.kt`

### Hardcoded Values

- [ ] **Hardcoded JTE development paths in `JteInfra.kt`**
  Assumes working directory is project root with `web/` subdirectory — breaks in production deployments.
  — `platform-web/.../infra/JteInfra.kt:37-39`

- [ ] **Supported languages/layouts/shells are inline sets**
  `setOf("en", "fr")`, `setOf("nice", "cozy", "compact")`, `setOf("sidebar", "topbar")` in `Filters.kt` should be configurable.
  — `platform-web/.../web/Filters.kt:281-296`

- [ ] **Outbox status strings (`"PENDING"`, `"PROCESSED"`, `"FAILED"`) scattered**
  Used as raw strings in `JooqOutboxRepository`, `MessageService`, and `DevDashboardRoutes`. Should be an enum or constants.
  — Multiple files

---

## Completed

- [x] **Targeted cache invalidation in `MessageService`**
  — `platform-core/.../service/MessageService.kt` lines 129–130, 150–152, 197, 207–208, 240–242, 276–278

- [x] **Bound the `gravatarCache` with Caffeine**
  — `platform-web/.../web/WebPageFactory.kt` line 9

- [x] **Eliminate extra SELECT in `JooqContactRepository.updateContact()`**
  — `platform-persistence-jooq/.../persistence/JooqContactRepository.kt` lines 247–252

- [x] **Continuous outbox drain when batch is full**
  — `platform-core/.../service/OutboxProcessor.kt` line 8 · `Main.kt` line 61

- [x] **Document the jOOQ vs. JDBI persistence selection policy**
  Both modules implement the same repository interfaces with no runtime flag or documented rule for which is active.

- [x] **Guard `WebContext.user` against cache removal**
  — `platform-web/.../web/WebContext.kt` lines 69–86

- [x] **Update dependency bumps** (http4k, opentelemetry, angus-mail, assertj-core, ktfmt)

- [x] **Fix desktop `SyncViewModel.onSessionExpired()` race condition**
