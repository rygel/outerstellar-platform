# Reachability Metadata Resource Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `NativeResourceDriftTest` auto-fix the `resources` section of `reachability-metadata.json` when drift is detected, eliminating the manual generation script.

**Architecture:** Replace 6 individual drift test methods with a single unified test that scans project resources from the source tree, combines them with a constant list of dependency resources, and compares against the current metadata. On drift, the test rewrites the resources section in-place and fails with a commit prompt. The reflection section (168 entries) is untouched. The manual generation script is deleted.

**Tech Stack:** Kotlin, JUnit 5, Jackson (ObjectMapper), GraalVM reachability metadata JSON

**Design spec:** `docs/superpowers/specs/2026-05-25-reachability-metadata-resources-design.md`

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `platform-web/src/test/kotlin/.../nativeimage/NativeResourceDriftTest.kt` | **Rewrite** | Scanner + auto-fix test |
| `platform-web/src/main/resources/META-INF/.../reachability-metadata.json` | **Auto-updated** | Metadata file (resources section rewritten by test) |
| `scripts/generate-reachability-resources.ps1` | **Delete** | Superseded by the test |
| `docs/aot-native-image.md` | **Modify** | Update workflow documentation |

### Resource entry split

The `resources` section contains two categories of entries:

**Project resources** (scanned from source tree — 33 entries):
- Flyway migrations (12 SQL files + directory + index)
- i18n bundles (`messages*.properties`)
- Config files (`application.yaml`, `application-*.yaml`)
- Static web assets (CSS, JS, fonts, HTML under `static/`)
- Logback config (`logback.xml`)

**Dependency resources** (constant in test class — 20 entries):
- 16 glob-only entries from third-party JARs (META-INF/services, logback version props, flyway internals, postgres driver config, prometheus, http4k mime types)
- 2 module+glob entries (java.logging)
- 1 module+glob entry (jdk.jfr)
- 1 bundle entry (sun.util.logging.resources.logging)

**Stale entries removed** (4 entries currently in metadata that should not be):
- `logback-test.xml` — test-scoped, not on production classpath
- `themes.json` — only exists in platform-desktop, not a platform-web dependency
- `themes/default.json` — does not exist anywhere on filesystem
- `themes/dark.json` — does not exist anywhere on filesystem

---

### Task 1: Rewrite NativeResourceDriftTest

**Files:**
- Rewrite: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/nativeimage/NativeResourceDriftTest.kt`

- [ ] **Step 1: Write the new test**

Replace the entire file with the following code. This replaces 6 individual test methods with a single unified test that scans project resources, compares against metadata, and auto-fixes on drift.

```kotlin
package io.github.rygel.outerstellar.platform.nativeimage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import java.io.File
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class NativeResourceDriftTest {

    private val metadataFile = File(
        "src/main/resources/META-INF/native-image/" +
            "io.github.rygel/outerstellar-platform-web/reachability-metadata.json"
    )

    private val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    private val dependencyGlobs = listOf(
        "META-INF/org/http4k/core/mime.types",
        "META-INF/services/ch.qos.logback.classic.spi.Configurator",
        "META-INF/services/io.opentelemetry.context.ContextStorageProvider",
        "META-INF/services/java.net.spi.InetAddressResolverProvider",
        "META-INF/services/java.net.spi.URLStreamHandlerProvider",
        "META-INF/services/java.nio.channels.spi.SelectorProvider",
        "META-INF/services/java.sql.Driver",
        "META-INF/services/java.time.zone.ZoneRulesProvider",
        "META-INF/services/javax.xml.parsers.SAXParserFactory",
        "META-INF/services/org.flywaydb.core.extensibility.Plugin",
        "META-INF/services/org.slf4j.spi.SLF4JServiceProvider",
        "ch/qos/logback/classic/logback-classic-version.properties",
        "ch/qos/logback/core/logback-core-version.properties",
        "org/flywaydb/core/internal/version.txt",
        "org/postgresql/driverconfig.properties",
        "prometheus.properties",
    )

    private fun scanProjectGlobs(): Set<String> {
        val globs = sortedSetOf<String>()
        scanMigrations(globs)
        scanI18nBundles(globs)
        scanConfigFiles(globs)
        scanStaticAssets(globs)
        scanLogback(globs)
        return globs
    }

    private fun scanMigrations(globs: MutableSet<String>) {
        globs.add("db/migration")
        globs.add("db/migration/migrations.index")
        val dir = File("../platform-persistence-jdbi/src/main/resources/db/migration")
        assertTrue(dir.isDirectory, "Migration directory must exist")
        dir.listFiles()
            ?.filter { it.name.endsWith(".sql") && it.name.startsWith("V") }
            ?.forEach { globs.add("db/migration/${it.name}") }
    }

    private fun scanI18nBundles(globs: MutableSet<String>) {
        val dir = File("../platform-core/src/main/resources")
        assertTrue(dir.isDirectory, "platform-core resources must exist")
        dir.listFiles()
            ?.filter { it.name.matches(Regex("messages.*\\.properties")) }
            ?.forEach { globs.add(it.name) }
    }

    private fun scanConfigFiles(globs: MutableSet<String>) {
        globs.add("application.yaml")
        globs.add("application-*.yaml")
    }

    private fun scanStaticAssets(globs: MutableSet<String>) {
        val staticDir = File("src/main/resources/static")
        if (!staticDir.isDirectory) return
        scanDirectory(staticDir, "static", globs)
    }

    private fun scanDirectory(dir: File, prefix: String, globs: MutableSet<String>) {
        dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            val path = "$prefix/${file.name}"
            if (file.isDirectory) {
                scanDirectory(file, path, globs)
            } else {
                globs.add(path)
            }
        }
    }

    private fun scanLogback(globs: MutableSet<String>) {
        if (File("../platform-core/src/main/resources/logback.xml").isFile) {
            globs.add("logback.xml")
        }
    }

    private fun buildExpectedResources(): ArrayNode {
        val resources = mapper.createArrayNode()

        for (glob in scanProjectGlobs()) {
            resources.add(mapper.createObjectNode().put("glob", glob))
        }

        for (glob in dependencyGlobs.sorted()) {
            resources.add(mapper.createObjectNode().put("glob", glob))
        }

        resources.add(
            mapper.createObjectNode()
                .put("module", "java.logging")
                .put("glob", "sun/util/logging/resources/logging_en.properties")
        )
        resources.add(
            mapper.createObjectNode()
                .put("module", "java.logging")
                .put("glob", "sun/util/logging/resources/logging_en_GB.properties")
        )
        resources.add(
            mapper.createObjectNode()
                .put("module", "jdk.jfr")
                .put("glob", "jdk/jfr/internal/types/metadata.bin")
        )
        resources.add(
            mapper.createObjectNode()
                .put("bundle", "sun.util.logging.resources.logging")
        )

        return resources
    }

    @Test
    fun `resources section matches classpath`() {
        assertTrue(metadataFile.isFile, "reachability-metadata.json must exist")

        val expected = buildExpectedResources()
        val tree = mapper.readTree(metadataFile)
        val current = tree["resources"]
        assertTrue(current != null && current.isArray, "resources array must exist")

        val expectedSet = expected.map { it.toString() }.toSet()
        val currentSet = current.map { it.toString() }.toSet()

        if (expectedSet != currentSet) {
            val fixed =
                mapper.readTree(metadataFile) as com.fasterxml.jackson.databind.ObjectNode
            fixed.set<ArrayNode>("resources", expected)
            mapper.writeValue(metadataFile, fixed)
            assertTrue(
                false,
                "Resource entries regenerated — review diff and commit"
            )
        }
    }
}
```

Key design decisions in this code:

1. **`dependencyGlobs`** — 16 glob-only entries from third-party JARs. These are stable and only change with dependency upgrades. Maintained as a constant list.

2. **`scanProjectGlobs()`** — Scans the source tree across 3 modules: `platform-persistence-jdbi` (migrations), `platform-core` (i18n, logback), and `platform-web` (static assets). Config files use wildcard patterns rather than scanning individual files.

3. **`buildExpectedResources()`** — Constructs the complete expected resources array in a fixed order: project globs (sorted) → dependency globs (sorted) → module+glob entries → bundle entries. This order is deterministic.

4. **Comparison via `toString()`** — Each `JsonNode` entry is serialized to its compact string representation for set comparison. This handles different entry shapes (glob-only, module+glob, bundle) uniformly.

5. **Auto-fix via `mapper.writeValue()`** — On drift, rewrites the entire JSON file. The reflection section is preserved (read and written back unchanged) but may be reformatted by Jackson's pretty printer. This is a one-time cosmetic change — subsequent runs produce no diff.

- [ ] **Step 2: Compile the test**

Run:
```powershell
mvn -pl platform-web compile test-compile -am "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true" "-Dexec.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit the test rewrite**

```bash
git add platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/nativeimage/NativeResourceDriftTest.kt
git commit -m "refactor: unify NativeResourceDriftTest with auto-fix

Replace 6 individual drift test methods with a single unified test
that scans project resources from source tree and auto-fixes the
resources section of reachability-metadata.json when drift is detected."
```

---

### Task 2: Run the drift test to auto-fix metadata

**Files:**
- Auto-updated: `platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json`

- [ ] **Step 1: Run the drift test (first pass — should auto-fix)**

Run:
```powershell
mvn -pl platform-web test -Dtest=NativeResourceDriftTest -am "-Dexec.skip=true"
```

Expected: Test FAILS with message "Resource entries regenerated — review diff and commit". The metadata file is updated in-place:
- Stale entries removed: `themes.json`, `themes/default.json`, `themes/dark.json`, `logback-test.xml`
- Resource ordering normalized (alphabetical within each group)
- Reflection section preserved but may be reformatted by Jackson's pretty printer

- [ ] **Step 2: Review the auto-fixed metadata**

Run:
```powershell
git diff platform-web/src/main/resources/META-INF/native-image/
```

Verify:
1. **No entries added or removed from the reflection section** — only cosmetic formatting changes are acceptable
2. **Resource entries reordered** — project globs first (sorted alphabetically), then dependency globs (sorted), then special entries
3. **Stale entries gone** — `themes.json`, `themes/default.json`, `themes/dark.json`, `logback-test.xml` no longer present
4. **All expected entries present** — 33 project + 16 dependency globs + 4 special entries = 53 total

If Jackson reformatted the reflection section, verify the entry count is unchanged (168 reflection entries).

- [ ] **Step 3: Run the test again (second pass — should pass)**

Run:
```powershell
mvn -pl platform-web test -Dtest=NativeResourceDriftTest -am "-Dexec.skip=true"
```

Expected: Test PASSES (resources now match expected). This confirms the auto-fix produced valid output.

- [ ] **Step 4: Commit the auto-fixed metadata**

```bash
git add platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json
git commit -m "chore: auto-fix reachability metadata resource entries

Removed stale entries (themes/default.json, themes/dark.json,
logback-test.xml, themes.json). Resource ordering normalized."
```

---

### Task 3: Delete generation script and update docs

**Files:**
- Delete: `scripts/generate-reachability-resources.ps1`
- Modify: `docs/aot-native-image.md` (Drift Prevention section, lines 401-418; When to Update section, lines 412-419)

- [ ] **Step 1: Delete the generation script**

```bash
git rm scripts/generate-reachability-resources.ps1
```

- [ ] **Step 2: Update the Drift Prevention section in docs/aot-native-image.md**

Replace lines 403-418 (from "The metadata must stay" through "- Adding new i18n resource bundles") with:

```markdown
The metadata must stay in sync with actual classpath files. Two mechanisms prevent drift:

1. **Auto-fixing drift test** — `NativeResourceDriftTest` scans project resources from the source tree and compares them against the `resources` section of `reachability-metadata.json`. When drift is detected (missing or stale entries), the test **rewrites the resources section automatically** and fails with a commit prompt. Run the test again to confirm the fix.

2. **Runtime self-check** — `NativeStartupCheck` runs on native-image startup and verifies critical resources are accessible. It fails fast with actionable error messages if anything is missing.

The test covers: Flyway migrations, i18n bundles, application config YAML, static web assets, and logback config. Dependency resources (from third-party JARs) are maintained as a constant in the test class. Reflection entries (168) are hand-maintained and stable.

### When to Update

Update the metadata when:

- Adding new Flyway migrations (the drift test auto-fixes on next run)
- Adding new dependencies that use reflection (run the tracing agent to detect them)
- Changing logging configuration (Logback classes may differ)
- Adding new i18n resource bundles (the drift test auto-fixes on next run)
- Adding new static web assets (the drift test auto-fixes on next run)
```

- [ ] **Step 3: Commit**

```bash
git add scripts/generate-reachability-resources.ps1 docs/aot-native-image.md
git commit -m "docs: update drift prevention docs, delete generation script

The NativeResourceDriftTest now auto-fixes the resources section.
The manual generation script is no longer needed."
```

---

### Task 4: Full reactor verify

- [ ] **Step 1: Run full reactor verify (non-desktop modules)**

Run:
```powershell
mvn clean verify -T4 -pl platform-core,platform-security,platform-test-infrastructure,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```

Expected: BUILD SUCCESS. All tests pass including `NativeResourceDriftTest`.

- [ ] **Step 2: Verify git status is clean**

Run:
```powershell
git status
```

Expected: working tree clean (all changes committed in previous tasks)
