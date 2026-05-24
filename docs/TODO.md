# TODO

## Backlog

- [ ] Generic `catch (Exception)` boilerplate in DesktopSyncEngine
- [ ] DesktopSyncEngine interface for testability
- [ ] TOTP two-factor authentication
- [ ] Unified Settings page with tabs
- [ ] Search SPI (SearchProvider interface)
- [ ] Export SPI (CSV/JSON export)
- [ ] Jazzer fuzz tests
- [ ] SecurityService can shrink further — callers could bypass delegation and use AuthService/AccountService directly, leaving SecurityService as just API key + OAuth + password reset

## Completed

- [x] AppContext refactoring, Security hardening (CORS, CSP, session, audit, SSRF) (PR #239-261)
- [x] Per-account rate limiting
- [x] WebPageFactory split into 10 domain factories
- [x] fragments-seo-core integration
- [x] Responsive layout (auto-close nav, touch CSS)
- [x] App.kt refactoring (OptionalServices extraction)
- [x] SyncWindowMenu + SyncWindowNav extraction
- [x] 18 performance/runtime items (configurable knobs, startup timing, fetch optimization, indexes, adaptive outbox)
- [x] Remove Koin from server runtime, unified Dockerfile.build, native-image hardening (PR #351)
- [x] Banner SPI — BannerProvider interface, server-side banner rendering (PR #352)
- [x] Decompose SecurityService into SessionService + UserAdminService (PR #353)
- [x] Split WebContext into RequestContext + ShellRenderer (PR #354)
- [x] Remove WebContext facade, use RequestContext + ShellRenderer directly (PR #355)
- [x] Remove SecurityService session delegation, callers use SessionService directly (PR #355)
- [x] Extract AuthService + AccountService from SecurityService (PR #357)
