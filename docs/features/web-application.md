# Web Application

## Architecture

- **Server**: http4k with Netty backend
- **Templates**: JTE (Java Template Engine) — precompiled in production
- **Frontend**: HTMX + DaisyUI 5 (Tailwind CSS v4)
- **I18n**: Custom I18nService, EN + FR locales

## Features

### Authentication

| Feature | Route | Description |
|---|---|---|
| Sign in | `POST /auth/components/result` | Session-based auth with opaque tokens |
| Register | `POST /auth/components/result?mode=register` | New user registration |
| Password reset | `GET /auth/reset/{token}` | Token-based reset flow |
| Change password | `POST /auth/components/change-password` | Authenticated password change |
| OAuth (Apple) | `GET /auth/oauth/apple` | Sign in with Apple (configurable) |
| Logout | `POST /auth/logout` | Session invalidation |

Auth uses opaque session tokens (`oss_` prefix, 192-bit entropy). Sessions expire after configurable timeout (default 30 min) with sliding-window extension. Rate limiting: 10 req/min per IP, 20 req/15min per account.

### Messages

| Feature | Route | Description |
|---|---|---|
| List | `GET /` | Paginated message list with search |
| Create | `POST /messages` | New server message |
| Delete | `POST /messages/{syncId}/delete` | Soft delete |
| Restore | `POST /messages/{syncId}/restore` | Restore deleted |
| Conflict resolution | `POST /messages/resolve/{syncId}` | Resolve sync conflicts |

### Contacts

| Feature | Route | Description |
|---|---|---|
| List | `GET /contacts` | Paginated with search |
| Create | `POST /contacts` | New contact (name, emails, phones, social, company) |
| Edit | `POST /contacts/{syncId}/update` | Update contact |
| Delete | `POST /contacts/{syncId}/delete` | Soft delete |

### Notifications

| Feature | Route | Description |
|---|---|---|
| List | `GET /notifications` | User notifications |
| Mark read | `POST /notifications/{id}/read` | Single |
| Mark all read | `POST /notifications/read-all` | Bulk |
| Bell | `GET /components/notification-bell` | HTMX-loaded badge |

### Search

| Feature | Route | Description |
|---|---|---|
| Search page | `GET /search?q=` | HTML results |
| JSON API | `GET /api/v1/search?q=` | JSON results |
| Search providers | SPI | `SearchProvider` interface for plugin extensibility |

### Admin

| Feature | Route | Description |
|---|---|---|
| User management | `GET /admin/users` | List, enable/disable, role toggle, unlock |
| Audit log | `GET /admin/audit` | Paginated audit trail |
| Dev dashboard | `GET /admin/dev` | Cache stats, outbox stats, telemetry |
| CSV export | `GET /admin/users?format=csv` | User list export |
| API key management | `GET /auth/api-keys` | Create/delete API keys |

### Settings

| Feature | Route | Description |
|---|---|---|
| Profile | `GET /auth/profile` | Email, username, avatar |
| Password | `GET /auth/profile` | Change password |
| Notifications | `GET /notifications` | Preferences |
| Appearance | Sidebar | Theme, language, layout selectors |

## Layout

Two shell layouts switchable at runtime:
- **Sidebar** (default): Left nav panel with icons + labels
- **Topbar**: Horizontal nav bar

Three density modes: nice (default), cozy, compact.

## Real-time Updates

WebSocket at `/ws/sync` provides real-time UI refresh via HTMX `ws-subscribe`. The server pushes `HX-Trigger` events for message list, contact list, and notification bell updates.
