# Shared PostgreSQL Container Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-module PostgreSQL containers with one reused container, each test class gets its own database.

**Architecture:** New `platform-testkit` module provides `SharedPostgres` (singleton reused container) and `TestDatabase` (per-class database with Flyway). `WebTest` and `JdbiTest` consume it. All `@AfterEach` cleanup deleted. `@TestInstance(PER_CLASS)` enables per-class database lifecycle.

**Tech Stack:** Testcontainers 2.0.5 (reuse mode), JDBI 3.53.0, Flyway 12.6.2, PostgreSQL 18, JUnit 6.1.0

**Spec:** `docs/superpowers/specs/2026-05-22-shared-postgres-container-design.md`

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-testkit/pom.xml` | Maven module definition |
| Create | `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/SharedPostgres.kt` | Singleton reused container + CREATE/DROP DATABASE |
| Create | `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/TestDatabase.kt` | Per-class database handle (jdbcUrl, Jdbi, DataSource) |
| Create | `platform-testkit/src/main/resources/.testcontainers.properties` | `testcontainers.reuse.enable=true` |
| Modify | `pom.xml` (root) | Add `platform-testkit` to `<modules>` |
| Modify | `platform-persistence-jdbi/pom.xml` | Add test-infrastructure dependency, remove direct testcontainers deps |
| Modify | `platform-web/pom.xml` | Add test-infrastructure dependency, remove direct testcontainers deps |
| Modify | `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiTest.kt` | Use SharedPostgres, PER_CLASS, remove cleanup |
| Modify | `platform-web/src/test/kotlin/.../web/WebTest.kt` | Use SharedPostgres, PER_CLASS, remove cleanup |
| Modify | 55 WebTest subclasses | Remove `@AfterEach fun reset() = cleanup()` |
| Delete | `platform-persistence-jdbi/src/main/kotlin/.../persistence/CleanupTables.kt` | No longer needed |

---

### Task 1: Create platform-testkit module

**Files:**
- Create: `platform-testkit/pom.xml`
- Modify: `pom.xml` (root, `<modules>` section, line ~48)

- [ ] **Step 1: Create the module directory**

```powershell
New-Item -ItemType Directory -Path "platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing" -Force
New-Item -ItemType Directory -Path "platform-testkit/src/main/resources" -Force
```

- [ ] **Step 2: Create pom.xml**

Create `platform-testkit/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.rygel</groupId>
        <artifactId>outerstellar-platform-parent</artifactId>
        <version>1.6.2-SNAPSHOT</version>
    </parent>

    <artifactId>outerstellar-platform-testkit</artifactId>
    <name>Platform Test Infrastructure</name>

    <dependencies>
        <dependency>
            <groupId>io.github.rygel</groupId>
            <artifactId>outerstellar-platform-persistence-jdbi</artifactId>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.jdbi</groupId>
            <artifactId>jdbi3-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <extensions>
            <extension>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
            </extension>
            <extension>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>test-jar</goal>
                        </goals>
                    </execution>
                </executions>
            </extension>
        </extensions>
    </build>
</project>
```

- [ ] **Step 3: Add module to root pom.xml**

In `pom.xml` (root), add after the `platform-seeder` line in `<modules>` (line ~47):

```xml
        <module>platform-testkit</module>
```

- [ ] **Step 4: Create .testcontainers.properties**

Create `platform-testkit/src/main/resources/.testcontainers.properties`:

```
testcontainers.reuse.enable=true
```

- [ ] **Step 5: Compile to verify module resolves**

```powershell
mvn -pl platform-testkit compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS (no Kotlin files yet, but POM resolves)

- [ ] **Step 6: Commit**

```bash
git add platform-testkit/ pom.xml
git commit -m "build: scaffold platform-testkit module"
```

---

### Task 2: Implement SharedPostgres and TestDatabase

**Files:**
- Create: `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/SharedPostgres.kt`
- Create: `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/TestDatabase.kt`

- [ ] **Step 1: Create SharedPostgres.kt**

Create `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/SharedPostgres.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.testing

import java.sql.DriverManager
import org.testcontainers.containers.PostgreSQLContainer

object SharedPostgres {
    private val container: PostgreSQLContainer<*> =
        PostgreSQLContainer("postgres:18")
            .withDatabaseName("outerstellar_base")
            .withUsername("outerstellar")
            .withPassword("outerstellar")
            .withReuse(true)
            .apply { start() }

    fun createDatabase(name: String): TestDatabase {
        val dbName = "test_${sanitizeDbName(name)}"
        DriverManager
            .getConnection(container.jdbcUrl, container.username, container.password)
            .use { conn -> conn.createStatement().execute("CREATE DATABASE \"$dbName\"") }
        val jdbcUrl = container.jdbcUrl.replaceAfterLast('/', "$dbName")
        return TestDatabase(dbName, jdbcUrl, container.username, container.password)
    }

    fun dropDatabase(dbName: String) {
        DriverManager
            .getConnection(container.jdbcUrl, container.username, container.password)
            .use { conn ->
                conn.createStatement().execute("DROP DATABASE IF EXISTS \"$dbName\"")
            }
    }
}

fun sanitizeDbName(className: String): String =
    className
        .lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .take(58)
```

- [ ] **Step 2: Create TestDatabase.kt**

Create `platform-testkit/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/TestDatabase.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.testing

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import javax.sql.DataSource
import org.jdbi.v3.core.Jdbi

class TestDatabase(
    val dbName: String,
    val jdbcUrl: String,
    val jdbcUser: String,
    val jdbcPassword: String,
) {
    val dataSource: DataSource by lazy {
        createDataSource(jdbcUrl, jdbcUser, jdbcPassword).also { migrate(it) }
    }

    val jdbi: Jdbi by lazy { Jdbi.create(dataSource) }

    fun drop() {
        SharedPostgres.dropDatabase(dbName)
    }
}
```

- [ ] **Step 3: Compile**

```powershell
mvn -pl platform-testkit -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add platform-testkit/
git commit -m "feat(test): add SharedPostgres and TestDatabase for per-class databases"
```

---

### Task 3: Rewrite JdbiTest to use SharedPostgres

**Files:**
- Modify: `platform-persistence-jdbi/pom.xml`
- Modify: `platform-persistence-jdbi/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiTest.kt`

This is the simpler of the two base classes (13 test classes, no HTTP layer). Proves the SharedPostgres approach works before touching WebTest.

- [ ] **Step 1: Add test-infrastructure dependency to platform-persistence-jdbi/pom.xml**

In `platform-persistence-jdbi/pom.xml`, add after the `outerstellar-platform-core` test-jar dependency (after line ~91):

```xml
        <dependency>
            <groupId>io.github.rygel</groupId>
            <artifactId>outerstellar-platform-testkit</artifactId>
            <version>${project.version}</version>
            <classifier>tests</classifier>
            <scope>test</scope>
        </dependency>
```

Also remove the direct testcontainers dependencies (they're now transitive via test-infrastructure):

```xml
        <!-- REMOVE these two -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Rewrite JdbiTest.kt**

Replace entire `platform-persistence-jdbi/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiTest.kt` with:

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.util.UUID
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class JdbiTest {
    private val testDb =
        SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))

    protected val jdbi: Jdbi by lazy { testDb.jdbi }

    protected fun createUser(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
    ): UUID {
        val id = UUID.randomUUID()
        JdbiUserRepository(jdbi)
            .save(
                User(
                    id = id,
                    username = username,
                    email = "${id.toString().take(6)}@example.com",
                    passwordHash = "hash",
                    role = role,
                )
            )
        return id
    }

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
```

Changes from original:
- Removed `companion object` (container, sharedJdbi)
- Removed `@BeforeEach setupDatabase()` (lateinit assignment)
- Removed `@AfterEach cleanDatabase()` (DELETE chain)
- Added `@TestInstance(PER_CLASS)`
- Added `@AfterAll tearDown()` (drops database)
- `jdbi` changed from `lateinit var` to `val by lazy`

- [ ] **Step 3: Run platform-persistence-jdbi tests**

```powershell
mvn -pl platform-persistence-jdbi -am test -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: All 13 JdbiTest subclasses pass. Each creates its own database, Flyway migrates it, tests run, database is dropped.

- [ ] **Step 4: Commit**

```bash
git add platform-persistence-jdbi/
git commit -m "refactor(test): rewrite JdbiTest with per-class databases via SharedPostgres"
```

---

### Task 4: Rewrite WebTest to use SharedPostgres

**Files:**
- Modify: `platform-web/pom.xml`
- Modify: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt`

This is the big one — 55 test classes depend on WebTest. The rewrite moves all state from companion object to instance level.

- [ ] **Step 1: Add test-infrastructure dependency to platform-web/pom.xml**

In `platform-web/pom.xml`, add after the `outerstellar-platform-security` dependency:

```xml
        <dependency>
            <groupId>io.github.rygel</groupId>
            <artifactId>outerstellar-platform-testkit</artifactId>
            <version>${project.version}</version>
            <classifier>tests</classifier>
            <scope>test</scope>
        </dependency>
```

Also remove the direct testcontainers dependencies:

```xml
        <!-- REMOVE these two -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Rewrite WebTest.kt**

Replace entire `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt` with:

```kotlin
package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.app
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
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.util.UUID
import org.http4k.core.HttpHandler
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance

data class TestOverrides(
    val userRepository: UserRepository? = null,
    val messageCache: MessageCache? = null,
    val contactService: ContactService? = null,
    val notificationService: NotificationService? = null,
    val deviceTokenRepository: DeviceTokenRepository? = null,
    val pollService: PollService? = null,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WebTest {
    private val testDb =
        SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))

    val testConfig by lazy {
        AppConfig(
            port = 0,
            jdbcUrl = testDb.jdbcUrl,
            jdbcUser = testDb.jdbcUser,
            jdbcPassword = testDb.jdbcPassword,
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
    }

    val testJdbi: Jdbi by lazy { testDb.jdbi }
    val renderer by lazy { createRenderer() }
    val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
    val testPasswordHash by lazy { encoder.encode("Test@12345678") }

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

    fun createSecurityService(userRepository: UserRepository = this.userRepository): SecurityService =
        SecurityService(
            userRepository,
            encoder,
            sessionRepository = sessionRepository,
            apiKeyRepository = apiKeyRepository,
            resetRepository = passwordResetRepository,
            auditRepository = auditRepository,
        )

    fun withAuthenticatedUser(
        username: String = "testuser_" + UUID.randomUUID().toString().take(8),
        passwordHash: String = testPasswordHash,
        role: String = "USER",
    ): Triple<String, String, String> {
        val userId = UUID.randomUUID()
        val user =
            io.github.rygel.outerstellar.platform.model.User(
                id = userId,
                username = username,
                email = "$username@test.com",
                passwordHash = passwordHash,
                role = io.github.rygel.outerstellar.platform.model.UserRole.valueOf(role),
            )
        userRepository.save(user)
        val token = createSecurityService().createSession(userId)
        return Triple(token, userId.toString(), username)
    }

    fun buildApp(
        config: AppConfig = testConfig,
        securityService: SecurityService = createSecurityService(),
        overrides: TestOverrides = TestOverrides(),
    ): HttpHandler {
        val resolvedUserRepo = overrides.userRepository ?: this.userRepository
        val resolvedMessageCache = overrides.messageCache ?: StubMessageCache()
        val outbox = StubOutboxRepository()
        val txManager = StubTransactionManager()
        val messageService =
            MessageService(messageRepository, outbox, txManager, resolvedMessageCache)
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

    fun cleanup() {}

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
```

Key changes from original:
- **Removed** `companion object` entirely
- **Added** `@TestInstance(PER_CLASS)` on the class
- **Moved** all state (testConfig, testJdbi, repos, etc.) to instance level
- **Removed** `@AfterEach resetState()` — no cleanup needed
- **Added** `@AfterAll tearDown()` — drops database
- `cleanup()` kept as empty function for backward compatibility
- All imports updated: removed `javax.sql.DataSource`, `AfterEach`, `PostgreSQLContainer`; added `testing.SharedPostgres`, `testing.sanitizeDbName`, `AfterAll`, `TestInstance`
- `createRenderer()` call unchanged — still uses `io.github.rygel.outerstellar.platform.infra.createRenderer`

- [ ] **Step 3: Compile platform-web**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: Compile errors from `@AfterEach fun reset() = cleanup()` — the `cleanup()` call still works (empty function), but `@AfterEach` import may cause issues. These are fixed in Task 5.

Actually, `@AfterEach` is still valid — it just calls an empty function. Compilation should succeed. If `CleanupTables` import is still in WebTest, remove it.

Expected: BUILD SUCCESS

- [ ] **Step 4: Run a single test to verify**

```powershell
mvn -pl platform-web -am test -Dtest=HealthCheckIntegrationTest -Dexec.skip=true
```

Expected: PASS — HealthCheckIntegrationTest creates its own database, Flyway migrates it, test runs, database drops.

- [ ] **Step 5: Commit**

```bash
git add platform-web/pom.xml platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/WebTest.kt
git commit -m "refactor(test): rewrite WebTest with per-class databases via SharedPostgres"
```

---

### Task 5: Remove @AfterEach cleanup from all WebTest subclasses

**Files:**
- Modify: All 55 test classes under `platform-web/src/test/kotlin/`

Every WebTest subclass has one of these patterns:

```kotlin
    @AfterEach
    fun reset() = cleanup()
```

or:

```kotlin
    @AfterEach
    fun resetState() = cleanup()
```

Some may also import `org.junit.jupiter.api.AfterEach` only for this method.

- [ ] **Step 1: Find all @AfterEach cleanup calls**

```powershell
rg "@AfterEach" platform-web/src/test/kotlin/ --files-with-matches
rg "fun reset.*cleanup" platform-web/src/test/kotlin/ --files-with-matches
```

- [ ] **Step 2: Remove @AfterEach cleanup methods from all WebTest subclasses**

For each file found:
1. Remove the `@AfterEach` annotation and the `fun reset() = cleanup()` or `fun resetState() = cleanup()` method (typically 2-3 lines)
2. If `cleanup()` is the ONLY use of `@AfterEach` in the file, remove the `import org.junit.jupiter.api.AfterEach` import
3. Do NOT remove any other `@AfterEach` methods that do actual work (unlikely but check)

- [ ] **Step 3: Compile**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Run all platform-web tests**

```powershell
mvn -pl platform-web -am test -Dexec.skip=true
```

Expected: All tests pass. Each class creates its own database, runs tests, drops database.

- [ ] **Step 5: Commit**

```bash
git add platform-web/src/test/
git commit -m "refactor(test): remove @AfterEach cleanup from WebTest subclasses"
```

---

### Task 6: Delete CleanupTables and update Surefire config

**Files:**
- Delete: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CleanupTables.kt`
- Modify: `platform-web/pom.xml` — remove `parallel>none` Surefire override (now safe to run parallel since each class has its own DB)

- [ ] **Step 1: Verify CleanupTables is not used anywhere else**

```powershell
rg "CleanupTables" --type kotlin
```

Expected: Only references are in `CleanupTables.kt` itself, docs, and the design spec. No production or test code references.

- [ ] **Step 2: Delete CleanupTables.kt**

```powershell
Remove-Item "platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/CleanupTables.kt"
```

- [ ] **Step 3: Enable parallel test execution in platform-web**

In `platform-web/pom.xml`, the Surefire override currently forces `parallel=none`:

```xml
            <!-- Web tests share a PostgreSQL database — parallel execution would cause races -->
            <extension>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <parallel>none</parallel>
                    <systemPropertyVariables>
                        <jte.production>true</jte.production>
                    </systemPropertyVariables>
                </configuration>
            </extension>
```

Change to:

```xml
            <extension>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <parallel>classes</parallel>
                    <threadCount>4</threadCount>
                    <systemPropertyVariables>
                        <jte.production>true</jte.production>
                    </systemPropertyVariables>
                </configuration>
            </extension>
```

Each test class now has its own database — parallel execution is safe.

- [ ] **Step 4: Compile**

```powershell
mvn -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Full reactor test**

```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seeder
```

Expected: All tests pass (now running platform-web in parallel). Build success.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(test): delete CleanupTables, enable parallel test execution in platform-web"
```

---

### Task 7: Update documentation

**Files:**
- Modify: `docs/testing.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Update docs/testing.md**

Update the "Test Base Classes" section to reflect the new architecture:

- WebTest description: no longer mentions `@AfterEach cleanup()`, now mentions per-class databases and `@TestInstance(PER_CLASS)`
- JdbiTest description: same treatment
- Update the "Container Caching" section to describe `SharedPostgres` with reuse mode
- Update the code examples to remove cleanup
- Update the "Anti-Patterns" section: remove "Never create your own PostgreSQL container" (now they should use `SharedPostgres`)

- [ ] **Step 2: Update AGENTS.md**

Update the "Testing expectations" section to mention:
- `SharedPostgres` is the single source of PostgreSQL containers
- Per-class databases via `TestDatabase`
- `@TestInstance(PER_CLASS)` on WebTest and JdbiTest
- Remove references to `CleanupTables.ALL`
- Update the Maven command examples to include `platform-testkit` in the module list

- [ ] **Step 3: Commit**

```bash
git add docs/testing.md AGENTS.md
git commit -m "docs: update testing docs for shared container and per-class databases"
```

---

## Self-Review Checklist

### Spec coverage
- [x] New module `platform-testkit` — Task 1
- [x] `SharedPostgres` singleton with reuse — Task 2
- [x] `TestDatabase` per-class handle — Task 2
- [x] `sanitizeDbName()` utility — Task 2
- [x] `.testcontainers.properties` — Task 1
- [x] JdbiTest rewrite with PER_CLASS — Task 3
- [x] WebTest rewrite with PER_CLASS — Task 4
- [x] Remove @AfterEach cleanup from subclasses — Task 5
- [x] Delete CleanupTables — Task 6
- [x] Enable parallel in platform-web — Task 6
- [x] Update docs — Task 7
- [x] E2E/special tests not affected — covered in spec, excluded from plan

### Placeholder scan
- No TBD, TODO, or "implement later" found
- No "add appropriate error handling" vagueness
- All code blocks contain complete implementations
- All file paths are exact

### Type consistency
- `sanitizeDbName()` returns `String`, consumed by `SharedPostgres.createDatabase(name: String)` — consistent
- `TestDatabase` has `dbName`, `jdbcUrl`, `jdbcUser`, `jdbcPassword` — used consistently in WebTest and JdbiTest
- `SharedPostgres.dropDatabase()` takes `dbName: String` — called via `testDb.drop()` which passes `this.dbName` — consistent
- `createSecurityService()` signature unchanged from original — safe
- `buildApp()` signature unchanged from original — safe
- `withAuthenticatedUser()` signature unchanged — safe
