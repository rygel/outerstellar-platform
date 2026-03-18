# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [1.0.6] – 2026-03-18

### Added
- Shared `components/Pagination.kte` component — single source of truth for prev/next navigation; replaces three divergent inline implementations across AuditLog, UserAdmin, and Contacts pages
- Shared `components/PageHeader.kte` component — unified page title/description/breadcrumb block with an optional `@Content` slot for action buttons
- Shared `components/ContactForm.kte` HTMX modal — create and edit contacts without leaving the page
- `.data-table` CSS class (plus `.th-center`, `.td-center`, `.td-mono` modifiers) — replaces inline `style=` on every `<th>` and `<td>` across AuditLog, UserAdmin, and ApiKeys pages
- Contact CRUD routes: `POST /contacts`, `GET /contacts/new`, `GET /contacts/{id}/edit`, `POST /contacts/{id}/update`, `POST /contacts/{id}/delete`
- i18n keys for contact form labels (EN + FR)

### Fixed
- `jackson-module-kotlin` promoted from `test` to `compile` scope in `web/pom.xml`; the previous `test`-scope declaration silently overrode the transitive `compile` dependency from `http4k-format-jackson`, breaking `ThemeCatalog` at compile time

---

## [1.0.5] – 2026-03-18

### Performance
- Skip BCrypt encode on every restart — admin password is only hashed when the admin user does not yet exist, saving ~100 ms per boot after first run
- HikariCP `minimumIdle` reduced from 2 to 1, opening one fewer eager DB connection at startup
- Enabled `CaffeineMessageCache` (max 1 000 entries, 10-minute TTL) in place of the no-op cache; entity-level hits benefit the sync push path and list results are cached between writes

### Fixed
- `AppConfig` profile load order corrected so profile-specific YAML (`application-prod.yaml`) overrides the base `application.yaml` as intended

### Changed
- `SwingAppConfig` now supports profile-based config loading (`APP_PROFILE` env var) matching the web application pattern
- Added `desktop/src/main/resources/application.yaml`, `application-dev.yaml`, and `application-prod.yaml` resource files for the desktop module

---

## [1.0.4] – 2026-03-18

### Security
- Replaced raw UUID bearer tokens with opaque session tokens (`oss_` prefix, 192-bit entropy)
- Session tokens are stored as SHA-256 hashes in a new `sessions` table; raw tokens are never persisted
- Sliding-window expiry: every authenticated request extends the session by the configured timeout (default 30 minutes)
- Expired sessions return `HTTP 401` with `X-Session-Expired: true` header so clients can distinguish expiry from invalid tokens
- API key bearer auth preserved as a fallback for `osk_`-prefixed tokens

---

## [1.0.3] – 2026-03-17

### Added
- `OAuthRepository` and `DeviceTokenRepository` interfaces with Jooq and Jdbi implementations
- `MessageRestoreIntegrationTest` covering soft-deleted message restore flow

---

## [1.0.2] – 2026-03-17

### Changed
- All CDN-hosted frontend dependencies (htmx, Alpine.js, chart.js, etc.) vendored as local static assets — no external network calls at runtime

---

## [1.0.1] – 2026-03-16

### Changed
- Updated all dependencies to latest stable versions
- Upgraded ktfmt, Jackson, and Maven plugins
- Fixed Playwright test compatibility after dependency updates
- Applied ktfmt formatting to `SyncModels.kt`

---

## [1.0.0] – 2026-03-16

Initial release.
