# Auto-Generate Reachability Metadata Resources

**Issue:** #325
**Date:** 2026-05-25
**Status:** Approved

## Problem

`reachability-metadata.json` has 57 resource entries maintained by hand. A generation script exists (`scripts/generate-reachability-resources.ps1`) but only covers a subset of resource types and prints to stdout for manual copy-paste. Contributors forget to run it, and the drift test catches the mismatch after the fact.

## Decision

Make `NativeResourceDriftTest` the source of truth for resource entries. When it detects drift, it rewrites the `resources` section of `reachability-metadata.json` automatically and fails with a commit prompt.

Reflection entries (168) remain hand-maintained — they only change with dependency upgrades and are stable.

## Design

### Resource scanner

Add a `scanExpectedResources()` method to `NativeResourceDriftTest` that discovers all resource entries from the source tree:

| Resource type | Source path | Pattern |
|---|---|---|
| Flyway migrations | `platform-persistence-jdbi/src/main/resources/db/migration/` | `db/migration/V*.sql` |
| Migration index | same | `db/migration/migrations.index` |
| Migration directories | same | `db/migration/` |
| i18n bundles | `platform-core/src/main/resources/` | `messages*.properties` |
| Config files | `platform-web/src/main/resources/` | `application*.yaml` |
| Logback config | `platform-web/src/main/resources/` | `logback.xml` |
| Static CSS | `platform-web/src/main/resources/static/` | `static/site.css` |
| Static JS | `platform-web/src/main/resources/static/` | `static/platform.js` |
| Vendor assets | `platform-web/src/main/resources/static/vendor/remixicon/` | `static/vendor/remixicon/remixicon.*` (eot, svg, ttf, woff, woff2, css) |
| META-INF/services | all modules `src/main/resources/META-INF/services/` | `META-INF/services/*` |
| Themes | `platform-core/src/main/resources/` | `themes.json`, `themes/*.json` |

The scanner produces a sorted list of `{ "glob": "..." }` entries.

### Auto-fix behavior

The existing test methods are refactored into a single `resources match classpath` test:

1. Scan expected resources from classpath
2. Read current `resources` section from `reachability-metadata.json`
3. Compare sorted lists
4. **If they match:** test passes
5. **If they differ:** rewrite the `resources` section (preserving `reflection` section), then fail with: `"Resource entries regenerated — review diff and commit"`

### Files changed

| File | Change |
|---|---|
| `NativeResourceDriftTest.kt` | Replace individual test methods with unified scanner + auto-fix |
| `reachability-metadata.json` | Will be auto-updated by the test when drift is detected |
| `scripts/generate-reachability-resources.ps1` | **Deleted** — absorbed by the test |
| `docs/aot-native-image.md` | Update workflow documentation |

### What stays the same

- Reflection entries (168) — hand-maintained, stable
- `NativeStartupCheck` — runtime validation unchanged
- Native image CI pipeline — unchanged

## Acceptance criteria

- [x] Metadata workflow is scripted and reproducible (the test IS the workflow)
- [x] Contributors have a documented way to refresh metadata (run the test, commit the diff)
- [x] Drift is detectable (test fails on drift)
- [x] Server native build is less dependent on manual edits (resources auto-generated)
