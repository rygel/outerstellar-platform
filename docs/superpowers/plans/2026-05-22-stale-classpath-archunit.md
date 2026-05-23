# Stale Classpath Detection + Architecture Enforcement Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent phantom test failures from stale Maven artifacts, and make ArchUnit architecture rules actually enforce across all library modules.

**Architecture:** (1) Wrapper scripts that always do a clean install before testing, plus an AGENTS.md warning. (2) Move ArchitectureTest from platform-core to platform-web where all library module classes are on the classpath, remove `allowEmptyShould(true)` from rules that can now actually be validated.

**Tech Stack:** Kotlin, Maven, ArchUnit, JUnit 5, PowerShell, Bash

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `test-all.ps1` | Windows wrapper: clean install + verify |
| Create | `test-all.sh` | Linux/Mac wrapper: clean install + verify |
| Delete | `platform-core/src/test/kotlin/.../arch/ArchitectureTest.kt` | Old location (classpath-limited) |
| Create | `platform-web/src/test/kotlin/.../arch/ArchitectureTest.kt` | New location (sees all library modules) |
| Modify | `AGENTS.md` | Document wrapper scripts and stale classpath risk |

---

### Task 1: Create test wrapper scripts

**Problem:** Running `mvn -pl platform-web test` without first `mvn clean install -DskipTests` on upstream modules causes phantom test failures from stale JARs in `~/.m2/repository/`. This is the single biggest time sink in the development workflow.

**Solution:** Wrapper scripts that always do a clean install before testing. Developers run one command instead of remembering two.

**Files:**
- Create: `test-all.ps1` (root directory)
- Create: `test-all.sh` (root directory)

- [ ] **Step 1: Create `test-all.ps1`**

Create `test-all.ps1` in the project root:

```powershell
#!/usr/bin/env pwsh
# test-all.ps1 — Safe test runner that prevents stale classpath failures.
#
# Usage:
#   ./test-all.ps1                  # Full reactor build (non-desktop modules)
#   ./test-all.ps1 -Module web     # Single module (still rebuilds all dependencies)
#   ./test-all.ps1 -SkipInstall    # Skip the install step (unsafe, for fast iteration only)
#
# This script ALWAYS runs `mvn clean install -DskipTests` before testing
# to prevent stale artifact failures from ~/.m2/repository.

param(
    [string]$Module = "",
    [switch]$SkipInstall = $false,
    [int]$Threads = 4
)

$ErrorActionPreference = "Stop"

$modules = "platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed"

if ($Module -ne "") {
    $modules = $Module
}

if (-not $SkipInstall) {
    Write-Host ">>> Step 1/2: Clean install (no tests) to refresh local artifacts..." -ForegroundColor Cyan
    mvn clean install -T"$Threads" -DskipTests -pl $modules
    if ($LASTEXITCODE -ne 0) {
        Write-Host ">>> Install failed. Aborting." -ForegroundColor Red
        exit $LASTEXITCODE
    }
} else {
    Write-Host ">>> WARNING: Skipping install step. Stale artifacts may cause failures!" -ForegroundColor Yellow
}

Write-Host ">>> Step 2/2: Running tests..." -ForegroundColor Cyan
mvn verify -T"$Threads" -pl $modules
exit $LASTEXITCODE
```

- [ ] **Step 2: Create `test-all.sh`**

Create `test-all.sh` in the project root:

```bash
#!/usr/bin/env bash
# test-all.sh — Safe test runner that prevents stale classpath failures.
#
# Usage:
#   ./test-all.sh                   # Full reactor build (non-desktop modules)
#   ./test-all.sh -m web            # Single module (still rebuilds all dependencies)
#   ./test-all.sh --skip-install    # Skip the install step (unsafe, for fast iteration only)
#
# This script ALWAYS runs `mvn clean install -DskipTests` before testing
# to prevent stale artifact failures from ~/.m2/repository.

set -euo pipefail

MODULES="platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed"
THREADS=4
SKIP_INSTALL=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -m|--module)
            MODULES="$2"
            shift 2
            ;;
        --skip-install)
            SKIP_INSTALL=true
            shift
            ;;
        -t|--threads)
            THREADS="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [ "$SKIP_INSTALL" = false ]; then
    echo ">>> Step 1/2: Clean install (no tests) to refresh local artifacts..."
    mvn clean install -T"$THREADS" -DskipTests -pl "$MODULES"
else
    echo ">>> WARNING: Skipping install step. Stale artifacts may cause failures!"
fi

echo ">>> Step 2/2: Running tests..."
mvn verify -T"$THREADS" -pl "$MODULES"
```

- [ ] **Step 3: Make shell script executable**

```bash
git update-index --chmod=+x test-all.sh
```

- [ ] **Step 4: Commit**

```bash
git add test-all.ps1 test-all.sh
git commit -m "chore: add test-all wrapper scripts for stale classpath prevention"
```

---

### Task 2: Move ArchitectureTest to platform-web

**Problem:** ArchitectureTest lives in platform-core which only sees core classes. All 9 ArchUnit rules targeting non-core packages use `allowEmptyShould(true)` and match 0 classes — they always pass regardless of violations.

**Solution:** platform-web depends on core, security, persistence-jdbi, and sync-client (4 out of 8 modules). Rules targeting those 4 modules can now be properly enforced without `allowEmptyShould(true)`. Rules targeting desktop/seed/javafx still need it.

**Files:**
- Delete: `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/arch/ArchitectureTest.kt`
- Create: `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/arch/ArchitectureTest.kt`
- Modify: `platform-web/pom.xml` (add ArchUnit test dependency if not present)

- [ ] **Step 1: Check if ArchUnit is already a test dependency in platform-web**

Read `platform-web/pom.xml` and search for `archunit`. If it's NOT there, add it. The platform-core pom.xml already has it, so check what version is used there first.

If ArchUnit needs to be added to platform-web, add this to `platform-web/pom.xml` in the `<dependencies>` section under test scope:

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit</artifactId>
    <version>${archunit.version}</version>
    <scope>test</scope>
</dependency>
```

Check if the version property `archunit.version` already exists in the root pom.xml `<properties>` section. If not, add it there.

- [ ] **Step 2: Create the new ArchitectureTest in platform-web**

Create `platform-web/src/test/kotlin/io/github/rygel/outerstellar/platform/arch/ArchitectureTest.kt`:

```kotlin
package io.github.rygel.outerstellar.platform.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition
import org.junit.jupiter.api.Test

class ArchitectureTest {

    // This test lives in platform-web, which depends on:
    //   platform-core, platform-security, platform-persistence-jdbi, platform-sync-client
    // All rules targeting those modules are PROPERLY ENFORCED (no allowEmptyShould).
    //
    // Rules targeting desktop, seed, or javafx still use allowEmptyShould(true)
    // because those modules are NOT on the classpath.

    private val allClasses =
        ClassFileImporter()
            .importPackages("io.github.rygel.outerstellar.platform")

    @Test
    fun `core should not depend on web or desktop`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..core..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `persistence implementation should not depend on web or desktop`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `security module should not depend on web or desktop`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..security..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `sync client should not depend on persistence, desktop, or web`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..sync..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..persistence.jdbi..", "..desktop..", "..web..")

        rule.check(allClasses)
    }

    @Test
    fun `services should not depend on web framework`() {
        val rule =
            noClasses()
                .that()
                .resideInAPackage("..service..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..web..", "..desktop..")

        rule.check(allClasses)
    }

    @Test
    fun `repository interfaces should reside in core`() {
        val rule =
            classes()
                .that()
                .areInterfaces()
                .and()
                .haveSimpleNameEndingWith("Repository")
                .should()
                .resideInAPackage("io.github.rygel.outerstellar.platform.persistence")

        rule.check(allClasses)
    }

    @Test
    fun `repository implementations should not reside in core`() {
        val rule =
            noClasses()
                .that()
                .areNotInterfaces()
                .and()
                .haveNameMatching(".*\\.Jdbi.+Repository")
                .should()
                .resideInAPackage("..core..")

        rule.check(allClasses)
    }

    @Test
    fun `desktop should not depend on web`() {
        val importedClasses =
            ClassFileImporter()
                .importPackages("io.github.rygel.outerstellar.platform")

        val rule =
            noClasses()
                .that()
                .resideInAPackage("..desktop..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..web..")
                .allowEmptyShould(true)

        rule.check(importedClasses)
    }

    @Test
    fun `no cycles in service layer`() {
        val importedClasses =
            ClassFileImporter()
                .importPackages(
                    "io.github.rygel.outerstellar.platform.service",
                    "io.github.rygel.outerstellar.platform.persistence",
                    "io.github.rygel.outerstellar.platform.model",
                )

        val rule =
            SlicesRuleDefinition.slices()
                .matching("io.github.rygel.outerstellar.platform.(*)..")
                .should()
                .beFreeOfCycles()

        rule.check(importedClasses)
    }
}
```

Key differences from the old version:
- `allClasses` is imported once and reused (not re-imported per test)
- Rules targeting `..core..`, `..persistence..`, `..security..`, `..service..`, `..sync..` do NOT have `allowEmptyShould(true)` — they will now FAIL if violated
- Rules targeting `..desktop..` still use `allowEmptyShould(true)` (desktop not on classpath)
- The `no cycles` rule does NOT use `allowEmptyShould(true)` — it can now detect real cycles

- [ ] **Step 3: Delete the old ArchitectureTest from platform-core**

Delete `platform-core/src/test/kotlin/io/github/rygel/outerstellar/platform/arch/ArchitectureTest.kt`

Also check if the `arch/` directory is now empty and remove it if so.

Also check if ArchUnit is still needed as a test dependency in `platform-core/pom.xml`. If no other test in platform-core uses ArchUnit, remove the dependency:

```xml
<!-- Remove from platform-core/pom.xml if no other test uses it -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit</artifactId>
    <version>${archunit.version}</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 4: Compile platform-core and platform-web**

```powershell
mvn -pl platform-core,platform-web compile "-Ddetekt.skip=true" "-Dspotbugs.skip=true" "-Dspotless.check.skip=true"
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Run the new ArchitectureTest**

```powershell
mvn -pl platform-web test -Dtest=ArchitectureTest -Dexec.skip=true
```

Expected: All 9 tests pass. If any fail, that means the rule detected a REAL architectural violation that was previously hidden — investigate and either fix the violation or adjust the rule.

- [ ] **Step 6: Run the full platform-web test suite**

```powershell
mvn -pl platform-web test -Dexec.skip=true
```

Expected: 593 tests, 0 failures

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(arch): move ArchitectureTest to platform-web for real enforcement

platform-web depends on core, security, persistence-jdbi, and sync-client,
so ArchUnit rules targeting those modules now match real classes instead of
zero. Removed allowEmptyShould(true) from 8 of 9 rules. The desktop rule
still needs it (desktop not on classpath)."
```

---

### Task 3: Update AGENTS.md

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: Add wrapper script documentation and stale classpath warning**

In the `## Build and run` section of `AGENTS.md`, after the existing `### Test execution` subsection, add a new subsection:

```markdown
### Stale classpath prevention

Running `mvn -pl platform-web test` without first rebuilding upstream modules
picks up stale JARs from `~/.m2/repository/`, causing phantom test failures
that don't exist in the source code. **Always use the wrapper scripts:**

```powershell
# Full reactor (recommended)
./test-all.ps1

# Single module (still rebuilds all dependencies first)
./test-all.ps1 -Module platform-web

# Linux/Mac
./test-all.sh
./test-all.sh -m platform-web
```

If you MUST run `mvn` directly, always precede it with:

```powershell
mvn clean install -DskipTests -T4 -pl platform-core,platform-security,platform-persistence-jdbi,platform-sync-client,platform-web,platform-seed
```
```

- [ ] **Step 2: Update ArchitectureTest reference**

In AGENTS.md, find any reference to ArchitectureTest being in platform-core and update it to note it's now in platform-web. Search for "ArchitectureTest" and update relevant sections.

- [ ] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs: add test wrapper scripts and stale classpath warning to AGENTS.md"
```

---

## Execution Order

Tasks 1 and 2 are independent and can run in parallel. Task 3 depends on both being done.

**Parallel execution:**
- Task 1 (wrapper scripts) — no code dependencies
- Task 2 (ArchitectureTest move) — no code dependencies
- Task 3 (AGENTS.md update) — after both complete

## Self-Review

1. **Spec coverage:** Both high-priority items covered. Stale classpath = wrapper scripts + docs. Architecture enforcement = move test to platform-web.
2. **Placeholder scan:** All steps contain exact code/commands. No TBDs.
3. **Type consistency:** ArchitectureTest imports and package names consistent throughout.
