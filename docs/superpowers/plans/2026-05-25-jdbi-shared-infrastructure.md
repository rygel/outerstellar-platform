# JDBI Shared Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate timestamp boilerplate, duplicate escapeLike/FilterClause, and inconsistent import patterns across 14 JDBI repositories by registering JDBI `ArgumentFactory`/`ColumnMapper` for `Instant` and extracting shared query-building utilities.

**Architecture:** Register a `SqlTimestampArgumentFactory` and `InstantColumnMapper` on the JDBI instance so all repositories can bind `Instant` values directly and read `Instant` columns without manual `Timestamp.from()`/`.toInstant()` conversion. Extract `escapeLike()` and `FilterClause` into a shared `JdbiSupport.kt` file. Add ResultSet extension helpers for the 3 null-handling patterns (nullable, required, default).

**Tech Stack:** Kotlin, JDBI 3, PostgreSQL 18, JUnit 5

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-persistence-jdbi/src/main/kotlin/.../persistence/JdbiSupport.kt` | ArgumentFactory, ColumnMapper, FilterClause, escapeLike, ResultSet extensions |
| Modify | `platform-persistence-jdbi/src/main/kotlin/.../di/PersistenceFactory.kt:78-83` | Register ArgumentFactory + ColumnMapper on JDBi instance |
| Modify | `platform-persistence-jdbi/src/main/kotlin/.../testing/TestDatabase.kt:29` | Register same on test JDBi instance |
| Modify | All 14 repository files | Remove manual Timestamp conversion, use shared FilterClause/escapeLike |
| Create | `platform-persistence-jdbi/src/test/kotlin/.../persistence/JdbiSupportTest.kt` | Unit tests for JdbiSupport utilities |

---

### Task 1: Create JdbiSupport.kt with Instant mapping and query utilities

**Files:**
- Create: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiSupport.kt`

- [ ] **Step 1: Write JdbiSupport.kt**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import org.jdbi.v3.core.argument.Argument
import org.jdbi.v3.core.argument.ArgumentFactory
import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.ColumnMapper
import org.jdbi.v3.core.statement.Query
import org.jdbi.v3.core.statement.StatementContext

data class FilterClause(val sql: String, val binder: (Query) -> Unit)

fun String.escapeLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")

fun ResultSet.getNullableInstant(column: String): Instant? = getTimestamp(column)?.toInstant()

fun ResultSet.getRequiredInstant(column: String): Instant =
    getTimestamp(column)?.toInstant() ?: error("$column is unexpectedly null")

fun ResultSet.getInstantOrDefault(column: String, default: Instant = Instant.now()): Instant =
    getTimestamp(column)?.toInstant() ?: default

class InstantArgumentFactory : ArgumentFactory {
    override fun build(type: Class<*>, value: Any, config: ConfigRegistry): Argument? {
        if (!Instant::class.java.isAssignableFrom(type)) return null
        val instant = value as Instant
        return Argument { position, stmt, _ -> stmt.setTimestamp(position, Timestamp.from(instant)) }
    }
}

class InstantColumnMapper : ColumnMapper<Instant> {
    override fun map(r: ResultSet, columnNumber: Int, ctx: StatementContext): Instant? = r.getTimestamp(columnNumber)?.toInstant()

    override fun map(r: ResultSet, columnLabel: String, ctx: StatementContext): Instant? = r.getTimestamp(columnLabel)?.toInstant()
}
```

- [ ] **Step 2: Compile to verify**

Run: `mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

---

### Task 2: Register ArgumentFactory and ColumnMapper on production JDBi

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/di/PersistenceFactory.kt:78-83`

- [ ] **Step 1: Add registration in PersistenceFactory.kt**

Change the JDBi creation block (line 78-83) from:

```kotlin
    val jdbi =
        Jdbi.create(ds).also {
            if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                Metrics.globalRegistry.gauge("database.connections.active", 1)
            }
        }
```

To:

```kotlin
    val jdbi =
        Jdbi.create(ds).also {
            it.registerArgument(io.github.rygel.outerstellar.platform.persistence.InstantArgumentFactory())
            it.registerColumnMapper(io.github.rygel.outerstellar.platform.persistence.InstantColumnMapper())
            if (Metrics.globalRegistry.find("database.connections.active").gauge() == null) {
                Metrics.globalRegistry.gauge("database.connections.active", 1)
            }
        }
```

Also register on the seed-only JDBi instance (line 75) to keep it consistent:

```kotlin
    if (config.devMode) {
        val seedJdbi = Jdbi.create(ds)
        seedJdbi.registerArgument(io.github.rygel.outerstellar.platform.persistence.InstantArgumentFactory())
        JdbiUserRepository(seedJdbi).seedAdminUser(DEV_ADMIN_PLACEHOLDER_HASH)
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

---

### Task 3: Register ArgumentFactory and ColumnMapper on test JDBi

**Files:**
- Modify: `platform-test-infrastructure/src/main/kotlin/io/github/rygel/outerstellar/platform/testing/TestDatabase.kt:29`

- [ ] **Step 1: Update TestDatabase.kt**

Add import for `InstantArgumentFactory` and `InstantColumnMapper`, then update the `jdbi` lazy property:

```kotlin
import io.github.rygel.outerstellar.platform.persistence.InstantArgumentFactory
import io.github.rygel.outerstellar.platform.persistence.InstantColumnMapper
```

Change line 29 from:

```kotlin
    val jdbi: Jdbi by lazy { Jdbi.create(dataSource) }
```

To:

```kotlin
    val jdbi: Jdbi by lazy {
        Jdbi.create(dataSource).also {
            it.registerArgument(InstantArgumentFactory())
            it.registerColumnMapper(InstantColumnMapper())
        }
    }
```

- [ ] **Step 2: Compile both modules**

Run: `mvn -pl platform-test-infrastructure,platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

---

### Task 4: Migrate JdbiSessionRepository — remove Timestamp imports and manual conversion

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiSessionRepository.kt`

This repository is a clean representative: it imports `java.sql.Timestamp`, uses `Timestamp.from()` for writes and `?.toInstant() ?: error(...)` for reads.

- [ ] **Step 1: Remove `java.sql.Timestamp` import, add `getRequiredInstant` import**

Remove:
```kotlin
import java.sql.Timestamp
```

The file already imports `java.time.Instant` and `java.util.UUID`. Add:

```kotlin
import io.github.rygel.outerstellar.platform.persistence.getRequiredInstant
```

- [ ] **Step 2: Replace write-side `Timestamp.from()` calls**

Line 22: `.bind("createdAt", Timestamp.from(session.createdAt))` → `.bind("createdAt", session.createdAt)`

Line 23: `.bind("expiresAt", Timestamp.from(session.expiresAt))` → `.bind("expiresAt", session.expiresAt)`

Line 38: `.bind("now", Timestamp.from(Instant.now()))` → `.bind("now", Instant.now())`

Line 62: `.bind("expiresAt", Timestamp.from(expiresAt))` → `.bind("expiresAt", expiresAt)`

Line 87: `.bind("now", Timestamp.from(Instant.now()))` → `.bind("now", Instant.now())`

- [ ] **Step 3: Replace read-side timestamp mapping**

Change `mapSession` (lines 92-102) from:

```kotlin
    private fun mapSession(rs: java.sql.ResultSet): Session {
        val createdAt = rs.getTimestamp("created_at")
        val expiresAt = rs.getTimestamp("expires_at")
        return Session(
            id = rs.getLong("id"),
            tokenHash = rs.getString("token_hash"),
            userId = rs.getObject("user_id", UUID::class.java),
            createdAt = createdAt?.toInstant() ?: error("plt_sessions.created_at is unexpectedly null"),
            expiresAt = expiresAt?.toInstant() ?: error("plt_sessions.expires_at is unexpectedly null"),
        )
    }
```

To:

```kotlin
    private fun mapSession(rs: java.sql.ResultSet): Session {
        return Session(
            id = rs.getLong("id"),
            tokenHash = rs.getString("token_hash"),
            userId = rs.getObject("user_id", UUID::class.java),
            createdAt = rs.getRequiredInstant("created_at"),
            expiresAt = rs.getRequiredInstant("expires_at"),
        )
    }
```

- [ ] **Step 4: Compile**

Run: `mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 5: Run SessionRepository tests**

Run: `mvn -pl platform-persistence-jdbi -am test "-Dtest=JdbiSessionRepositoryTest"`
Expected: All tests pass

---

### Task 5: Migrate JdbiUserRepository

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiUserRepository.kt`

- [ ] **Step 1: Add imports, remove FQCN usage**

Add:
```kotlin
import io.github.rygel.outerstellar.platform.persistence.getNullableInstant
```

- [ ] **Step 2: Replace write-side `java.sql.Timestamp.from()` (line 143)**

`.bind("lastActivity", java.sql.Timestamp.from(java.time.Instant.now()))` → `.bind("lastActivity", Instant.now())`

- [ ] **Step 3: Replace read-side timestamp mapping (lines 297-319)**

Change `mapUser` from:

```kotlin
    private fun mapUser(rs: java.sql.ResultSet): User {
        val lastActivity = rs.getTimestamp("last_activity_at")
        return User(
            ...
            lockedUntil = rs.getTimestamp("locked_until")?.toInstant(),
            lastActivityAt = lastActivity?.toInstant(),
            ...
        )
    }
```

To:

```kotlin
    private fun mapUser(rs: java.sql.ResultSet): User {
        return User(
            ...
            lockedUntil = rs.getNullableInstant("locked_until"),
            lastActivityAt = rs.getNullableInstant("last_activity_at"),
            ...
        )
    }
```

- [ ] **Step 4: Compile and test**

Run: `mvn -pl platform-persistence-jdbi -am test "-Dtest=JdbiUserRepositoryTest"`
Expected: All tests pass

---

### Task 6: Migrate JdbiNotificationRepository

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiNotificationRepository.kt`

- [ ] **Step 1: Add import**

```kotlin
import io.github.rygel.outerstellar.platform.persistence.getNullableInstant
import io.github.rygel.outerstellar.platform.persistence.getInstantOrDefault
```

- [ ] **Step 2: Replace write-side FQCN Timestamp.from()**

Line 23: `.bind("createdAt", java.sql.Timestamp.from(notification.createdAt))` → `.bind("createdAt", notification.createdAt)`

Line 55: `.bind("readAt", java.sql.Timestamp.from(java.time.Instant.now()))` → `.bind("readAt", Instant.now())`

Line 68: `.bind("readAt", java.sql.Timestamp.from(java.time.Instant.now()))` → `.bind("readAt", Instant.now())`

- [ ] **Step 3: Replace read-side mapping**

```kotlin
    private fun mapNotification(rs: java.sql.ResultSet): Notification {
        return Notification(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            title = rs.getString("title"),
            body = rs.getString("body"),
            type = rs.getString("type") ?: "info",
            readAt = rs.getNullableInstant("read_at"),
            createdAt = rs.getInstantOrDefault("created_at"),
        )
    }
```

- [ ] **Step 4: Compile and test**

Run: `mvn -pl platform-persistence-jdbi -am test "-Dtest=JdbiNotificationRepositoryTest"`
Expected: All tests pass

---

### Task 7: Migrate remaining repositories (bulk migration)

The remaining 11 repositories each follow the same patterns. Process each one:

**Files to modify (all in `platform-persistence-jdbi/src/main/kotlin/.../persistence/`):**
- `JdbiApiKeyRepository.kt` — 2 FQCN Timestamp.from() writes, nullable reads
- `JdbiAuditRepository.kt` — FQCN Timestamp.from() writes, default-to-now reads
- `JdbiDeviceTokenRepository.kt` — 1 FQCN Timestamp.from() write
- `JdbiOAuthRepository.kt` — no timestamps (verify first)
- `JdbiOutboxRepository.kt` — 1 FQCN Timestamp.from() write, assumed-non-null read
- `JdbiPasswordResetRepository.kt` — 1 Timestamp.from() write (has proper import), assumed-non-null read
- `JdbiPollRepository.kt` — 7 Timestamp.from() writes, required-error reads
- `JdbiVoteRepository.kt` — 1 Timestamp.from() write, required-error read
- `JdbiMessageRepository.kt` — no Timestamp writes (uses epoch_ms), but has escapeLike/FilterClause
- `JdbiContactRepository.kt` — no Timestamp writes (uses epoch_ms), but has escapeLike/FilterClause
- `JdbiTransactionManager.kt` — no timestamps (verify)

**For each repository with Timestamp writes:**
1. Remove `import java.sql.Timestamp` if present
2. Add appropriate import: `getNullableInstant`, `getRequiredInstant`, or `getInstantOrDefault`
3. Replace `.bind("col", Timestamp.from(...))` → `.bind("col", ...)` (Instant value directly)
4. Replace `.bind("col", java.sql.Timestamp.from(java.time.Instant.now()))` → `.bind("col", Instant.now())`
5. Replace read-side `rs.getTimestamp("col")?.toInstant() ?: error(...)` → `rs.getRequiredInstant("col")`
6. Replace read-side `rs.getTimestamp("col")?.toInstant()` → `rs.getNullableInstant("col")`
7. Replace read-side `rs.getTimestamp("col")?.toInstant() ?: Instant.now()` → `rs.getInstantOrDefault("col")`
8. Replace read-side `rs.getTimestamp("col").toInstant()` (assumed non-null) → `rs.getRequiredInstant("col")`

- [ ] **Step 1: Migrate JdbiApiKeyRepository.kt**
- [ ] **Step 2: Migrate JdbiAuditRepository.kt**
- [ ] **Step 3: Migrate JdbiDeviceTokenRepository.kt**
- [ ] **Step 4: Migrate JdbiOutboxRepository.kt**
- [ ] **Step 5: Migrate JdbiPasswordResetRepository.kt**
- [ ] **Step 6: Migrate JdbiPollRepository.kt**
- [ ] **Step 7: Migrate JdbiVoteRepository.kt**
- [ ] **Step 8: Compile all**

Run: `mvn -pl platform-persistence-jdbi compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"`
Expected: BUILD SUCCESS

- [ ] **Step 9: Run all persistence tests**

Run: `mvn -pl platform-persistence-jdbi -am test`
Expected: All tests pass

---

### Task 8: Extract shared FilterClause and escapeLike from Message and Contact repos

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/.../persistence/JdbiMessageRepository.kt:384-431`
- Modify: `platform-persistence-jdbi/src/main/kotlin/.../persistence/JdbiContactRepository.kt:541-644`

- [ ] **Step 1: Remove private FilterClause and escapeLike from JdbiMessageRepository.kt**

Delete lines 384 (`private data class FilterClause(...)`) and 431 (`private fun String.escapeLike()...`).

Add import: `import io.github.rygel.outerstellar.platform.persistence.FilterClause` and `import io.github.rygel.outerstellar.platform.persistence.escapeLike`

The `buildFilterClause` method (lines 386-411) stays as-is since it's specific to message filtering. Only the data class and extension function move to the shared location.

- [ ] **Step 2: Remove private FilterClause and escapeLike from JdbiContactRepository.kt**

Delete lines 541 (`private data class FilterClause(...)`) and 644 (`private fun String.escapeLike()...`).

Add import: `import io.github.rygel.outerstellar.platform.persistence.FilterClause` and `import io.github.rygel.outerstellar.platform.persistence.escapeLike`

- [ ] **Step 3: Compile and test**

Run: `mvn -pl platform-persistence-jdbi -am test "-Dtest=JdbiMessageRepositoryTest,JdbiContactRepositoryTest"`
Expected: All tests pass

---

### Task 9: Write unit tests for JdbiSupport utilities

**Files:**
- Create: `platform-persistence-jdbi/src/test/kotlin/io/github/rygel/outerstellar/platform/persistence/JdbiSupportTest.kt`

- [ ] **Step 1: Write tests**

```kotlin
package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.sql.Timestamp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JdbiSupportTest {

    @Test
    fun `escapeLike escapes special characters`() {
        assertThat("hello%world_test!".escapeLike()).isEqualTo("hello!%world!_test!!")
    }

    @Test
    fun `escapeLike handles empty string`() {
        assertThat("".escapeLike()).isEmpty()
    }

    @Test
    fun `escapeLike handles string with no special characters`() {
        assertThat("hello".escapeLike()).isEqualTo("hello")
    }

    @Test
    fun `InstantArgumentFactory handles Instant values`() {
        val factory = InstantArgumentFactory()
        val instant = Instant.now()
        assertThat(factory.build(Instant::class.java, instant, org.jdbi.v3.core.config.ConfigRegistry())).isNotNull
    }

    @Test
    fun `InstantArgumentFactory returns null for non-Instant types`() {
        val factory = InstantArgumentFactory()
        assertThat(factory.build(String::class.java, "hello", org.jdbi.v3.core.config.ConfigRegistry())).isNull()
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn -pl platform-persistence-jdbi -am test "-Dtest=JdbiSupportTest"`
Expected: All tests pass

---

### Task 10: Full reactor verification and commit

- [ ] **Step 1: Run full reactor build**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed`
Expected: BUILD SUCCESS, all 615+ tests pass

- [ ] **Step 2: Verify no remaining manual Timestamp conversions**

Run: `rg "Timestamp\.from" platform-persistence-jdbi/src/main/`
Expected: Zero matches (all converted)

Run: `rg "getTimestamp" platform-persistence-jdbi/src/main/`
Expected: Zero matches in repository files (all converted to extension helpers)

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: extract shared JDBI infrastructure (Instant mapping, FilterClause, escapeLike)

- Register InstantArgumentFactory + InstantColumnMapper on JDBi instances
- Add ResultSet extension helpers (getNullableInstant, getRequiredInstant, getInstantOrDefault)
- Extract FilterClause data class and escapeLike() into JdbiSupport.kt
- Remove ~40 lines of manual Timestamp.from()/.toInstant() boilerplate across 10 repositories
- Consistent import patterns: no more mixed FQCN/import Timestamp usage"
```
