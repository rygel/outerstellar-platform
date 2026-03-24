# AGENTS Guide

This file defines repository-specific guardrails for coding agents and contributors.

## Scope

- Applies to the entire repository.
- If a subdirectory later adds its own `AGENTS.md`, that file should refine behavior for that subtree.

## Build and run

- Run Maven commands from the repository root unless a task explicitly requires module-local execution.
- Use existing scripts for local workflows:
  - `start-web.ps1`
  - `stop-web.ps1`
  - `start-swing.ps1`
  - `generate-jooq.ps1`

## Maven profile conventions

- Coverage:
  - `-Pcoverage` for coverage-oriented verification runs.
- Test execution:
  - `-Ptests-headless` for desktop/Swing CI-safe runs.
  - `-Ptests-headful` for local visual verification runs.
- Runtime:
  - `-Pruntime-dev` for local launch commands.
  - `-Pruntime-prod` for production-like launch commands.

## jOOQ and database schema rules

- Flyway migrations are the schema source of truth.
- jOOQ generated sources are version controlled under:
  - `platform-persistence-jooq/src/main/generated/jooq`
- Do not rely on implicit jOOQ generation during normal `compile`/`test`.
- When schema-relevant changes are made (migration changes, jOOQ config changes), regenerate and commit generated files:
  - `mvn -pl platform-persistence-jooq -Pjooq-codegen generate-sources`
  - or `./generate-jooq.ps1`
- Migration and generated source changes should be committed together.

## Swing theming and i18n rules

- All Swing windows/dialogs must follow FlatLaf theming and shared UI defaults.
- Avoid hardcoded user-facing strings in Swing code; use i18n keys.
- Runtime language/theme switching must update already mounted UI, not just newly opened dialogs.
- Any Swing theming change should include regression test updates:
  - headless-safe `ThemeManager` unit tests
  - GUI E2E coverage for settings-driven theme switching where applicable

## Testing expectations

- Prefer module-focused validation first, then broader reactor validation when changes cross modules.
- Minimum for persistence/schema changes:
  - `mvn -pl platform-persistence-jooq test`
- Minimum for Swing UI/theming changes:
  - `mvn -pl platform-desktop test`

## Safety and repository hygiene

- Do not commit transient artifacts from `target/`.
- Keep generated sources deterministic and reproducible.
- Do not suppress warnings by default; prefer fixing root causes.
