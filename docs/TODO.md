# TODO

## Platform Features

### Usability
- [x] Unified Settings page (/settings with tabs: Profile, Password, API Keys, Notifications, Appearance)
- [x] Implement web message edit and delete actions (HomeRoutes missing handlers)
- [x] Return HTMX-safe response from message creation (currently returns redirect)
- [x] Add contact trash and restore flow (soft-delete exists, no UI)
- [x] Expose JSON export routes and UI entry points (only CSV exposed currently)
- [x] Clean up primary navigation (hide Auth/Errors from default nav, surface Search/Settings)
- [x] Hide or finish Sign in with Apple (AppleOAuthProvider.exchangeCode throws "not yet implemented")
- [x] Improve accessibility labels for icon-only controls (aria-label on notification/profile/action icons)
