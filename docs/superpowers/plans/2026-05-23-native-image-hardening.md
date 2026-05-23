# Native-Image Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add build-time validation and runtime self-checks that prevent native-image packaging drift from reaching production.

**Architecture:** Three layers: (1) a build-time test that compares actual classpath resources against `reachability-metadata.json`, (2) a PowerShell script that auto-generates the resource entries from actual files, (3) a runtime diagnostic on native-image startup that validates critical resources are accessible.

**Tech Stack:** Kotlin/JUnit 5 (tests), PowerShell (resource generation script), Kotlin (runtime self-check)

**Issues:** #327, #325, #328

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `platform-web/src/test/kotlin/.../nativeimage/NativeResourceDriftTest.kt` | Build-time test: validates reachability-metadata.json resources vs actual files |
| Create | `scripts/generate-reachability-resources.ps1` | Auto-generates the `"resources"` array entries from classpath files |
| Modify | `platform-web/src/main/resources/META-INF/native-image/.../reachability-metadata.json` | Fix V13 drift, add missing entries |
| Create | `platform-web/src/main/kotlin/.../infra/NativeStartupCheck.kt` | Runtime self-check in native-image mode |
| Modify | `platform-web/src/main/kotlin/.../Main.kt` | Call NativeStartupCheck before app starts |
| Update | `docs/aot-native-image.md` | Document the drift test, generation script, and startup check |

---

### Task 1: Fix existing drift in reachability-metadata.json

**Why:** V13 migration is missing from the resources section. The metadata also lists stale entries (`jooq-settings.xml`, `V9__add_totp.sql` / `V11__add_message_votes.sql` which were renamed/removed). This must be fixed before the drift test is written (otherwise the test would immediately fail).

**Files:**
- Modify: `platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json`

- [ ] **Step 1: Audit the resources section against actual files**

Read the current `"resources"` array in `reachability-metadata.json` (lines 1287-1421). Cross-reference against:

**Actual migration files** (from `platform-persistence-jdbi/src/main/resources/db/migration/`):
- V1__initial_schema.sql
- V2__user_profile_enhancements.sql
- V3__sessions_table.sql
- V4__user_preferences.sql
- V5__performance_indexes.sql
- V6__admin_stats_indexes.sql
- V7__account_lockout.sql
- V8__query_path_indexes.sql
- V10__add_trgm_search_indexes.sql
- V11__add_totp.sql
- V12__add_polls.sql
- V13__utc_only_timestamptz.sql

Note: No V9 (was never created — gap in numbering). V9__add_totp.sql in metadata is wrong — the actual file is V11__add_totp.sql. V11__add_message_votes.sql in metadata is wrong — no such file exists.

**Resources that should be listed:**
- `application.yaml` (platform-core)
- `db/migration/migrations.index`
- `db/migration/V1__initial_schema.sql` through `V8__query_path_indexes.sql`
- `db/migration/V10__add_trgm_search_indexes.sql`
- `db/migration/V11__add_totp.sql`
- `db/migration/V12__add_polls.sql`
- `db/migration/V13__utc_only_timestamptz.sql`
- `logback.xml`
- `messages.properties`
- `messages_fr.properties`
- `themes.json`, `themes/default.json`, `themes/dark.json`
- All `META-INF/services/*` entries (keep existing)
- All module/bundle entries (keep existing)

**Resources to remove:**
- `jooq-settings.xml` (jOOQ was removed in PR #333)
- `db/migration/V9__add_totp.sql` (wrong filename — actual is V11)
- `db/migration/V11__add_message_votes.sql` (doesn't exist)

**Resources to add:**
- `db/migration/V13__utc_only_timestamptz.sql`
- `application.yaml` (if not already listed — check)
- `db/migration/migrations.index` (if not already listed — check)

- [ ] **Step 2: Apply the fixes**

Edit `reachability-metadata.json` to:
1. Remove `db/migration/V9__add_totp.sql`
2. Remove `db/migration/V11__add_message_votes.sql`
3. Remove `jooq-settings.xml`
4. Add `db/migration/V13__utc_only_timestamptz.sql`

The migration resource entries should be in the resources array as:
```json
    {
      "glob": "db/migration/V13__utc_only_timestamptz.sql"
    },
```

Remove the three stale entries. Keep everything else.

- [ ] **Step 3: Verify**

Run: `mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -q`

- [ ] **Step 4: Commit**

```
git add platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json
git commit -m "fix(native): remove stale entries and add V13 migration to reachability metadata"
```

---

### Task 2: Create NativeResourceDriftTest

**Why:** Catches future drift — any new migration, i18n bundle, or YAML profile that isn't in `reachability-metadata.json` will fail the build.

**Files:**
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/nativeimage/NativeResourceDriftTest.kt`

- [ ] **Step 1: Write the test**

Create `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/nativeimage/NativeResourceDriftTest.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.nativeimage

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NativeResourceDriftTest {

    private val metadataFile = File(
        "src/main/resources/META-INF/native-image/" +
            "io.github.rygel/outerstellar-platform-web/reachability-metadata.json"
    )

    private val mapper = ObjectMapper()

    private fun resourceGlobs(): Set<String> {
        assertTrue(metadataFile.isFile, "reachability-metadata.json must exist")
        val tree = mapper.readTree(metadataFile)
        val resources = tree["resources"]
        assertTrue(resources != null && resources.isArray, "resources array must exist")
        return resources
            .map { it["glob"]?.asText() ?: "" }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    @Test
    fun `all migration sql files are listed in reachability metadata`() {
        val migrationDir = File("../../platform-persistence-jdbi/src/main/resources/db/migration")
        assertTrue(migrationDir.isDirectory, "Migration directory must exist")

        val sqlFiles = migrationDir.listFiles()
            ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
            ?.map { "db/migration/${it.name}" }
            ?: emptyList()

        assertTrue(sqlFiles.isNotEmpty(), "There should be at least one migration")

        val globs = resourceGlobs()
        val missing = sqlFiles.filter { it !in globs }

        assertTrue(
            missing.isEmpty(),
            "These migration files are not in reachability-metadata.json resources:\n" +
                missing.joinToString("\n  ") +
                "\nRun: scripts/generate-reachability-resources.ps1 to regenerate",
        )
    }

    @Test
    fun `i18n bundles are listed in reachability metadata`() {
        val messagesDir = File("../../platform-core/src/main/resources")
        assertTrue(messagesDir.isDirectory, "platform-core resources must exist")

        val bundles = messagesDir.listFiles()
            ?.filter { it.name.matches(Regex("messages.*\\.properties")) }
            ?.map { it.name }
            ?: emptyList()

        assertTrue(bundles.isNotEmpty(), "There should be at least one messages bundle")

        val globs = resourceGlobs()
        val missing = bundles.filter { it !in globs }

        assertTrue(
            missing.isEmpty(),
            "These i18n bundles are not in reachability-metadata.json resources:\n" +
                missing.joinToString("\n  ") +
                "\nRun: scripts/generate-reachability-resources.ps1 to regenerate",
        )
    }

    @Test
    fun `logback xml is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue(
            "logback.xml" in globs,
            "logback.xml must be listed in reachability-metadata.json resources",
        )
    }

    @Test
    fun `migration manifest is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue(
            "db/migration/migrations.index" in globs,
            "db/migration/migrations.index must be listed in reachability-metadata.json resources",
        )
    }

    @Test
    fun `application yaml is listed in reachability metadata`() {
        val globs = resourceGlobs()
        assertTrue(
            "application.yaml" in globs,
            "application.yaml must be listed in reachability-metadata.json resources",
        )
    }

    @Test
    fun `no stale migration entries exist in reachability metadata`() {
        val migrationDir = File("../../platform-persistence-jdbi/src/main/resources/db/migration")
        val actualSqlFiles = migrationDir.listFiles()
            ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
            ?.map { "db/migration/${it.name}" }
            ?.toSet()
            ?: emptySet()

        val globs = resourceGlobs()
        val metadataMigrations = globs.filter { it.startsWith("db/migration/V") && it.endsWith(".sql") }

        val stale = metadataMigrations.filter { it !in actualSqlFiles }

        assertTrue(
            stale.isEmpty(),
            "These migration entries in reachability-metadata.json don't match actual files:\n" +
                stale.joinToString("\n  ") +
                "\nRemove them from reachability-metadata.json",
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it passes (after Task 1 fixes)**

Run: `mvn -pl platform-web test -Dtest=NativeResourceDriftTest -Dspotless.check.skip=true`

Expected: PASS (all 6 tests)

- [ ] **Step 3: Commit**

```
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/nativeimage/NativeResourceDriftTest.kt
git commit -m "test(native): add NativeResourceDriftTest for reachability metadata validation"
```

---

### Task 3: Create resource generation script

**Why:** Automates the boring part of maintaining `reachability-metadata.json` — the resource entries. Engineers run the script after adding migrations, i18n bundles, etc. Reflection entries stay manual.

**Files:**
- Create: `scripts/generate-reachability-resources.ps1`

- [ ] **Step 1: Write the script**

Create `scripts/generate-reachability-resources.ps1`:

```powershell
<#
.SYNOPSIS
Generates the "resources" array entries for reachability-metadata.json from actual classpath files.

.DESCRIPTION
Scans known resource locations and outputs JSON entries suitable for pasting into
reachability-metadata.json. Does NOT modify the file — it prints the entries to stdout.
Reflection entries are manual and must be maintained by hand.

.EXAMPLE
./scripts/generate-reachability-resources.ps1
#>

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")

$resourceDirs = @(
    @{ Prefix = "db/migration"; Path = "$RootDir/platform-persistence-jdbi/src/main/resources/db/migration"; Filter = "V*.sql" },
    @{ Prefix = ""; Path = "$RootDir/platform-core/src/main/resources"; Filter = "messages*.properties" },
    @{ Prefix = ""; Path = "$RootDir/platform-core/src/main/resources"; Filter = "logback.xml" },
    @{ Prefix = ""; Path = "$RootDir/platform-core/src/main/resources"; Filter = "application.yaml" },
    @{ Prefix = ""; Path = "$RootDir/platform-core/src/main/resources"; Filter = "application-*.yaml" },
    @{ Prefix = "themes"; Path = "$RootDir/platform-core/src/main/resources/themes"; Filter = "*.json" },
    @{ Prefix = ""; Path = "$RootDir/platform-core/src/main/resources"; Filter = "themes.json" }
)

$entries = [System.Collections.Generic.SortedSet[string]]::new()

foreach ($dir in $resourceDirs) {
    $dirPath = $dir.Path
    if (-not (Test-Path $dirPath)) { continue }

    $files = Get-ChildItem -LiteralPath $dirPath -Filter $dir.Filter -File
    foreach ($file in $files) {
        if ($dir.Prefix) {
            $glob = "$($dir.Prefix)/$($file.Name)"
        } else {
            $relative = $file.FullName.Substring($dirPath.Length + 1).Replace("\", "/")
            $glob = $relative
        }
        $entries.Add($glob) | Out-Null
    }
}

# Add migrations.index
$entries.Add("db/migration/migrations.index") | Out-Null

# Print as JSON array entries
Write-Output "// Auto-generated resource entries - paste into reachability-metadata.json resources array"
Write-Output "// Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Output ""
foreach ($entry in $entries) {
    Write-Output "    {`"glob`": `"$entry`"},"
}
```

- [ ] **Step 2: Run the script to verify it works**

Run: `pwsh scripts/generate-reachability-resources.ps1`

Expected: JSON entries printed to stdout for all migrations, i18n bundles, YAML files, themes, logback.

- [ ] **Step 3: Commit**

```
git add scripts/generate-reachability-resources.ps1
git commit -m "feat(native): add script to auto-generate reachability-metadata resource entries"
```

---

### Task 4: Create NativeStartupCheck runtime diagnostic

**Why:** Catches drift at runtime in native-image mode — if a resource is missing from the binary, the app fails fast with an actionable error instead of an obscure ClassNotFoundException later.

**Files:**
- Create: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/NativeStartupCheck.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/Main.kt`

- [ ] **Step 1: Write NativeStartupCheck**

Create `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/NativeStartupCheck.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.infra

import org.slf4j.LoggerFactory

object NativeStartupCheck {
    private val logger = LoggerFactory.getLogger(NativeStartupCheck::class.java)

    private val IS_NATIVE = System.getProperty("org.graalvm.nativeimage.imagekind") != null

    private data class CheckResult(val name: String, val passed: Boolean, val detail: String = "")

    fun run() {
        if (!IS_NATIVE) {
            logger.info("Native startup check skipped (not running in native-image mode)")
            return
        }

        logger.info("Running native-image startup diagnostics...")
        val results = mutableListOf<CheckResult>()

        results += checkResource("db/migration/migrations.index", "Migration manifest")
        results += checkResource("application.yaml", "Application config")
        results += checkResource("logback.xml", "Logging config")
        results += checkResource("messages.properties", "i18n English bundle")
        results += checkResource("messages_fr.properties", "i18n French bundle")

        results += checkClassLoadable(
            "gg.jte.generated.precompiled.outerstellar.JteHomeGenerated",
            "JTE template registry",
        )

        results += checkMigrationIndexNonEmpty()

        val failures = results.filter { !it.passed }
        if (failures.isEmpty()) {
            logger.info("Native startup check passed ({} checks)", results.size)
        } else {
            failures.forEach { logger.error("NATIVE CHECK FAILED: {} - {}", it.name, it.detail) }
            throw IllegalStateException(
                "Native startup check failed (${failures.size}/${results.size} checks). " +
                    "See errors above. This usually means reachability-metadata.json is missing entries. " +
                    "Run: scripts/generate-reachability-resources.ps1",
            )
        }
    }

    private fun checkResource(path: String, name: String): CheckResult {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
        val passed = stream != null
        stream?.close()
        return if (passed) {
            logger.debug("  [OK] {} - found {}", name, path)
            CheckResult(name, true)
        } else {
            CheckResult(name, false, "$path not found on classpath — add to reachability-metadata.json resources")
        }
    }

    private fun checkClassLoadable(className: String, name: String): CheckResult {
        return try {
            Class.forName(className)
            logger.debug("  [OK] {} - class loaded", name)
            CheckResult(name, true)
        } catch (e: ClassNotFoundException) {
            CheckResult(name, false, "$className not found — check JteClassRegistry and reflection metadata")
        }
    }

    private fun checkMigrationIndexNonEmpty(): CheckResult {
        val name = "Migration index content"
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("db/migration/migrations.index")
            ?: return CheckResult(name, false, "migrations.index not found on classpath")
        val content = stream.bufferedReader().readText().trim()
        stream.close()
        return if (content.isNotBlank()) {
            val count = content.lines().filter { it.isNotBlank() }.size
            logger.debug("  [OK] {} - {} migrations listed", name, count)
            CheckResult(name, true)
        } else {
            CheckResult(name, false, "migrations.index is empty — migrations will not run")
        }
    }
}
```

- [ ] **Step 2: Call NativeStartupCheck from Main.kt**

In `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/Main.kt`, add the import and call at the start of `main()`:

Add import:
```kotlin
import io.github.rygel.outerstellar.platform.infra.NativeStartupCheck
```

Add call at the beginning of the `main()` function body, before Koin starts:
```kotlin
NativeStartupCheck.run()
```

The placement should be the first line in `main()`, before any framework initialization. This ensures the check runs before Koin, Flyway, or Netty start.

- [ ] **Step 3: Compile**

Run: `mvn -pl platform-web compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -q`

- [ ] **Step 4: Verify existing tests still pass**

Run: `mvn -pl platform-web -am test -Dtest=HealthCheckIntegrationTest -Dspotless.check.skip=true`

Expected: PASS (the startup check should be a no-op in JVM mode since `IS_NATIVE` is false)

- [ ] **Step 5: Commit**

```
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/NativeStartupCheck.kt
git add platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/Main.kt
git commit -m "feat(native): add NativeStartupCheck runtime diagnostic for native-image mode"
```

---

### Task 5: Update docs and run full reactor verify

**Why:** Document the new tooling so contributors know how to use it.

**Files:**
- Modify: `docs/aot-native-image.md`

- [ ] **Step 1: Add documentation section**

In `docs/aot-native-image.md`, add a section after the existing "Troubleshooting" section (or at the end if no troubleshooting section exists) titled "Resource Drift Prevention":

```markdown
## Resource Drift Prevention

Native-image builds silently drop resources that aren't listed in `reachability-metadata.json`. The project has three safety nets to catch drift:

### Build-time: NativeResourceDriftTest

A JUnit test that validates every migration, i18n bundle, YAML profile, and logback config is listed in `reachability-metadata.json`. This test runs as part of the normal CI build.

If it fails, run the generation script to regenerate the resource entries:

```powershell
pwsh scripts/generate-reachability-resources.ps1
```

### Script: generate-reachability-resources.ps1

Generates the `"resources"` array entries from actual classpath files. Prints to stdout — copy/paste into `reachability-metadata.json`. Does NOT modify the file automatically (reflection entries require human judgment).

### Runtime: NativeStartupCheck

In native-image mode only, the app runs a startup diagnostic that checks:
- Migration manifest (`migrations.index`) is readable and non-empty
- `application.yaml`, `logback.xml`, i18n bundles are on classpath
- JTE template classes are loadable

If any check fails, the app exits immediately with an actionable error message.
```

- [ ] **Step 2: Run full reactor verify**

Run: `mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed`

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add docs/aot-native-image.md
git commit -m "docs: document native-image resource drift prevention tooling"
```

---

## Summary

| Task | What | Closes |
|------|------|--------|
| 1 | Fix existing drift in reachability-metadata.json | (prerequisite) |
| 2 | NativeResourceDriftTest — build-time validation | #327 |
| 3 | generate-reachability-resources.ps1 — auto-generation | #325 |
| 4 | NativeStartupCheck — runtime diagnostic | #328 |
| 5 | Docs + full verify | — |
