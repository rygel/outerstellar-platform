# TODO

## Security
- [ ] Add Jazzer fuzz tests for high-risk surfaces (sync API payloads, OAuth callback parsing, i18n message resolution)
- [ ] Register on [OpenSSF CII Best Practices](https://www.bestpractices.dev/) and complete self-assessment

## Quality
- [ ] Review EI_EXPOSE_REP global SpotBugs exclusion — investigate if Kotlin immutable List bytecode can be handled without global suppression
- [ ] Add PostgreSQL integration test profile to CI (`-Ptest-postgres` with Podman)

## Platform Release
- [ ] Merge develop → main and release v0.1.0
- [ ] Publish to GitHub Packages

## MAIA Rebase
- [ ] Rebase MAIA onto outerstellar-platform (feat/platform-rebase branch)
- [ ] Implement MaiaCrmPlugin against PlatformPlugin interface
- [ ] Delete duplicated security, auth, layout, filter code from MAIA
- [ ] Migrate CRM Flyway migrations to plugin migration location
- [ ] Layout engine performance improvements (cache ThemeCatalog CSS, nav link caching)
