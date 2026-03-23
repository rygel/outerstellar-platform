# Outerstellar Platform — Feature Reference

## Core Infrastructure

### Authentication & Authorization
- **Session-based auth** — cookie-based sessions with configurable timeout
- **JWT auth** — optional stateless token authentication for APIs and devices
- **API key auth** — per-user API keys with prefix-based lookup
- **OAuth 2.0** — social sign-in (Apple, extensible to other providers)
- **Password reset** — token-based password reset flow with email delivery
- **Role-based access** — USER and ADMIN roles with route-level enforcement
- **Fine-grained permissions** — wildcard `domain:action:instance` permission model with pluggable `PermissionResolver`
- **Multi-realm authentication** — composable `AuthRealm` chain (session, API key, custom) for bearer token resolution
- **CSRF protection** — double-submit cookie pattern for HTML form routes
- **Async activity tracking** — non-blocking last-activity updates via batching

### User Management
- **User registration and login** — with BCrypt password hashing
- **Admin dashboard** — user listing, enable/disable, role promotion/demotion
- **Audit log** — timestamped record of admin actions
- **Profile management** — username, email, avatar (Gravatar), notification preferences
- **Account deletion** — self-service account removal
- **User preferences persistence** — language, theme, and layout stored in User model, follow user across devices

### Settings
- **Unified settings page** — tabbed UI at `/settings` (Profile, Password, API Keys, Notifications, Appearance)
- **Theme selection** — dark/light mode toggle + theme catalog with 30+ themes, dark mode preview swatches
- **Language selection** — i18n with locale switching (English, French, German)
- **Layout selection** — sidebar or topbar layout
- **Preferences sync** — settings saved to user account when logged in, cookies for anonymous users

### Search
- **Search SPI** — `SearchProvider` interface for plugins to register searchable entities
- **Search page** — `/search?q=` with aggregated results across all providers
- **Search API** — `GET /api/v1/search?q=` JSON endpoint

### Export
- **Export SPI** — `ExportProvider` interface for plugins to register exportable entities
- **CSV utilities** — `CsvUtils.escapeCsv()` and `CsvUtils.toCsvRow()` for safe CSV generation
- **Built-in exports** — user list and audit log as CSV

## Web Framework

### Routing & Templates
- **http4k** — contract-based routing with OpenAPI documentation
- **JTE/KTE templates** — precompiled Kotlin templates with hot-reload in dev mode
- **PlatformPlugin interface** — apps register routes, nav items, Koin modules, and migrations
- **PluginContext** — shared services (renderer, config, security, analytics) passed to plugins
- **Layout router** — sidebar and topbar layout variants with automatic dispatching

### Filters & Middleware
- **Correlation ID** — `X-Request-Id` tracking across requests
- **CORS** — configurable cross-origin resource sharing
- **Security headers** — X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy
- **Content Security Policy** — configurable CSP via `AppConfig.cspPolicy`
- **ETag caching** — hash-based ETags with 304 Not Modified support
- **Static asset caching** — 1-year immutable Cache-Control for CSS, JS, fonts, images
- **Rate limiting** — per-IP rate limiting
- **Request logging** — structured access logs with timing
- **Session timeout** — configurable session expiry with redirect to login
- **OpenTelemetry** — distributed tracing integration
- **Micrometer metrics** — request counters and timers (Prometheus-compatible)

### Real-time
- **WebSocket sync** — `/ws/sync` for real-time UI refresh notifications
- **Transactional outbox** — reliable event delivery with outbox pattern
- **Event publisher** — `publishRefresh(targetId)` for triggering UI updates

## Persistence

### Database Support
- **H2** — embedded database for development and testing (PostgreSQL compatibility mode)
- **PostgreSQL** — production database support
- **Flyway migrations** — versioned schema management with plugin migration support
- **jOOQ** — type-safe SQL with code generation
- **JDBI** — lightweight SQL abstraction (alternative to jOOQ)
- **HikariCP** — connection pooling

### Repositories
- Message repository (CRUD, sync, conflict tracking)
- Contact repository (CRUD, search, sync)
- User repository (with caching layer)
- Session repository
- API key repository
- OAuth connection repository
- Password reset token repository
- Audit log repository
- Notification repository
- Outbox repository
- Device token repository

## Sync & Messaging

### Two-way Sync
- **Push/pull API** — `POST /api/v1/sync` (push dirty) and `GET /api/v1/sync?since=` (pull changes)
- **Contact sync** — separate sync endpoints for contacts
- **Conflict detection** — last-write-wins with manual conflict resolution (MINE/THEIRS)
- **Dirty tracking** — `dirty` flag on entities for offline-first sync
- **Soft delete** — `deleted` flag preserves sync history

### Notifications
- **In-app notifications** — per-user notification list with read/unread
- **Notification bell** — real-time unread count in UI
- **Push notification support** — device token registration API

## Email
- **SimpleJavaMail** — clean email API with SMTP/TLS support
- **Circuit breaker** — Resilience4j circuit breaker prevents cascading failures when SMTP is down
- **Console email** — development fallback that logs emails to console
- **No-op email** — silent fallback for tests

## Analytics & Observability
- **Segment analytics** — optional user tracking (identify, track, page)
- **OpenTelemetry** — distributed tracing with configurable exporters
- **Prometheus metrics** — `/metrics` endpoint for scraping
- **Dev dashboard** — admin-only diagnostics (cache stats, outbox stats, metrics)

## Desktop (Swing)
- **Cross-platform Swing UI** — FlatLaf look-and-feel with theme support
- **Two-way sync** — desktop client syncs with server via REST API
- **System tray** — minimize to tray with notification badges
- **Deep links** — custom protocol handler for opening from browser
- **Connectivity checker** — automatic reconnection on network changes
- **Update service** — check for new versions

## i18n & Theming
- **I18nService** — message bundle resolution with locale switching
- **Parameter injection** — `{0}`, `{name}` placeholders in messages
- **Theme catalog** — 30+ themes with CSS variable generation
- **Smart shader** — automatic color derivation for consistent palettes

## CI/CD & Quality
- **GitHub Actions** — build, test, quality, desktop (Xvfb), E2E (Playwright), security (OWASP)
- **CodeQL** — semantic code analysis for Java/Kotlin
- **Gitleaks** — secret detection in git history
- **Hadolint** — Dockerfile linting
- **Zizmor** — GitHub Actions security scanning
- **OSSF Scorecard** — OpenSSF project health
- **actionlint** — GitHub Actions syntax linting
- **Detekt** — Kotlin static analysis
- **SpotBugs** — Java bug detection
- **Checkstyle** — code style enforcement
- **PMD** — code smell detection
- **JaCoCo** — test coverage reporting
- **Spotless** — code formatting (ktfmt)
- **Maven Enforcer** — dependency convergence, Java version, duplicate classes
- **Dependabot** — grouped dependency updates targeting develop
- **Branch protection** — required PRs and status checks on main and develop
