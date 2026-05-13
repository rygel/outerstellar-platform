# Desktop Client

## Overview

The desktop client is a Swing application with two-way sync against the web server. Build on FlatLaf theming with RemixIcon support.

## Architecture

```
SwingSyncApp.kt (665 lines)
  ├── SyncWindow         Main window (frame, panels, tables)
  ├── SyncWindowMenu     Menu bar (17 items, translations, accelerators)
  ├── SyncWindowNav      Navigation buttons (5 nav buttons, auth state)
  ├── SyncDialogs        All dialogs (login, register, settings, conflict, etc.)
  ├── SyncProfilePanel   Profile view
  ├── SyncViews          View builders (sidebar, messages, contacts, users, notifications)
  ├── DesktopSyncEngine  Sync engine (pull/push messages/contacts)
  └── SyncViewModel      State management, observer pattern
```

## Features

### Sync Engine

The `DesktopSyncEngine` (platform-sync-client module) handles:
- Login/register with session management
- Two-way message sync (pull changes, push local changes)
- Two-way contact sync (pull/push with conflict detection)
- Auto-sync every 5 minutes
- Connectivity checking (HTTP health endpoint)
- Session expiry detection and re-login
- Analytics tracking (PersistentBatchingAnalyticsService)

### Dialogs

- **Login**: Username/password authentication
- **Register**: New account creation
- **Settings**: Theme, language, connectivity configuration
- **Change password**: Authenticated password change
- **Contact form**: Create/edit contacts
- **Conflict resolver**: Server vs local message conflict
- **Help/About/Feedback/Updates**: Standard dialogs

### Theming

- FlatLaf-based with 32 DaisyUI theme colors
- ThemeManager applies FlatLaf themes + Swing component overrides
- Light/dark mode switching at runtime
- RemixIcon integration with SVG rendering

## Testing

Desktop tests use AssertJ Swing and run inside a Podman container with Xvfb:

```powershell
podman build -t outerstellar-test-desktop -f docker/Dockerfile.test-desktop .
podman run --rm --network host `
  -v "${env:USERPROFILE}\.m2\repository:/root/.m2/repository" `
  -v "${env:USERPROFILE}\.m2\settings.xml:/root/.m2/settings.xml" `
  -v "/var/run/docker.sock:/var/run/docker.sock" `
  outerstellar-test-desktop
```

126 tests cover: UI rendering (UiLayoutTest), i18n switching (SyncWindowI18nTest), auth flows, admin operations, session expiry, profile, password reset, and E2E flows (SwingAppE2ETest).
