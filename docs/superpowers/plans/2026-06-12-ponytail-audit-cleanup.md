# Ponytail Audit Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove dead code, unused dependencies, and unnecessary abstractions identified by the ponytail audit — reducing complexity without changing behavior.

**Architecture:** Pure deletion/simplification. No new features. Each task is independent and can be committed separately. Tasks are ordered by risk: lowest-risk deletions first, then dependency removals, then code-level simplifications.

**Tech Stack:** Kotlin, Maven, http4k, JDBI

---

## Task 1: Delete root junk files and update .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Delete root DLL files**

```powershell
Remove-Item -LiteralPath awt.dll, java.dll, javaaccessbridge.dll, jawt.dll, jvm.dll, management_ext.dll
```

- [ ] **Step 2: Delete JVM crash logs and CI log**

```powershell
Remove-Item -LiteralPath hs_err_pid100372.log, hs_err_pid60400.log, hs_err_pid78856.log, hs_err_pid93452.log, .codex-ci-web-unit.log
```

- [ ] **Step 3: Add missing gitignore patterns**

The `.gitignore` already has `*.dll` and `*.log`. Add `hs_err_pid*.log` is already covered by `*.log`. No changes needed — the gitignore already covers these patterns. The files exist because they were created after the gitignore entry was added and git is already not tracking them.

- [ ] **Step 4: Verify files are gone**

```powershell
Get-ChildItem -Path . -Filter *.dll -File
Get-ChildItem -Path . -Filter hs_err_pid*.log -File
```

Expected: no output for either command.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: delete root junk files (DLLs, crash logs, CI log)"
```

---

## Task 2: Delete empty platform-persistence-jooq directory

**Files:**
- Delete: `platform-persistence-jooq/` (entire directory)

- [ ] **Step 1: Verify directory has no source files**

```powershell
Get-ChildItem -Path .\platform-persistence-jooq -Recurse -Include *.kt,*.java | Select-Object FullName
```

Expected: empty output.

- [ ] **Step 2: Delete the directory**

```powershell
Remove-Item -Recurse -Force platform-persistence-jooq
```

- [ ] **Step 3: Verify it's gone**

```powershell
Test-Path platform-persistence-jooq
```

Expected: `False`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete empty platform-persistence-jooq module"
```

---

## Task 3: Delete unused PlatformTheme interface and DaisyUITheme class

`PlatformTheme` is an interface with 1 implementation (`DaisyUITheme`). Neither is consumed as a type anywhere — no parameter, field, or collection holds `PlatformTheme`. The test for DaisyUITheme tests methods that all return empty defaults from the interface. The entire abstraction is dead.

**Files:**
- Delete: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/theme/PlatformTheme.kt`
- Delete: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/theme/DaisyUITheme.kt`
- Delete: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/theme/DaisyUIThemeTest.kt`
- Delete: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/theme/` (directory if empty after above)

- [ ] **Step 1: Grep for any consumers that reference PlatformTheme or DaisyUITheme**

```powershell
rg "PlatformTheme|DaisyUITheme" --include "*.kt" --include "*.kte"
```

Expected: only the 3 files listed above (plus the grep itself). No imports in other files.

- [ ] **Step 2: Delete the 3 files and directory**

```powershell
Remove-Item -Recurse -Force platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/theme
Remove-Item platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/web/theme/DaisyUIThemeTest.kt
```

- [ ] **Step 3: Compile platform-web**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete unused PlatformTheme interface and DaisyUITheme"
```

---

## Task 4: Delete unused WebComponent interface

`WebComponent<T>` is an interface with 1 implementation (`MessageListComponent`). No code holds a `WebComponent<*>` reference or uses it polymorphically. Remove the interface and make `MessageListComponent` a standalone class.

**Files:**
- Delete: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebComponent.kt`
- Modify: `platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/MessageListComponent.kt:37`

- [ ] **Step 1: Delete the interface file**

```powershell
Remove-Item platform-web/src/main/kotlin/io/github/rygel/outerstellar/platform/web/WebComponent.kt
```

- [ ] **Step 2: Remove the interface supertype from MessageListComponent**

In `MessageListComponent.kt` line 37, change:
```kotlin
class MessageListComponent(private val messageService: MessageService) : WebComponent<MessageListViewModel> {
```
to:
```kotlin
class MessageListComponent(private val messageService: MessageService) {
```

Also remove the `override` keyword from the `build` method on line 39:
```kotlin
    fun build(ctx: RequestContext, shellRenderer: ShellRenderer, vararg args: Any?): MessageListViewModel {
```

- [ ] **Step 3: Compile platform-web**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete unused WebComponent interface"
```

---

## Task 5: Delete unused SwingAppConfig typealias

`SwingAppConfig` is a `typealias SwingAppConfig = DesktopAppConfig` that is never referenced anywhere outside its own file.

**Files:**
- Delete: `platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingAppConfig.kt`

- [ ] **Step 1: Verify no references**

```powershell
rg "SwingAppConfig" --include "*.kt"
```

Expected: only the file itself.

- [ ] **Step 2: Delete the file**

```powershell
Remove-Item platform-desktop/src/main/kotlin/io/github/rygel/outerstellar/platform/swing/SwingAppConfig.kt
```

- [ ] **Step 3: Compile platform-desktop**

```powershell
mvn -pl platform-desktop -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "chore: delete unused SwingAppConfig typealias"
```

---

## Task 6: Inline LockoutRepository into UserRepository

`LockoutRepository` is a sub-interface of `UserRepository` with 3 methods. Nothing uses `LockoutRepository` as a standalone type — all 3 methods are called through `UserRepository`. Inline the methods directly into `UserRepository` and delete the sub-interface.

**Files:**
- Delete: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/LockoutRepository.kt`
- Modify: `platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/UserRepository.kt`

- [ ] **Step 1: Inline LockoutRepository methods into UserRepository**

In `UserRepository.kt`, change line 8:
```kotlin
interface UserRepository : LockoutRepository {
```
to:
```kotlin
interface UserRepository {
    fun incrementFailedLoginAttempts(userId: UUID): Int

    fun resetFailedLoginAttempts(userId: UUID)

    fun updateLockedUntil(userId: UUID, lockedUntil: Instant?)
```

Add the `Instant` import at the top (it's not currently imported since it came from LockoutRepository):
```kotlin
import java.time.Instant
```

- [ ] **Step 2: Delete LockoutRepository.kt**

```powershell
Remove-Item platform-core/src/main/kotlin/io/github/rygel/outerstellar/platform/persistence/LockoutRepository.kt
```

- [ ] **Step 3: Compile full reactor**

```powershell
mvn -pl platform-web -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS (platform-core change propagates to all consumers)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: inline LockoutRepository methods into UserRepository"
```

---

## Task 7: Remove dead dependencies from platform-sync-client

`resilience4j-circuitbreaker`, `resilience4j-retry`, and `http4k-ops-resilience4j` are declared in `platform-sync-client/pom.xml` but have zero imports in that module. They belong only in `platform-core` (which already has the one it needs).

**Files:**
- Modify: `platform-sync-client/pom.xml`

- [ ] **Step 1: Remove the 3 dead dependencies from sync-client pom.xml**

Remove these blocks from `platform-sync-client/pom.xml`:

```xml
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-ops-resilience4j</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-circuitbreaker</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-retry</artifactId>
        </dependency>
```

- [ ] **Step 2: Compile to verify nothing breaks**

```powershell
mvn -pl platform-sync-client -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-sync-client/pom.xml
git commit -m "chore: remove unused resilience4j deps from sync-client"
```

---

## Task 8: Remove dead dependencies from platform-security

`http4k-security-core` and `http4k-api-openapi` are declared in `platform-security/pom.xml` but have zero imports in that module. They are only used in `platform-web`.

**Files:**
- Modify: `platform-security/pom.xml`

- [ ] **Step 1: Remove the 2 dead dependencies from security pom.xml**

Remove these blocks from `platform-security/pom.xml`:

```xml
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-security-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-api-openapi</artifactId>
        </dependency>
```

- [ ] **Step 2: Compile to verify nothing breaks**

```powershell
mvn -pl platform-security -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add platform-security/pom.xml
git commit -m "chore: remove unused http4k deps from platform-security"
```

---

## Task 9: Remove redundant dependencies from platform-web and platform-desktop

`http4k-client-apache` in `platform-web` is redundant — it's already a transitive dep via `platform-sync-client` which platform-web depends on. `snakeyaml-engine` in `platform-desktop` and `platform-desktop-javafx` is redundant — it's already a transitive dep via `platform-core`.

**Files:**
- Modify: `platform-web/pom.xml`
- Modify: `platform-desktop/pom.xml`
- Modify: `platform-desktop-javafx/pom.xml`

- [ ] **Step 1: Remove http4k-client-apache from platform-web pom.xml**

Remove from `platform-web/pom.xml`:
```xml
        <dependency>
            <groupId>org.http4k</groupId>
            <artifactId>http4k-client-apache</artifactId>
        </dependency>
```

- [ ] **Step 2: Remove snakeyaml-engine from platform-desktop pom.xml**

Remove from `platform-desktop/pom.xml`:
```xml
        <dependency>
            <groupId>org.snakeyaml</groupId>
            <artifactId>snakeyaml-engine</artifactId>
        </dependency>
```

- [ ] **Step 3: Remove snakeyaml-engine from platform-desktop-javafx pom.xml**

Remove from `platform-desktop-javafx/pom.xml`:
```xml
        <dependency>
            <groupId>org.snakeyaml</groupId>
            <artifactId>snakeyaml-engine</artifactId>
        </dependency>
```

- [ ] **Step 4: Compile affected modules**

```powershell
mvn -pl platform-web,platform-desktop,platform-desktop-javafx -am compile -Ddetekt.skip=true -Dspotbugs.skip=true -Dspotless.check.skip=true
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add platform-web/pom.xml platform-desktop/pom.xml platform-desktop-javafx/pom.xml
git commit -m "chore: remove redundant transitive deps from web and desktop modules"
```

---

## Task 10: Run full build verification

After all tasks are complete, run the full reactor build to verify nothing is broken.

- [ ] **Step 1: Full reactor build (non-desktop)**

```powershell
mvn clean verify -T4 -pl !platform-desktop,!platform-desktop-javafx
```

Expected: BUILD SUCCESS. All tests pass, all quality checks pass.

- [ ] **Step 2: Final commit (if any spotless formatting was applied)**

```bash
git add -A
git diff --cached --stat
```

If there are staged changes, commit them:
```bash
git commit -m "style: spotless formatting after audit cleanup"
```

---

## Deferred items (not in this plan)

These audit findings are valid but deferred for a future PR to keep scope manageable:

- **Collapse PasswordEncoder interface** into BCryptPasswordEncoder (same file, low risk but security-adjacent)
- **Collapse ConnectivityChecker / SessionLifecycle interfaces** in sync-client
- **Extract SyncViewModel bgWork helper** (~80 lines saved, behavioral change)
- **Consolidate hand-rolled escapeHtml/escapeJson** into shared utility
- **Extract JdbiContactRepository generic load helper** and shared sync-state UPSERT
- **Remove SeoMetadata dead parameters** (ogType, twitterCard, locale always default)
- **Replace ErrorPageFactory numeric constants** with Status objects
- **Delete benchmarks/ directory** (contains only baseline.json)
- **Evaluate platform-desktop-javafx** (57 files, scaffolded, excluded from CI)
