# Deprecate jOOQ — Remove `platform-persistence-jooq` Module

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `platform-persistence-jooq` entirely, making `platform-persistence-jdbi` the sole persistence implementation.

**Architecture:** JDBI already has 100% repository parity with jOOQ. The main work is (1) ensuring jdbi has all migrations, (2) rewriting WebTest.kt and downstream tests from jOOQ `DSLContext` to JDBI `Jdbi`, (3) removing the jOOQ module and all references. The `Migrator.kt` standalone runner moves to jdbi. `DatabaseInfra.kt` is already duplicated — the jdbi copy survives.

**Tech Stack:** JDBI 3, Flyway, Testcontainers, http4k, Koin

---

## File Structure

### Created
- `platform-persistence-jdbi/src/main/resources/db/migration/V8__query_path_indexes.sql` — copy from jooq
- `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/Migrator.kt` — standalone migration runner

### Modified
- `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt` — add NotificationRepository registration
- `platform-persistence-jdbi/src/main/resources/db/migration/migrations.index` — add V8
- `platform-web/src/test/kotlin/.../web/WebTest.kt` — rewrite from jOOQ to JDBI
- `platform-web/src/test/kotlin/.../web/AuditLogIntegrationTest.kt` — replace jOOQ DSL with JDBI
- `platform-web/src/test/kotlin/.../web/PasswordResetFlowIntegrationTest.kt` — replace jOOQ DSL with JDBI
- `platform-web/src/test/kotlin/.../web/SessionTimeoutIntegrationTest.kt` — replace jOOQ DSL with JDBI
- `platform-web/src/test/kotlin/.../web/UserManagementWebUiIntegrationTest.kt` — replace jOOQ DSL with JDBI
- `platform-web/src/test/kotlin/.../web/PlatformPageRenderingTest.kt` — replace JooqNotificationRepository
- `platform-web/src/test/kotlin/.../web/PerformanceBenchmarkTest.kt` — replace testDsl usage
- `platform-web/src/test/kotlin/.../security/SecurityIntegrationTest.kt` — replace JooqUserRepository
- `platform-web/src/main/kotlin/.../di/AdminWebModule.kt` — use Koin resolution instead of hardcoded JooqNotificationRepository
- `platform-web/pom.xml` — replace jooq dep with jdbi dep, remove jooq direct dep
- `platform-seeder/pom.xml` — replace jooq dep with jdbi dep
- `platform-desktop/pom.xml` — replace jooq dep with jdbi dep
- `platform-desktop-javafx/pom.xml` — replace jooq dep with jdbi dep
- `pom.xml` (root) — remove jooq module, dependencyManagement, extension, property, spinaker watch
- `docker/Dockerfile` — remove jooq COPY line
- `docker/Dockerfile.native` — remove jooq COPY line
- `docker/Dockerfile.build` desktop-test target — remove jooq COPY line
- `platform-core/src/test/kotlin/.../arch/ArchitectureTest.kt` — remove jooq package references
- `AGENTS.md` — remove jooq references
- `README.md` — remove jooq references

### Deleted
- `platform-persistence-jooq/` — entire directory

---

## Pre-conditions

- JDBI has all 13 repository implementations matching jOOQ (confirmed: yes)
- JDBI `DatabaseInfra.kt` is functionally identical to jOOQ's (confirmed: yes, only pool name differs)
- `JdbiNotificationRepository` exists but is not registered in `PersistenceModule` (confirmed: yes)

---

### Task 1: Ensure JDBI module is self-sufficient

**Files:**
- Create: `platform-persistence-jdbi/src/main/resources/db/migration/V8__query_path_indexes.sql`
- Create: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/Migrator.kt`
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`
- Modify: `platform-persistence-jdbi/src/main/resources/db/migration/migrations.index`
- Test: `platform-persistence-jdbi/src/test/kotlin/.../persistence/MigrationManifestDriftTest.kt`

- [ ] **Step 1: Copy V8 migration to jdbi**

Copy `platform-persistence-jooq/src/main/resources/db/migration/V8__query_path_indexes.sql` to `platform-persistence-jdbi/src/main/resources/db/migration/V8__query_path_indexes.sql`.

Content:

```sql
-- Indexes for high-traffic query paths identified during performance review.

-- plt_messages: listDirtyMessages() filters by dirty=true AND deleted_at IS NULL
CREATE INDEX IF NOT EXISTS idx_plt_messages_dirty
    ON plt_messages(dirty, deleted_at);

-- plt_contacts: listDirtyContacts() filters by dirty=true
CREATE INDEX IF NOT EXISTS idx_plt_contacts_dirty
    ON plt_contacts(dirty);

-- plt_sessions: looked up by token_hash during every authenticated request
CREATE INDEX IF NOT EXISTS idx_plt_sessions_token_hash
    ON plt_sessions(token_hash);

-- plt_sessions: cleaned up by user_id on password change (invalidate all)
CREATE INDEX IF NOT EXISTS idx_plt_sessions_user_id
    ON plt_sessions(user_id);

-- plt_password_reset_tokens: looked up by token during password reset flow
CREATE INDEX IF NOT EXISTS idx_plt_password_reset_tokens_token
    ON plt_password_reset_tokens(token);

-- plt_password_reset_tokens: cleaned up by user_id on password change
CREATE INDEX IF NOT EXISTS idx_plt_password_reset_tokens_user_id
    ON plt_password_reset_tokens(user_id);
```

- [ ] **Step 2: Update migrations.index**

Add `V8__query_path_indexes` to `platform-persistence-jdbi/src/main/resources/db/migration/migrations.index` in alphabetical order:

```
V1__initial_schema
V10__add_trgm_search_indexes
V11__add_totp
V12__add_polls
V2__user_profile_enhancements
V3__sessions_table
V4__user_preferences
V5__performance_indexes
V6__admin_stats_indexes
V7__account_lockout
V8__query_path_indexes
```

- [ ] **Step 3: Add NotificationRepository to JDBI PersistenceModule**

In `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceModule.kt`, add this registration alongside the other repositories:

```kotlin
single<NotificationRepository> { JdbiNotificationRepository(get()) }
```

Also add the import:

```kotlin
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiNotificationRepository
```

- [ ] **Step 4: Create Migrator.kt in jdbi module**

Create `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/Migrator.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import org.slf4j.LoggerFactory

fun main() {
    val logger = LoggerFactory.getLogger("io.github.rygel.outerstellar.platform.persistence.Migrator")
    val config = io.github.rygel.outerstellar.platform.AppConfig.fromEnvironment()

    logger.info(
        "Running Flyway migrations against {} as user {} (profile: {})",
        config.jdbcUrl.replace(Regex("://[^:]+:[^@]+@"), "://***:***@"),
        config.jdbcUser,
        config.profile,
    )

    val ds = createDataSource(config.jdbcUrl, config.jdbcUser, config.jdbcPassword, config.runtime)

    try {
        migrate(ds)
        logger.info("Host migrations completed successfully")
    } catch (e: Exception) {
        logger.error("Migration failed: {}", e.message, e)
        System.exit(1)
    } finally {
        ds.close()
    }

    logger.info("All migrations complete")
    System.exit(0)
}
```

- [ ] **Step 5: Compile and test jdbi module**

Run:
```powershell
mvn -pl platform-persistence-jdbi clean test "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: PASS (including MigrationManifestDriftTest)

- [ ] **Step 6: Commit**

```bash
git add platform-persistence-jdbi/
git commit -m "feat(jdbi): add missing migration V8, Migrator.kt, NotificationRepository registration"
```

---

### Task 2: Rewrite WebTest.kt to use JDBI

**Files:**
- Modify: `platform-web/src/test/kotlin/.../web/WebTest.kt`

This is the largest single change. WebTest currently uses `DSLContext` (jOOQ) for cleanup and repository instantiation. We replace it with JDBI's `Jdbi` instance and JDBI repository implementations.

- [ ] **Step 1: Rewrite WebTest.kt**

Replace the full file content of `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt` with:

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPollRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiUserRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.PollService
import javax.sql.DataSource
import org.http4k.core.HttpHandler
import org.jdbi.v3.core.Jdbi
import org.testcontainers.containers.PostgreSQLContainer

data class TestOverrides(
    val userRepository: UserRepository? = null,
    val messageCache: MessageCache? = null,
    val contactService: ContactService? = null,
    val notificationService: NotificationService? = null,
    val deviceTokenRepository: DeviceTokenRepository? = null,
    val pollService: PollService? = null,
)

abstract class WebTest protected constructor() {
    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        private val dataSource: DataSource by lazy {
            createDataSource(container.jdbcUrl, container.username, container.password).also { migrate(it) }
        }

        val testConfig =
            AppConfig(
                port = 0,
                jdbcUrl = container.jdbcUrl,
                jdbcUser = container.username,
                jdbcPassword = container.password,
                devDashboardEnabled = true,
                csrfEnabled = false,
                corsOrigins = "*",
                appleOAuth =
                    AppleOAuthConfig(
                        enabled = true,
                        clientId = "test.client.id",
                        teamId = "test.team",
                        keyId = "test.key",
                        privateKeyPem = "test.pem",
                    ),
            )

        val testJdbi: Jdbi by lazy { Jdbi.create(dataSource) }

        fun setup() {
        }

        fun cleanup() {
            testJdbi.useHandle<Exception> { handle ->
                handle.execute("DELETE FROM plt_sessions")
                handle.execute("DELETE FROM plt_notifications")
                handle.execute("DELETE FROM plt_device_tokens")
                handle.execute("DELETE FROM plt_oauth_connections")
                handle.execute("DELETE FROM plt_api_keys")
                handle.execute("DELETE FROM plt_password_reset_tokens")
                handle.execute("DELETE FROM plt_audit_log")
                handle.execute("DELETE FROM plt_outbox")
                handle.execute("DELETE FROM plt_contact_emails")
                handle.execute("DELETE FROM plt_contact_phones")
                handle.execute("DELETE FROM plt_contact_socials")
                handle.execute("DELETE FROM plt_contacts")
                handle.execute("DELETE FROM plt_messages")
                handle.execute("DELETE FROM plt_poll_votes")
                handle.execute("DELETE FROM plt_poll_options")
                handle.execute("DELETE FROM plt_polls")
                handle.execute("DELETE FROM plt_sync_state")
                handle.execute("DELETE FROM plt_users")
            }
        }

        val renderer by lazy { createRenderer() }
        val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
        val userRepository by lazy { JdbiUserRepository(testJdbi) }
        val messageRepository by lazy { JdbiMessageRepository(testJdbi) }
        val contactRepository by lazy { JdbiContactRepository(testJdbi) }
        val sessionRepository by lazy { JdbiSessionRepository(testJdbi) }
        val apiKeyRepository by lazy { JdbiApiKeyRepository(testJdbi) }
        val auditRepository by lazy { JdbiAuditRepository(testJdbi) }
        val notificationRepository by lazy { JdbiNotificationRepository(testJdbi) }
        val passwordResetRepository by lazy { JdbiPasswordResetRepository(testJdbi) }
        val oauthRepository by lazy { JdbiOAuthRepository(testJdbi) }
        val pollRepository by lazy { JdbiPollRepository(testJdbi) }
        val pollService by lazy { PollService(pollRepository) }

        fun buildApp(
            config: AppConfig = testConfig,
            securityService: SecurityService =
                SecurityService(
                    userRepository,
                    encoder,
                    sessionRepository = sessionRepository,
                    apiKeyRepository = apiKeyRepository,
                    resetRepository = passwordResetRepository,
                    auditRepository = auditRepository,
                ),
            overrides: TestOverrides = TestOverrides(),
        ): HttpHandler {
            val resolvedUserRepo = overrides.userRepository ?: this.userRepository
            val resolvedMessageCache = overrides.messageCache ?: StubMessageCache()
            val outbox = StubOutboxRepository()
            val txManager = StubTransactionManager()
            val messageService = MessageService(messageRepository, outbox, txManager, resolvedMessageCache)
            val resolvedContactService =
                overrides.contactService
                    ?: ContactService(
                        contactRepository,
                        transactionManager = txManager,
                        auditRepository = auditRepository,
                    )
            val pageFactory =
                WebPageFactory(
                    messageRepository,
                    messageService,
                    resolvedContactService,
                    securityService,
                    appleOAuthEnabled = true,
                )

            return app(
                    messageService,
                    resolvedContactService,
                    outbox,
                    resolvedMessageCache,
                    renderer,
                    pageFactory,
                    config,
                    securityService,
                    resolvedUserRepo,
                    deviceTokenRepository = overrides.deviceTokenRepository,
                    notificationService = overrides.notificationService,
                    pollService = overrides.pollService ?: pollService,
                )
                .http!!
        }
    }
}
```

Key changes from old WebTest.kt:
- `testDsl: DSLContext` → `testJdbi: Jdbi`
- All `JooqXxxRepository(testDsl)` → `JdbiXxxRepository(testJdbi)`
- Cleanup: `testDsl.execute(...)` → `testJdbi.useHandle<Exception> { handle -> handle.execute(...) }`
- Removed `org.jooq.*` imports, added `org.jdbi.v3.core.Jdbi` import
- Field name `testDsl` renamed to `testJdbi` (downstream tests that reference `testDsl` will be updated in Task 3)

- [ ] **Step 2: Verify compile**

Run:
```powershell
mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: FAIL (downstream tests still reference `testDsl` — that's OK, we fix them next)

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/test/kotlin/.../web/WebTest.kt
git commit -m "refactor(test): rewrite WebTest from jOOQ DSLContext to JDBI"
```

---

### Task 3: Migrate individual test files off `testDsl` / jOOQ

**Files:**
- Modify: `platform-web/src/test/kotlin/.../web/AuditLogIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/.../web/PasswordResetFlowIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/.../web/SessionTimeoutIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/.../web/UserManagementWebUiIntegrationTest.kt`
- Modify: `platform-web/src/test/kotlin/.../web/PlatformPageRenderingTest.kt`
- Modify: `platform-web/src/test/kotlin/.../web/PerformanceBenchmarkTest.kt`
- Modify: `platform-web/src/test/kotlin/.../security/SecurityIntegrationTest.kt`

Each file needs different changes. Read each file, identify the jOOQ usages, and replace them.

- [ ] **Step 1: Fix AuditLogIntegrationTest.kt**

Current usage: `testDsl.fetchCount(testDsl.selectFrom(DSL.table("plt_audit_log")))`, `testDsl.select(actionCol).from(auditTable).orderBy(...).fetchOne()?.get(actionCol)`, etc.

Replace all jOOQ DSL queries with JDBI handle queries. The test only needs:
- `auditCount()` → count rows in `plt_audit_log`
- `latestAction()` → get most recent `action` column
- `latestTargetUsername()` → get most recent `target_username` column

Replace the entire jOOQ query section:

```kotlin
import org.jdbi.v3.core.Jdbi

// Remove: import org.jooq.impl.DSL

// Replace the private helper methods:
private fun auditCount(): Int =
    testJdbi.open().createQuery("SELECT COUNT(*) FROM plt_audit_log").mapTo(Int::class.java).first()

private fun latestAction(): String? =
    testJdbi.open().createQuery("SELECT action FROM plt_audit_log ORDER BY created_at DESC LIMIT 1")
        .mapTo(String::class.java).findFirst().orElse(null)

private fun latestTargetUsername(): String? =
    testJdbi.open().createQuery("SELECT target_username FROM plt_audit_log ORDER BY created_at DESC LIMIT 1")
        .mapTo(String::class.java).findFirst().orElse(null)
```

Also remove these fields that are no longer needed:
```kotlin
// DELETE these lines:
private val auditTable = DSL.table("plt_audit_log")
private val actionCol = DSL.field("action", String::class.java)
private val targetUsernameCol = DSL.field("target_username", String::class.java)
```

- [ ] **Step 2: Fix PasswordResetFlowIntegrationTest.kt**

Current usage:
- Line 92: `testDsl.fetchCount(testDsl.selectFrom(org.jooq.impl.DSL.table("plt_password_reset_tokens")))`
- Line 100: same pattern
- Line 110: `testDsl.fetchValue("SELECT token FROM plt_password_reset_tokens WHERE user_id = ?", testUser.id)`

Replace:

```kotlin
// Line 92 area — replace testDsl.fetchCount(...) with:
val tokensBefore = testJdbi.open().createQuery("SELECT COUNT(*) FROM plt_password_reset_tokens")
    .mapTo(Int::class.java).first()

// Line 100 area — same pattern:
val tokensAfter = testJdbi.open().createQuery("SELECT COUNT(*) FROM plt_password_reset_tokens")
    .mapTo(Int::class.java).first()

// Line 110 area — replace testDsl.fetchValue(...) with:
val storedToken = testJdbi.open()
    .createQuery("SELECT token FROM plt_password_reset_tokens WHERE user_id = :id")
    .bind("id", testUser.id)
    .mapTo(String::class.java)
    .first()
```

- [ ] **Step 3: Fix SessionTimeoutIntegrationTest.kt**

Current usage:
- Line 49: `testDsl.execute("UPDATE plt_sessions SET expires_at = ...")`

Replace:
```kotlin
testJdbi.useHandle<Exception> { handle ->
    handle.execute(
        "UPDATE plt_sessions SET expires_at = CURRENT_TIMESTAMP - INTERVAL '2 hours'" +
            " WHERE user_id = '${expiredUser.id}'"
    )
}
```

- [ ] **Step 4: Fix UserManagementWebUiIntegrationTest.kt**

Current usage:
- Line 138: `testDsl.execute("UPDATE plt_sessions SET expires_at = ...")`

Replace:
```kotlin
testJdbi.useHandle<Exception> { handle ->
    handle.execute(
        "UPDATE plt_sessions SET expires_at = TIMESTAMP '2020-01-01 00:00:00' WHERE user_id = '${admin.id}'"
    )
}
```

- [ ] **Step 5: Fix PlatformPageRenderingTest.kt**

Current usage:
- Line 5: `import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository`
- Line 86: `val notificationService = NotificationService(JooqNotificationRepository(testDsl))`

Replace:
```kotlin
// Change import to:
import io.github.rygel.outerstellar.platform.persistence.JdbiNotificationRepository

// Change line 86 to:
val notificationService = NotificationService(JdbiNotificationRepository(testJdbi))
```

- [ ] **Step 6: Fix PerformanceBenchmarkTest.kt**

Current usage:
- Line 293: `testDsl.execute("DELETE FROM plt_sync_state")`

Replace:
```kotlin
testJdbi.useHandle<Exception> { handle -> handle.execute("DELETE FROM plt_sync_state") }
```

- [ ] **Step 7: Fix SecurityIntegrationTest.kt**

Current usage:
- Line 5: `import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository`
- Line 18: `private lateinit var userRepository: JooqUserRepository`
- Line 24: `userRepository = JooqUserRepository(testDsl)`

Replace:
```kotlin
// Change import to:
import io.github.rygel.outerstellar.platform.persistence.JdbiUserRepository

// Change field to:
private lateinit var userRepository: JdbiUserRepository

// Change line 24 to:
userRepository = JdbiUserRepository(testJdbi)
```

- [ ] **Step 8: Compile**

Run:
```powershell
mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: PASS (no more jOOQ references in test code)

- [ ] **Step 9: Commit**

```bash
git add platform-web/src/test/
git commit -m "refactor(test): migrate all web tests from jOOQ to JDBI"
```

---

### Task 4: Fix AdminWebModule.kt — remove hardcoded JooqNotificationRepository

**Files:**
- Modify: `platform-web/src/main/kotlin/.../di/AdminWebModule.kt`

- [ ] **Step 1: Update AdminWebModule.kt**

Current content:
```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import io.github.rygel.outerstellar.platform.security.AdminStatsService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import org.koin.dsl.module

val adminWebModule
    get() = module {
        single<NotificationRepository> { JooqNotificationRepository(get()) }
        single { NotificationService(get()) }
        single { AdminPageFactory(get(), get()) }
        single { AdminStatsService(get()) }
    }
```

Replace with (remove the explicit `NotificationRepository` registration — it's now in `PersistenceModule`):

```kotlin
package io.github.rygel.outerstellar.platform.di

import io.github.rygel.outerstellar.platform.security.AdminStatsService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.web.AdminPageFactory
import org.koin.dsl.module

val adminWebModule
    get() = module {
        single { NotificationService(get()) }
        single { AdminPageFactory(get(), get()) }
        single { AdminStatsService(get()) }
    }
```

- [ ] **Step 2: Compile**

Run:
```powershell
mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add platform-web/src/main/kotlin/.../di/AdminWebModule.kt
git commit -m "refactor: remove hardcoded JooqNotificationRepository from AdminWebModule"
```

---

### Task 5: Replace jooq dependencies with jdbi in all pom.xml files

**Files:**
- Modify: `platform-web/pom.xml`
- Modify: `platform-seeder/pom.xml`
- Modify: `platform-desktop/pom.xml`
- Modify: `platform-desktop-javafx/pom.xml`

In each of these 4 files, replace the jooq dependency with jdbi.

- [ ] **Step 1: Update platform-web/pom.xml**

Replace:
```xml
        <dependency>
            <groupId>io.github.rygel</groupId>
            <artifactId>outerstellar-platform-persistence-jooq</artifactId>
        </dependency>
```

With:
```xml
        <dependency>
            <groupId>io.github.rygel</groupId>
            <artifactId>outerstellar-platform-persistence-jdbi</artifactId>
        </dependency>
```

Also remove the direct jooq dependency (lines 85-88):
```xml
        <dependency>
            <groupId>org.jooq</groupId>
            <artifactId>jooq</artifactId>
        </dependency>
```

Replace it with JDBI:
```xml
        <dependency>
            <groupId>org.jdbi</groupId>
            <artifactId>jdbi3-core</artifactId>
        </dependency>
```

- [ ] **Step 2: Update platform-seeder/pom.xml**

Replace:
```xml
            <artifactId>outerstellar-platform-persistence-jooq</artifactId>
```

With:
```xml
            <artifactId>outerstellar-platform-persistence-jdbi</artifactId>
```

- [ ] **Step 3: Update platform-desktop/pom.xml**

Replace:
```xml
            <artifactId>outerstellar-platform-persistence-jooq</artifactId>
```

With:
```xml
            <artifactId>outerstellar-platform-persistence-jdbi</artifactId>
```

- [ ] **Step 4: Update platform-desktop-javafx/pom.xml**

Replace:
```xml
            <artifactId>outerstellar-platform-persistence-jooq</artifactId>
```

With:
```xml
            <artifactId>outerstellar-platform-persistence-jdbi</artifactId>
```

- [ ] **Step 5: Compile**

Run:
```powershell
mvn clean compile -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder -T4 -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add platform-web/pom.xml platform-seeder/pom.xml platform-desktop/pom.xml platform-desktop-javafx/pom.xml
git commit -m "refactor: replace jooq with jdbi dependency in all modules"
```

---

### Task 6: Remove jooq module from reactor and clean root pom.xml

**Files:**
- Modify: `pom.xml` (root)

- [ ] **Step 1: Remove module from reactor (line 41)**

Delete:
```xml
        <module>platform-persistence-jooq</module>
```

- [ ] **Step 2: Remove dependencyManagement entry (lines 198-202)**

Delete:
```xml
            <dependency>
                <groupId>io.github.rygel</groupId>
                <artifactId>outerstellar-platform-persistence-jooq</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 3: Remove jooq.version property (line 61)**

Delete:
```xml
        <jooq.version>3.21.4</jooq.version>
```

- [ ] **Step 4: Remove jooq dependency from dependencyManagement (lines 332-336)**

Delete:
```xml
            <dependency>
                <groupId>org.jooq</groupId>
                <artifactId>jooq</artifactId>
                <version>${jooq.version}</version>
            </dependency>
```

- [ ] **Step 5: Remove jooq-codegen extension from extensionManagement (lines 671-675)**

Delete:
```xml
                <extension>
                    <groupId>org.jooq</groupId>
                    <artifactId>jooq-codegen-maven</artifactId>
                    <version>${jooq.version}</version>
                </extension>
```

- [ ] **Step 6: Remove jooq from spinaker watch config (lines 940-942)**

Delete:
```xml
                        <watch>
                            <directory>platform-persistence-jooq/src/main/kotlin</directory>
                        </watch>
```

- [ ] **Step 7: Update project description (line 14)**

Change:
```xml
    <description>Opinionated app scaffold with http4k, jOOQ, JDBI, Flyway, JTE, and Koin</description>
```

To:
```xml
    <description>Opinionated app scaffold with http4k, JDBI, Flyway, JTE, and Koin</description>
```

- [ ] **Step 8: Compile**

Run:
```powershell
mvn clean compile -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder -T4 -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add pom.xml
git commit -m "refactor: remove jooq module from reactor, dependencyManagement, and extensions"
```

---

### Task 7: Update Dockerfiles

**Files:**
- Modify: `docker/Dockerfile`
- Modify: `docker/Dockerfile.native`
- Modify: `docker/Dockerfile.build` desktop-test target

- [ ] **Step 1: Update docker/Dockerfile**

Remove line 28:
```dockerfile
COPY platform-persistence-jooq/pom.xml platform-persistence-jooq/
```

- [ ] **Step 2: Update docker/Dockerfile.native**

Remove line 20:
```dockerfile
COPY platform-persistence-jooq/pom.xml platform-persistence-jooq/
```

- [ ] **Step 3: Update docker/Dockerfile.build desktop-test target**

Remove line 14:
```dockerfile
COPY platform-persistence-jooq/pom.xml platform-persistence-jooq/
```

- [ ] **Step 4: Commit**

```bash
git add docker/
git commit -m "refactor: remove jooq from Dockerfiles"
```

---

### Task 8: Update ArchitectureTest.kt

**Files:**
- Modify: `platform-core/src/test/kotlin/.../arch/ArchitectureTest.kt`

- [ ] **Step 1: Remove jooq package references**

Line 26: Change `"..persistence.jooq.."` → remove it from the package list.

```kotlin
// Before:
.resideInAnyPackage("..web..", "..desktop..", "..persistence.jooq..")

// After:
.resideInAnyPackage("..web..", "..desktop..")
```

Line 79: Remove `"..persistence.jooq.."` from the package list.

```kotlin
// Before:
.resideInAnyPackage("..persistence.jooq..", "..persistence.jdbi..", "..desktop..", "..web..")

// After:
.resideInAnyPackage("..persistence.jdbi..", "..desktop..", "..web..")
```

- [ ] **Step 2: Commit**

```bash
git add platform-core/src/test/kotlin/.../arch/ArchitectureTest.kt
git commit -m "refactor: remove jooq package references from ArchitectureTest"
```

---

### Task 9: Update documentation

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`

- [ ] **Step 1: Update AGENTS.md**

Remove all references to `platform-persistence-jooq`, jOOQ, jooq. Key sections to update:

1. Module Structure — remove the `platform-persistence-jooq` line
2. Repository pattern description — remove "(JooqXxxRepository)" references, keep "(JdbiXxxRepository)"
3. Test execution commands — remove jooq from module lists (already done per AGENTS.md)
4. Key Code Locations — remove jOOQ-specific entries
5. jOOQ and database schema rules section — simplify to just Flyway + schema rules
6. Any other mentions

Search for `jooq` case-insensitively and update every reference.

- [ ] **Step 2: Update README.md**

Remove all jOOQ references. Update the module list, technology stack, and any code examples that reference jOOQ.

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md README.md
git commit -m "docs: remove jooq references from documentation"
```

---

### Task 10: Delete `platform-persistence-jooq/` directory

**Files:**
- Delete: `platform-persistence-jooq/` (entire directory — 85 files)

- [ ] **Step 1: Delete the directory**

```powershell
Remove-Item -Recurse -Force platform-persistence-jooq
```

- [ ] **Step 2: Verify build still works without it**

```powershell
mvn clean compile -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder -T4 -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: delete platform-persistence-jooq module"
```

---

### Task 11: Run full test suite and verify

- [ ] **Step 1: Run full reactor build**

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder
```

Expected: ALL TESTS PASS

- [ ] **Step 2: Verify no jooq references remain**

```powershell
# Search for remaining jooq references in production code and tests
Get-ChildItem -Recurse -Include *.kt,*.java,*.xml,*.yml,*.yaml,*.md,*.sql,*.properties -Path . -Exclude target | Select-String -Pattern "jooq|jOOQ" -CaseSensitive:$false | Where-Object { $_.Path -notmatch "target" -and $_.Path -notmatch "docs/superpowers" }
```

Expected: zero matches (excluding plan/spec docs under docs/superpowers)

- [ ] **Step 3: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "chore: final jooq removal cleanup"
```
