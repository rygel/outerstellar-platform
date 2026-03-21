# TODO

## Platform Features
- [ ] Unified Settings page (/settings with tabs: Profile, Password, API Keys, Notifications, Appearance)
- [ ] Search SPI (SearchProvider interface, /search?q= endpoint aggregating results from plugins)
- [ ] Export SPI (ExportService interface, escapeCsv utility, CSV/JSON export for any entity)
- [ ] Configurable CSP policy via AppConfig (currently hardcoded in Filters.kt)
- [ ] Layout engine performance (cache ThemeCatalog CSS, nav link caching)
- [ ] Mobile responsive layout (responsive sidebar, touch-friendly controls, viewport meta)

## Security
- [ ] Add Jazzer fuzz tests for high-risk surfaces (sync API payloads, OAuth callback parsing, i18n message resolution)
- [ ] Register on [OpenSSF CII Best Practices](https://www.bestpractices.dev/) and complete self-assessment

## Quality
- [ ] Review EI_EXPOSE_REP global SpotBugs exclusion
- [ ] Add PostgreSQL integration test profile to CI (`-Ptest-postgres` with Podman)
- [ ] Split SwingSyncApp.kt (1,895 lines) into smaller components

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
