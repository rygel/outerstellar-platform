# TODO

## Platform Features

### Usability
- [ ] Unified Settings page (/settings with tabs: Profile, Password, API Keys, Notifications, Appearance)
- [ ] Implement web message edit and delete actions (HomeRoutes missing handlers)
- [ ] Return HTMX-safe response from message creation (currently returns redirect)
- [ ] Add contact trash and restore flow (soft-delete exists, no UI)
- [ ] Preserve contact list state during create/update/delete (query/offset/pagination)
- [ ] Bring desktop message/contact actions to parity with web/domain services
- [ ] Expose JSON export routes and UI entry points (only CSV exposed currently)
- [ ] Make search discoverable — global search box, type filters, result highlighting
- [ ] Complete TOTP setup UX (localize, styled fragments, backup-code management)
- [ ] Hide or finish Sign in with Apple (AppleOAuthProvider.exchangeCode throws "not yet implemented")
- [ ] Expand admin user-management workflows (create user, edit, force password reset, confirmations)
- [ ] Clean up primary navigation (hide Auth/Errors from default nav, surface Search/Settings)
- [ ] JavaFX desktop module implementation (scaffolded but not implemented)

### Search & Export SPI
- [ ] Search SPI (SearchProvider interface, /search?q= endpoint aggregating results from plugins)
- [ ] Export SPI (ExportService interface, escapeCsv utility, CSV/JSON export for any entity)

## Security

### High Priority
- [ ] TOTP two-factor authentication (secret generation, verification, setup flow, login enforcement)
- [x] Harden password reset tokens (UUID v7 with SecureRandom, time-ordered, 122-bit randomness)
- [x] Neutralize formula injection in CSV exports (prefix `=`, `+`, `-`, `@`, tab, CR in cells)
- [ ] Add Jazzer fuzz tests for high-risk surfaces (sync API payloads, OAuth callback parsing, i18n message resolution)

### Medium Priority
- [x] Use configured `appBaseUrl` for OAuth redirect URIs (avoid host-header poisoning)
- [x] Trust forwarded IP headers only from configured proxies (X-Forwarded-For, X-Real-IP)
- [x] Scope device-token deregistration to authenticated user (match by token + user_id)
- [x] Remove `unsafe-inline` from script CSP (move to nonce/hash-based CSP)
- [x] Apply the web filter chain only once (currently doubled — rate-limit, session, headers, log)
- [ ] Register on [OpenSSF CII Best Practices](https://www.bestpractices.dev/) and complete self-assessment

## Performance

### Data & Queries
- [ ] Batch sync push upserts (bulk prefetch by sync_id, batched INSERT ON CONFLICT)
- [ ] Bound sync pull and dirty push batches (page/batch limits with continuation cursors)
- [x] Add text-search indexes for `%LIKE%` search paths (PostgreSQL pg_trgm GIN or full-text)
- [ ] Reduce paired list/count pagination queries (seek pagination, approximate/cached counts)
- [x] Avoid loading all users for admin row actions (add findUserSummary by id)
- [ ] Stream or page CSV exports (currently builds full response in memory)
- [ ] Push search ranking and limits closer to providers (avoid extra query and sort work)

### Caching & Rendering
- [x] Wire runtime cache settings into `CaffeineMessageCache` (uses hardcoded defaults)
- [x] Replace prefix-scan message cache invalidation (O(1) scheme instead of key scan)
- [x] Restrict dynamic ETag hashing (limit to small/static text, skip dynamic HTML)
- [x] Precompute JTE template class lookup (build Map once instead of scanning on each render)
- [x] Resolve session state once per request (cache single lookup for user + sessionExpired)
- [x] Use bounded executor for Segment analytics (currently one daemon thread per event)
- [ ] Debounce desktop search reloads (currently reloads on every query change)

## Architecture

- [ ] Add `DesktopSyncEngine` interface for testability (SyncViewModel depends on concrete class)
- [ ] Configurable CSP policy via AppConfig (currently hardcoded in Filters.kt)
- [ ] Layout engine performance (cache ThemeCatalog CSS, nav link caching)

## Quality

- [ ] Review EI_EXPOSE_REP global SpotBugs exclusion
- [ ] Add PostgreSQL integration test profile to CI (`-Ptest-postgres` with Podman)
- [ ] Split SwingSyncApp.kt into smaller components
- [ ] Localize remaining desktop user-facing strings (conflict resolution, contact dialogs, spell-check, theme preview)
- [ ] Improve accessibility labels for icon-only controls (aria-label on notification/profile/action icons)

## MAIA Backport (Utilities)
- [x] DialogUtil — copyable info/warning/error/confirmation dialogs (desktop)
- [x] UnicodeIcon — Unicode emoji/symbol rendering as Swing Icons (desktop)
- [x] SpellChecker + SpellCheckingTextArea — dictionary-based spell checking (desktop)
- [x] SpellCheckingTextField — inline spell-check for text fields (desktop)
- [x] ChipCellRenderer — generic rounded-chip table cell renderer (desktop)
- [x] Enhanced ThemeCatalog — brightness adjustments, luminance detection, typography scale (web)

## MAIA Rebase
- [ ] Rebase MAIA onto outerstellar-platform (based on origin/master with 100+ commits)
- [ ] Implement MaiaCrmPlugin against PlatformPlugin interface
- [ ] Delete duplicated security, auth, layout, filter code from MAIA
- [ ] Migrate CRM Flyway migrations to plugin migration location
