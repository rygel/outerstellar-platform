# TODO

## Platform Features

### Usability
- [ ] Unified Settings page (/settings with tabs: Profile, Password, API Keys, Notifications, Appearance)
- [x] Implement web message edit and delete actions (HomeRoutes missing handlers)
- [x] Return HTMX-safe response from message creation (currently returns redirect)
- [x] Add contact trash and restore flow (soft-delete exists, no UI)
- [x] Expose JSON export routes and UI entry points (only CSV exposed currently)
- [x] Clean up primary navigation (hide Auth/Errors from default nav, surface Search/Settings)
- [x] Hide or finish Sign in with Apple (AppleOAuthProvider.exchangeCode throws "not yet implemented")
- [x] Improve accessibility labels for icon-only controls (aria-label on notification/profile/action icons)

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
