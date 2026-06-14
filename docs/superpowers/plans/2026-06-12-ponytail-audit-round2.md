# Ponytail Audit Round 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove dead code, unused dependencies, stale config, and duplicate code identified by the second ponytail audit — reducing complexity without changing behavior.

**Architecture:** Pure deletion/simplification. No new features. Each task is independently committable.

**Tech Stack:** Kotlin, Maven, http4k, JDBI, Flyway

---

## Task 1: Delete stale .gitmodules and root junk files

**Files:**
- Delete: `.gitmodules`
- Delete: `into.md`
- Delete: `TODO.md`
- Modify: `.gitignore` (add `.env`)

- [ ] **Step 1: Delete stale files**

`.gitmodules` references `pr-security` submodule that no longer exists. `into.md` is an accidental commit. `TODO.md` should be GitHub issues.

```powershell
Remove-Item -LiteralPath .gitmodules, into.md, TODO.md
```

- [ ] **Step 2: Add .env to .gitignore**

Add `.env` line to `.gitignore` (it currently contains only `ERROR_CODE=1` but could contain secrets in the future).

- [ ] **Step 3: Commit**

```bash
git add .gitignore
git add .gitmodules into.md TODO.md
git commit -m "chore: delete stale .gitmodules, into.md, TODO.md; gitignore .env"
```

---

## Task 2: Remove unused http4k-client-apache from platform-sync-client

The sync client uses `java.net.http.HttpClient` directly (see `ConnectivityChecker.kt:4,28`). The http4k Apache client has zero imports in this module.

**Files:**
- Modify: `platform-sync-client/pom.xml`

- [ ] **Step 1: Read the pom.xml first, then remove the dependency**

Remove from `platform-sync-client/pom.xml`:
```xml
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-client-apache</artifactId>
        </dependency>
```

- [ ] **Step 2: Compile**

```powershell
mvn -pl platform-sync-client -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-sync-client/pom.xml
git commit -m "chore: remove unused http4k-client-apache from sync-client"
```

---

## Task 3: Remove unused http4k-api-openapi from platform-extension-api

The extension-api module uses `org.http4k.contract.*` imports (which come from `http4k-core`), not `org.http4k.contract.openapi.*` (which comes from `http4k-api-openapi`). Zero openapi imports in this module.

**Files:**
- Modify: `platform-extension-api/pom.xml`

- [ ] **Step 1: Read the pom.xml first, then remove the dependency**

Remove from `platform-extension-api/pom.xml`:
```xml
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-api-openapi</artifactId>
        </dependency>
```

- [ ] **Step 2: Compile**

```powershell
mvn -pl platform-extension-api -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-extension-api/pom.xml
git commit -m "chore: remove unused http4k-api-openapi from extension-api"
```

---

## Task 4: Remove dead BOM entries from parent pom.xml

Five entries in the parent BOM have zero consumers across the entire repo.

**Files:**
- Modify: `pom.xml` (root)

- [ ] **Step 1: Remove each dead entry**

Read `pom.xml` first, then remove these blocks:

1. `http4k-server-jetty` (line ~244) — zero imports, only Netty is used
2. `http4k-ops-resilience4j` (line ~408) — zero imports after previous cleanup
3. `resilience4j-retry` (line ~447-450) — zero imports, only circuitbreaker is used in platform-core
4. `jakarta.activation-api` (line ~482-485) — zero imports, transitive via angus-mail
5. The `http4k-connect.version` property (line ~90) — no artifact references it

- [ ] **Step 2: Compile**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "chore: remove dead BOM entries (jetty, resilience4j-retry, jakarta.activation)"
```

---

## Task 5: Delete deprecated DatabaseInfra.migrateExtension()

`migrateExtension()` is `@Deprecated`, has `@Suppress("UNUSED_PARAMETER")`, and has zero callers. It just delegates to `migrate()`.

**Files:**
- Modify: `platform-persistence-jdbi/src/main/kotlin/.../infra/DatabaseInfra.kt:75-85`

- [ ] **Step 1: Remove the deprecated function**

Delete lines 75-85 from `DatabaseInfra.kt`:
```kotlin
@Deprecated("Extensions now share the platform history table. Use migrate() with extensionLocation instead.")
@Suppress("UNUSED_PARAMETER")
fun migrateExtension(
    dataSource: DataSource,
    location: String,
    historyTable: String,
    migrationNames: List<String>? = null,
) {
    repairLegacyExtensionHistoryTable(dataSource)
    migrate(dataSource = dataSource, extensionLocation = location, extensionMigrationNames = migrationNames)
}
```

- [ ] **Step 2: Compile**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```

- [ ] **Step 3: Commit**

```bash
git add platform-persistence-jdbi/src/main/kotlin/io/github/rygel/outerstellar/platform/infra/DatabaseInfra.kt
git commit -m "chore: remove deprecated DatabaseInfra.migrateExtension()"
```

---

## Task 6: Remove SeoMetadata dead constructor parameters and hand-rolled escaping

`SeoMetadata` has constructor parameters (`ogType`, `twitterCard`, `locale`) that always use their defaults at every call site. The `escapeHtml` and `escapeJson` private methods are hand-rolled and the HTML-building logic should use `kotlinx.serialization` for JSON escaping.

Note: `SeoMetadata.forPage()` IS used (in `LayoutHead.kte:27`), so keep it. But the `robots` parameter should actually be passed through instead of suppressed.

**Files:**
- Modify: `platform-core/src/main/kotlin/.../service/SeoMetadata.kt`
- Read: `platform-web/src/main/jte/.../layouts/LayoutHead.kte` (call site, verify before changing)

- [ ] **Step 1: Read LayoutHead.kte to understand the call site**

Read `platform-web/src/main/jte/io/github/rygel/outerstellar/platform/web/layouts/LayoutHead.kte` to see how `SeoMetadata.forPage()` is called. The current call passes `robots` as the last parameter.

- [ ] **Step 2: Simplify SeoMetadata**

Replace the entire `SeoMetadata.kt` with:
```kotlin
package io.github.rygel.outerstellar.platform.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class SeoMetadata(
    private val title: String,
    private val description: String,
    private val canonicalUrl: String,
    private val ogImage: String = "",
    private val noIndex: Boolean = false,
) {
    companion object {
        fun forPage(
            title: String,
            description: String,
            canonicalUrl: String,
            ogImage: String = "",
            locale: String = "en",
            robots: String = "index, follow",
        ) = SeoMetadata(
            title = title,
            description = description,
            canonicalUrl = canonicalUrl,
            ogImage = ogImage,
            noIndex = robots.startsWith("noindex"),
        )
    }

    fun generateAllMetaTags(): String = buildString {
        if (title.isNotBlank()) {
            appendLine("<meta property=\"og:title\" content=\"${escapeHtml(title)}\">")
            appendLine("<meta name=\"twitter:title\" content=\"${escapeHtml(title)}\">")
        }
        if (description.isNotBlank()) {
            appendLine("<meta property=\"og:description\" content=\"${escapeHtml(description)}\">")
            appendLine("<meta name=\"twitter:description\" content=\"${escapeHtml(description)}\">")
        }
        if (canonicalUrl.isNotBlank()) {
            appendLine("<meta property=\"og:url\" content=\"${escapeHtml(canonicalUrl)}\">")
        }
        if (ogImage.isNotBlank()) {
            appendLine("<meta property=\"og:image\" content=\"${escapeHtml(ogImage)}\">")
            appendLine("<meta name=\"twitter:image\" content=\"${escapeHtml(ogImage)}\">")
        }
        appendLine("<meta property=\"og:type\" content=\"website\">")
        appendLine("<meta name=\"twitter:card\" content=\"summary\">")
        appendLine("<meta property=\"og:locale\" content=\"en\">")
        if (noIndex) {
            appendLine("<meta name=\"robots\" content=\"noindex, nofollow\">")
        }
        if (canonicalUrl.isNotBlank() && title.isNotBlank()) {
            appendLine("<script type=\"application/ld+json\">")
            appendLine("{")
            appendLine("  \"@context\": \"https://schema.org\",")
            appendLine("  \"@type\": \"WebSite\",")
            appendLine("  \"name\": ${Json.encodeToString(JsonPrimitive(title))},")
            appendLine("  \"url\": ${Json.encodeToString(JsonPrimitive(canonicalUrl))}")
            if (description.isNotBlank()) {
                appendLine("  ,\"description\": ${Json.encodeToString(JsonPrimitive(description))}")
            }
            appendLine("}")
            appendLine("</script>")
        }
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;").replace("'", "&#39;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}
```

Key changes:
- Removed `ogType`, `twitterCard`, `locale` constructor params (always defaulted)
- Added `noIndex: Boolean` instead (derived from `robots` in `forPage`)
- `forPage` now actually uses the `robots` parameter (no more `@Suppress`)
- Replaced hand-rolled `escapeJson` with `Json.encodeToString(JsonPrimitive(value))` (already on classpath via kotlinx-serialization)
- Fixed incomplete `escapeHtml` (added missing `'` → `&#39;`)

- [ ] **Step 3: Compile**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
```

- [ ] **Step 4: Commit**

```bash
git add platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/service/SeoMetadata.kt
git commit -m "refactor: simplify SeoMetadata, replace hand-rolled escaping with kotlinx.serialization"
```

---

## Task 7: Full build verification

- [ ] **Step 1: Full reactor build (non-desktop)**

```powershell
mvn clean verify -T4 -pl !platform-desktop,!platform-desktop-javafx
```

Expected: BUILD SUCCESS. All 716+ tests pass, all quality checks pass.

---

## Deferred items (not in this plan)

These findings are valid but deferred for a future PR to keep scope manageable:

- **Replace Filters.kt hand-rolled `escapeHtml`** with the shared one from SeoMetadata (requires moving to a shared utility)
- **Extract shared sync-state UPSERT helper** from JdbiContactRepository + JdbiMessageRepository
- **Extract parameterized `loadCollectionForContacts`** from JdbiContactRepository triple
- **Collapse `SegmentEventSender` fun interface** into a function type
- **Split `SettingsTabContent`** 62-field god object into per-tab ViewModels
- **Extract `registerRouteGroup()` helper** from UiRouteRegistrar boilerplate
- **Replace `MessageListComponent` vararg args** with data class parameter
- **SeoMetadata HTML generation** should use JTE templates instead of string concatenation
- **Duplicate CSP policy** — consolidate AppConfig and Filters copies
- **Push notification stubs** — wired into DI and tested, can't delete without changing CoreComponents
- **starter-extension-app, benchmarks/** — orphaned but not harmful, separate decision
- **.env tracked in git** — needs careful review before gitignoring (may break CI)
