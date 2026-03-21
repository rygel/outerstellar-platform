# TODO

## Platform Features
- [ ] Unified Settings page (/settings with tabs: Profile, Password, API Keys, Notifications, Appearance)
- [ ] Search SPI (SearchProvider interface, /search?q= endpoint aggregating results from plugins)
- [ ] Export SPI (ExportService interface, escapeCsv utility, CSV/JSON export for any entity)
- [ ] Configurable CSP policy via AppConfig (currently hardcoded in Filters.kt)
- [ ] Layout engine performance (cache ThemeCatalog CSS, nav link caching)

## Security
- [ ] Add Jazzer fuzz tests for high-risk surfaces (sync API payloads, OAuth callback parsing, i18n message resolution)
- [ ] Register on [OpenSSF CII Best Practices](https://www.bestpractices.dev/) and complete self-assessment

## Quality
- [ ] Review EI_EXPOSE_REP global SpotBugs exclusion
- [ ] Add PostgreSQL integration test profile to CI (`-Ptest-postgres` with Podman)
- [ ] Split SwingSyncApp.kt (1,895 lines) into smaller components

## MAIA Rebase
- [ ] Rebase MAIA onto outerstellar-platform (based on origin/master with 100+ commits)
- [ ] Implement MaiaCrmPlugin against PlatformPlugin interface
- [ ] Delete duplicated security, auth, layout, filter code from MAIA
- [ ] Migrate CRM Flyway migrations to plugin migration location
