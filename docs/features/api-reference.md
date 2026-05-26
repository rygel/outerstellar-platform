# API Reference

## REST Endpoints

### Auth

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/login` | Session login |
| `POST` | `/api/v1/auth/register` | Create account |
| `POST` | `/api/v1/auth/reset-request` | Password reset email |
| `POST` | `/api/v1/auth/reset-confirm` | Confirm reset with token |
| `POST` | `/api/v1/auth/change-password` | Change password (authenticated) |
| `GET` | `/api/v1/auth/logout` | End session |

### Messages

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/sync/pull` | Pull message changes since timestamp |
| `POST` | `/api/v1/sync/push` | Push local message changes |
| `GET` | `/api/v1/sync/pull/contacts` | Pull contact changes |
| `POST` | `/api/v1/sync/push/contacts` | Push local contact changes |

### Admin

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/admin/users` | List users (paginated) |
| `PUT` | `/api/v1/admin/users/{id}` | Update user (role, enabled) |
| `POST` | `/api/v1/admin/users/{id}/unlock` | Unlock account |

### API Keys

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/api-keys` | List keys |
| `POST` | `/api/v1/api-keys` | Create key |
| `DELETE` | `/api/v1/api-keys/{id}` | Delete key |

### Notifications

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/notifications` | List notifications |
| `POST` | `/api/v1/notifications/{id}/read` | Mark read |
| `POST` | `/api/v1/notifications/read-all` | Mark all read |

### Devices

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/devices/register` | Register push device token |
| `DELETE` | `/api/v1/devices/register` | Unregister token |

### Search

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/search?q=` | JSON search results |

### Health & Info

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Health check (local only) |
| `GET` | `/metrics` | Prometheus metrics (admin) |
| `GET` | `/robots.txt` | Robots exclusion |
| `GET` | `/sitemap.xml` | XML sitemap |
| `GET` | `/ui/openapi.json` | OpenAPI spec |

## Auth

Use `Authorization: Bearer {session_token}` or `app_session={session_token}` cookie.

## HTML Routes

All HTML routes (auth, messages, contacts, search, admin, settings) return server-rendered JTE templates with HTMX for dynamic updates. Key patterns:
- `hx-get` / `hx-post` for AJAX navigation
- `hx-trigger="load"` for lazy-loaded components
- `ws-connect="/ws/sync"` for WebSocket real-time updates
- `hx-swap="innerHTML"` for partial page updates

## Sync Protocol

The sync protocol uses pull/push with conflict detection:

```
Pull: GET /api/v1/sync/pull?since={epoch_ms}
Push: POST /api/v1/sync/push  { messages: [{ syncId, author, content, ... }] }
```

Conflicts are detected by comparing `updatedAtEpochMs`. The server version wins by default; clients can resolve via `POST /messages/resolve/{syncId}`.
