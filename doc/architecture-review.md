# Architecture Review

Last reviewed: 2026-03-14

## Strengths

### Clean module boundaries

The project enforces proper dependency inversion across its multi-module structure. Domain interfaces live in `core/`, implementation details live in `persistence/`, and this separation is enforced at build time by ArchUnit tests. The `core` module has no dependency on `web`, `desktop`, or jOOQ implementation classes.

### Repository interface pattern

All persistence contracts are defined as interfaces in `core/persistence/` (`MessageRepository`, `ContactRepository`, `OutboxRepository`). The jOOQ implementations in `persistence/` are the only classes that touch SQL or jOOQ directly. This means any module depending on `core` can work against the interface without pulling in database dependencies.

### Optional dependency wiring

Koin's `getOrNull<TransactionManager>()` pattern lets services like `MessageService` and `OutboxProcessor` function in both transactional contexts (web with a real `JooqTransactionManager`) and non-transactional contexts (desktop with a stub). This avoids conditional logic in the service layer and makes the same business logic reusable across deployment targets.

### Quality tooling

The build enforces Detekt, SpotBugs, Checkstyle, PMD, Spotless/Ktlint, JaCoCo, Enforcer (dependency convergence, duplicate class banning), and OWASP dependency checks. Architecture rules are verified by ArchUnit tests. This goes beyond what most starters include and sets a high baseline for downstream projects.

### Realistic sync protocol

The bidirectional sync between desktop and web demonstrates dirty tracking, timestamp-based conflict detection, optimistic locking, and an outbox pattern. This is a non-trivial working example, not a toy.

### H2 as the persistence engine

H2 is the intentional and permanent choice for this starter. It provides zero-setup local persistence, works identically for both web server and Swing desktop, integrates cleanly with Flyway and jOOQ, and keeps the starter runnable without any external infrastructure. This is a deliberate design decision, not a limitation.

## Package root convention

The project uses two package roots:

- `dev.outerstellar.starter` — the project's own code
- `com.outerstellar` — external Outerstellar libraries, consumed as Maven dependencies

The `com.outerstellar` packages (`com.outerstellar:outerstellar-i18n` and `com.outerstellar:outerstellar-theme`) are external dependencies declared in the POM and resolved from the Maven repository. They are not vendored — no source copies exist in this project. The `com.outerstellar` package root should never contain project-owned source files.

**Rule:** all project-owned code uses `dev.outerstellar.starter`. The `com.outerstellar` namespace belongs to the external Outerstellar libraries and must not be used for project-owned classes.

## Areas for improvement

### Security model

The current authentication uses the user's UUID directly as a bearer token for API access. This is a predictable, non-expiring identifier — anyone who learns a user ID has permanent API access.

Recommendations:

- Replace UUID-as-token with opaque session tokens or JWTs that include expiry
- Add CSRF protection to session-based web routes
- Add rate limiting on `/api/v1/auth/login` to prevent brute-force attacks
- Move the default admin user seeding to a dev-only profile or CLI command rather than running it on every startup

### Outbox pattern is incomplete

`OutboxProcessor` and `OutboxRepository` capture events into an outbox table, but there is no scheduled consumer or polling loop that processes pending entries. The outbox entries accumulate without being consumed. This is a useful pattern to demonstrate but is currently misleading as a reference because the consumer side is missing.

Recommendation: either implement a simple polling consumer (e.g., a coroutine or scheduled executor that calls `listPending` and dispatches entries) or document explicitly that the consumer is left as an exercise.

### Event publishing granularity

`EventPublisher` supports only `publishRefresh(panelId: String)` — a single "refresh this panel" signal with no event type or payload. This means every event triggers a full reload of the target panel rather than a targeted incremental update.

Recommendation: introduce typed events (e.g., `MessageCreated`, `ContactUpdated`) with payload data so WebSocket subscribers can perform targeted DOM updates instead of full fragment re-fetches.

### Seed data runs unconditionally

`SeedData` and `JooqUserRepository.seedAdminUser()` execute on every application startup, including production. While the seed methods are idempotent (they check for existing data), having default credentials attempted on every boot is a security concern.

Recommendation: gate seed data behind a profile flag (e.g., `runtime-dev`) so it never runs in production.

### Desktop UI framework

Swing is effectively unmaintained by Oracle and receives no new features. For a Kotlin starter, Compose for Desktop (Kotlin Multiplatform) would be a more forward-looking choice that shares paradigms with the rest of the Kotlin ecosystem. This is a long-term consideration, not an immediate action item.

### Test base class inheritance

`H2WebTest` uses an abstract base class for test infrastructure (database setup, cleanup, stubs). This consumes the single-inheritance slot and creates coupling between test classes. A JUnit 5 extension or test fixture factory would be more composable and allow tests to mix different infrastructure concerns without inheritance chains.

## Design decisions applied during this review

### Removed primary/replica DSL distinction

The `PersistenceModule` previously registered two named `DSLContext` instances (`primaryDsl` and `replicaDsl`) that both pointed to the same `DataSource`. All repository constructors accepted both parameters. This added naming complexity and constructor noise without delivering any actual read/write splitting.

**What changed:**

- `PersistenceModule` now registers a single `DSLContext` (no named qualifiers)
- `JooqMessageRepository`, `JooqContactRepository`, and `JooqOutboxRepository` now take a single `dsl: DSLContext` constructor parameter
- `JooqUserRepository` and `JooqTransactionManager` were already using a single `dsl` parameter and required no changes

If read/write splitting is needed in the future, it should be introduced when a real replica datasource exists, not as a premature abstraction.

### Replaced dynamic jOOQ field lookups with generated references

Repository code previously used `MESSAGES.field("DELETED_AT", LocalDateTime::class.java)` with null-checks on every query, treating generated fields as optional. Since the jOOQ generated code includes typed fields for `DELETED_AT`, `SYNC_CONFLICT`, `UPDATED_AT_EPOCH_MS`, and all other columns, these dynamic lookups were unnecessary.

**What changed:**

- All `MESSAGES.field("...", Type::class.java)` calls replaced with direct field references like `MESSAGES.DELETED_AT`, `MESSAGES.SYNC_CONFLICT`
- Removed null-check guard clauses around fields that always exist in the generated schema
- Extracted `notSoftDeleted()` and `softDeleted()` helper methods in `JooqMessageRepository` to eliminate the repeated deleted-field condition pattern
- Same cleanup applied to `JooqContactRepository`

**Why:** The purpose of jOOQ code generation is type-safe SQL. Dynamic string-based field lookups defeat this — schema changes cause runtime failures instead of compile errors. The null-checks also created a false impression that fields might not exist, obscuring the actual schema contract.

### Fixed SyncWebSocket concurrent modification

`publishRefresh()` iterated over the `ConcurrentHashMap`-backed connection set and called `connections.remove(ws)` inside the iteration on send failure. While `ConcurrentHashMap.newKeySet()` is safe for individual operations, removing elements during `forEach` can skip entries.

**What changed:** Failed connections are now collected into a local list and removed after iteration completes.

### Deduplicated and generalized bearer token auth filter

The `App.kt` file contained two identical copies of the bearer token validation filter (`syncAuthFilter` and `bearerAuthFilter`). Both were hardcoded to look up only the admin user via `userRepository.findByUsername("admin")`.

**What changed:**

- Extracted a single `bearerAuthFilter` defined once and reused for both sync and API contract security
- Changed from `findByUsername("admin")` to `findById(UUID)` so any enabled user can authenticate via the API, not just admin
- Removed the now-unused `PasswordEncoder` parameter from the `app()` function and the `WebModule` wiring

### Routed form registration through SecurityService

The `AuthRoutes` form-based registration path directly called `userRepository.save()` without any validation — no duplicate username check, no password length enforcement, no email format validation. The API-based `AuthApi` already went through `SecurityService.register()` which has all these checks.

**What changed:**

- `AuthRoutes` now calls `securityService.register(email, password)` instead of directly constructing and saving a `User`
- Registration errors (duplicate username, short password) are caught and displayed to the user
- Removed `UserRepository`, `PasswordEncoder`, `User`, `UserRole`, and `UUID` from `AuthRoutes` — the class now depends only on `SecurityService` for auth operations

### Added atomic cache loading to prevent stampede

`MessageService.listMessages()` checked the cache, found a miss, then queried the database and stored the result. Under concurrent load, multiple threads could miss the cache simultaneously and all hit the database for the same query.

**What changed:**

- Added `getOrPut(key, loader)` to the `MessageCache` interface with a default implementation
- `CaffeineMessageCache` overrides it using Caffeine's `cache.get(key) { loader() }` which guarantees only one thread executes the loader for a given key
- `NoOpMessageCache` overrides it to always call the loader (no caching behavior)
- `MessageService.listMessages()` now uses `cache.getOrPut(cacheKey) { ... }` instead of manual check-then-populate

### Introduced OptimisticLockException

Both `JooqMessageRepository.updateMessage()` and `JooqContactRepository.updateContact()` used `check(rows != 0)` to detect version conflicts. `check()` throws `IllegalStateException`, which semantically means "programming error" — but a version conflict is an expected business condition that callers should handle.

**What changed:**

- Added `OptimisticLockException(entityType, syncId)` to the `OuterstellarException` sealed hierarchy in `Exceptions.kt`
- Replaced `check()` calls with explicit `if (rows == 0) throw OptimisticLockException(...)` in both repositories
- Since `OptimisticLockException` extends `OuterstellarException`, it is handled by the existing global error handler without additional wiring

### Broadened OutboxProcessor exception handling

The processor only caught `IllegalStateException` and `IllegalArgumentException`. Any other exception type (database timeout, network error, unexpected runtime failure) would propagate and stop the entire batch.

**What changed:** Replaced the two specific catch blocks with a single `catch (e: Exception)` that logs the full stack trace and marks the entry as failed. This ensures one bad entry cannot halt processing of the remaining batch.

### Fixed global error handler logging

The error handler logged `e.message` only, discarding the full stack trace and cause chain: `logger.error("Error handling request ${request.uri}: ${e.message}")`.

**What changed:** Switched to parameterized logging with the exception as the final argument: `logger.error("Error handling request {}: {}", request.uri, e.message, e)`. SLF4J recognizes the trailing `Throwable` and prints the full stack trace.

### Added pagination bounds validation

The `HomeRoutes` limit and offset parameters came directly from user query strings with no bounds. A client could send `?limit=999999` to force expensive queries, or negative offsets to cause undefined behavior.

**What changed:** Added `coerceIn(1, MAX_LIMIT)` for limit (max 100) and `coerceAtLeast(0)` for offset.

### Made SyncViewModel observers thread-safe

The `observers` list was a plain `mutableListOf` accessed from both SwingWorker background threads (via `notifyObservers`) and the EDT (via `addObserver`).

**What changed:** Replaced `mutableListOf<() -> Unit>()` with `CopyOnWriteArrayList<() -> Unit>()`, which is safe for concurrent iteration and modification.

### Translated hardcoded status messages in SyncViewModel

Several status strings were hardcoded in English (`"Contact updated"`, `"Conflict resolved using $strategy strategy"`, `"Auto-syncing..."`, `"Failed to resolve conflict: ..."`), bypassing the `i18nService` even though the app supports multiple languages.

**What changed:**

- All hardcoded strings replaced with `i18nService.translate(...)` calls using new translation keys
- Added keys `swing.status.contactUpdated`, `swing.status.conflictResolved`, `swing.status.conflictFailed`, and `swing.status.autoSyncing` to both `messages.properties` (English) and `messages_fr.properties` (French)

### Switched request logging to parameterized format

The request logging filter used string interpolation: `logger.info("${request.method} ${request.uri} -> ${response.status} (${duration}ms)")`. This eagerly concatenates the string even when the INFO log level is disabled.

**What changed:** Switched to SLF4J parameterized format: `logger.info("{} {} -> {} ({}ms)", request.method, request.uri, response.status, duration)`. The string is only constructed when the log level is active.

### Made conflict resolution strategy type-safe

Conflict resolution was passed as a raw `String` (`"mine"` or `"server"`) through `MessageService`, `SyncViewModel`, `HomeRoutes`, and `SwingSyncApp`. Typos like `"sever"` would silently fall through to the default case.

**What changed:**

- Added `ConflictStrategy` enum (`MINE`, `SERVER`) with a `fromString()` companion method to `Exceptions.kt` (shared model file)
- Updated `MessageService.resolveConflict()` to accept `ConflictStrategy` instead of `String`
- Updated all callers: `HomeRoutes`, `SyncViewModel`, `SwingSyncApp`

### Tightened session cookie SameSite attribute

The session cookie used `SameSite=Lax`, which allows the cookie to be sent on cross-site navigational GET requests. For a session authentication cookie, this is unnecessarily permissive.

**What changed:** Changed to `SameSite=Strict` in both `SessionCookie.create()` and `SessionCookie.clear()`. This prevents the session cookie from being sent on any cross-origin request.

### Changed executor shutdown to immediate termination

`SyncViewModel.stopAutoSync()` called `shutdown()` on the `ScheduledExecutorService`, which waits for running tasks to complete. On application exit, this can delay shutdown if a sync task is in progress.

**What changed:** Replaced `shutdown()` with `shutdownNow()`, which interrupts running tasks and cancels pending ones for immediate cleanup.

### Fixed XSS in toast notification via innerHTML

`Layout.kte` built the toast notification by assigning to `toast.innerHTML` with string concatenation:

```js
toast.innerHTML = '...<p ...>' + message + '</p>...'
```

`message` is `event.detail.xhr.responseText` — the raw HTTP response body from the server. Server error responses can include user-controlled content (e.g. a validation error message echoing back a malformed email address). Assigning raw text to `innerHTML` would execute any `<script>` tags or event handler attributes in that content.

**What changed:** Replaced the `innerHTML` assignment with explicit DOM construction using `document.createElement` and `textContent` assignment. The message and title strings are now set with `.textContent` rather than concatenated into HTML, so no input can be interpreted as markup.

### Moved setLastSyncEpochMs inside the sync transaction

`SyncService.sync()` applied pulled messages inside a `transactionManager.inTransaction` block but then called `repository.setLastSyncEpochMs(pullBody.serverTimestamp)` _after_ the transaction committed. If the process crashed between the commit and the timestamp write, the next sync would re-apply an already-processed batch. Because `upsertSyncedMessage` is idempotent this would not corrupt data, but it would cause redundant work and obscure the sync state.

**What changed:** Moved `repository.setLastSyncEpochMs(...)` inside the `inTransaction` block so the timestamp advances atomically with the message upserts.

### Closed equal-timestamp gap in conflict detection

`MessageService.processPushRequest` used strict `>` and `<` comparisons on `updatedAtEpochMs`. When an incoming message had the same timestamp as the local version, no branch matched and the message was silently dropped — neither applied nor flagged as a conflict.

**What changed:** Changed `incoming.updatedAtEpochMs > current.updatedAtEpochMs` to `>=`. Equal timestamps now take the incoming version, which is the correct last-writer-wins semantics for identical timestamps. The separate `<` branch was replaced with an `else` clause so all cases are exhaustively covered.

### Added sessionCookieSecure prod profile

`AppConfig.sessionCookieSecure` defaults to `false`, which is appropriate for local HTTP development but dangerous in production (the session cookie would be transmitted over unencrypted connections). Previously there was no mechanism to enforce the secure flag in production without setting an environment variable manually.

**What changed:** Created `web/src/main/resources/application-prod.yaml` that sets `sessionCookieSecure: true`. Running with `APP_PROFILE=prod` activates this profile. Added a comment on the field in `AppConfig` explaining the intentional default and how to override it.

### Read admin password from environment variable

`Main.kt` hardcoded `"admin123"` as the password for the seeded admin user. Anyone who cloned the starter and deployed it without reading `Main.kt` would have a known credential in production.

**What changed:** `Main.kt` now reads `ADMIN_PASSWORD` from the environment. If the variable is not set, a random UUID is generated and logged at WARN level as the first-boot password, with a reminder to set the variable before going to production. Because `seedAdminUser` only creates the admin user when none exists, the generated password is only meaningful on first boot.

### Moved seedAdminUser onto the UserRepository interface

`Main.kt` called `seedAdminUser` by casting `userRepository` to `JooqUserRepository`:

```kotlin
(main.userRepository as JooqUserRepository).seedAdminUser(...)
```

This breaks the interface abstraction — any alternative `UserRepository` implementation would throw `ClassCastException` at startup.

**What changed:** `seedAdminUser(passwordHash: String)` is now declared on the `UserRepository` interface. `JooqUserRepository.seedAdminUser` is annotated with `override`. `Main.kt` calls it directly on the `UserRepository` without a cast.

### Added WebSocket refresh events to ContactService

`MessageService` calls `eventPublisher.publishRefresh("message-list-panel")` after every mutation so connected browser tabs update in real time. `ContactService` had no equivalent — contact mutations were silent, and open tabs showing the contacts page would not refresh.

**What changed:** `ContactService` now accepts an `EventPublisher` constructor parameter (defaulting to `NoOpEventPublisher`). `createContact`, `updateContact`, and `deleteContact` each call `eventPublisher.publishRefresh("contact-list-panel")` after the repository operation. `CoreModule` wires in the shared `EventPublisher` singleton.

### Batched contact collection INSERTs

`insertCollections` previously issued one `INSERT` statement per email, phone, and social handle — a contact with 3 emails, 2 phones, and 2 socials generated 7 separate round trips.

**What changed:** Each non-empty collection is now inserted using `txDsl.batch(inserts).execute()`, reducing N individual round trips to a single batch statement per collection type (still within the same transaction).

### Made SyncService.apiToken @Volatile

`SyncService.sync()` runs on a `ScheduledExecutorService` background thread while `login()` and `logout()` can be called from the Swing EDT. The plain `var apiToken` had no memory visibility guarantee — a background thread could observe a stale null even after `login()` returned on another thread.

**What changed:** `apiToken` is now `@Volatile`, ensuring writes from any thread are immediately visible to all other threads.

### Removed duplicate htmx WebSocket extension script

`Layout.kte` included `htmx-ext-ws@2.0.1/ws.js` twice (lines 15–16). The duplicate caused the extension to register twice, which can trigger duplicate event listeners and redundant network requests.

**What changed:** Removed the duplicate `<script>` tag.

### Validated preference values before persisting cookies

`Filters.stateFilter` wrote `theme`, `lang`, and `layout` query parameters directly into long-lived cookies without checking whether the values were valid. WebContext validated the values on read and fell back to defaults, but the invalid value was still stored in the cookie, persisting silently across requests.

**What changed:** Each parameter is now validated against its allowed set before the cookie is created — `lang` must be `"en"` or `"fr"`, `layout` must be `"nice"`, `"cozy"`, or `"compact"`, and `theme` must be a known ID from `ThemeCatalog`. Parameters that fail validation are ignored and no cookie is set.

### Made contact collection updates atomic

`JooqContactRepository.insertCollections` (which replaces emails, phones, and social links for a contact with DELETE + INSERT) was called outside any database transaction in `updateContact`, `upsertSyncedContact`, and `resolveConflict`. A crash or exception between the DELETE and INSERT would leave the collections empty while the parent contact row remained updated — a partial, inconsistent state.

**What changed:** `insertCollections` now accepts a `DSLContext` parameter instead of using the class-level `dsl` field. Each caller (`updateContact`, `upsertSyncedContact`, `resolveConflict`, and `insertContact`) wraps its work in `dsl.transaction { config -> val txDsl = using(config); ... }` so the contact row update and the collection replacement execute atomically.

### Fixed UpdateService version comparison with pre-release suffixes

`UpdateService.isNewer` split version strings on `.` and filtered with `toIntOrNull()`. A version like `1.2.3-SNAPSHOT` splits into `["1", "2", "3-SNAPSHOT"]`; `"3-SNAPSHOT".toIntOrNull()` returns null, so it is dropped. The version would then be compared as `[1, 2]` rather than `[1, 2, 3]`, making it appear older than `1.2.3`.

**What changed:** Added `.substringBefore('-')` before splitting so that `1.2.3-SNAPSHOT` is normalized to `1.2.3` before component parsing. Both `latestParts` and `currentParts` are now stripped of any pre-release suffix before the numeric comparison.
