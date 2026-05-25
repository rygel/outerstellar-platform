# Configuration

## Overview

Configuration flows: YAML file → environment variable → default. The `AppConfig.fromEnvironment()` method loads `application-{PROFILE}.yaml` first, then falls back to `application.yaml`.

## Profiles

| Profile | File | Use Case |
|---|---|---|
| `default` | `application.yaml` | Production-like defaults |
| `small` | `application-small.yaml` | 4 Hikari connections, small caches |
| `large` | `application-large.yaml` | 50 connections, preload enabled |
| `dev` | `application-dev.yaml` | Dev dashboard, long sessions |
| `prod` | `application-prod.yaml` | Secure cookies, no CORS wildcard |
| `test` | `application-test.yaml` | Test configuration |
| `postgres` | `application-postgres.yaml` | PostgreSQL settings |

Usage: `APP_PROFILE=small mvn exec:java`

## AppConfig Fields

### Core

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `port` | `PORT` | 8080 | HTTP server port |
| `jdbcUrl` | `JDBC_URL` | `jdbc:postgresql://localhost:5432/outerstellar` | Database URL |
| `jdbcUser` | `JDBC_USER` | `outerstellar` | Database user |
| `jdbcPassword` | `JDBC_PASSWORD` | `outerstellar` | Database password |
| `profile` | `APP_PROFILE` | `default` | Active config profile |
| `devMode` | `DEVMODE` | false | Dev auto-login (localhost only) |
| `devDashboardEnabled` | `DEV_DASHBOARD_ENABLED` | false | Admin dev dashboard |
| `sessionCookieSecure` | `SESSIONCOOKIESECURE` | true | Secure flag on session cookie |
| `sessionTimeoutMinutes` | `SESSIONTIMEOUTMINUTES` | 30 | Session idle timeout |
| `corsOrigins` | `CORSORIGINS` | "" | Allowed CORS origins (comma-separated) |
| `csrfEnabled` | `CSRFENABLED` | true | CSRF protection |
| `appBaseUrl` | `APPBASEURL` | `http://localhost:8080` | External URL for canonical/seo |
| `maxFailedLoginAttempts` | `MAX_FAILED_LOGIN_ATTEMPTS` | 10 | Account lockout threshold |
| `lockoutDurationSeconds` | `LOCKOUT_DURATION_SECONDS` | 900 | Lockout duration (seconds) |
| `cspPolicy` | `CSP_POLICY` | (default policy) | Content-Security-Policy |
| `version` | `VERSION` | `dev` | Version label |

### Runtime Sizing

| YAML Key | Env Var | Default | Description |
|---|---|---|---|
| `runtime.hikariMaximumPoolSize` | `HIKARI_MAX_POOL_SIZE` | 20 | Max DB connections |
| `runtime.hikariMinimumIdle` | `HIKARI_MIN_IDLE` | 2 | Min idle connections |
| `runtime.hikariIdleTimeoutMs` | `HIKARI_IDLE_TIMEOUT_MS` | 300000 | Idle connection timeout |
| `runtime.hikariMaxLifetimeMs` | `HIKARI_MAX_LIFETIME_MS` | 1800000 | Connection max lifetime |
| `runtime.hikariConnectionTimeoutMs` | `HIKARI_CONNECTION_TIMEOUT_MS` | 10000 | Connection wait timeout |
| `runtime.hikariLeakDetectionThresholdMs` | `HIKARI_LEAK_DETECTION_THRESHOLD_MS` | 60000 | Connection leak detection |
| `runtime.flywayEnabled` | `FLYWAY_ENABLED` | true | Run migrations on startup |
| `runtime.jtePreloadEnabled` | `JTE_PRELOAD_ENABLED` | false | Preload JTE template classes |
| `runtime.cacheMessageMaxSize` | `CACHE_MESSAGE_MAX_SIZE` | 1000 | Message cache max entries |
| `runtime.cacheMessageExpireMinutes` | `CACHE_MESSAGE_EXPIRE_MINUTES` | 10 | Message cache TTL |
| `runtime.cacheGravatarMaxSize` | `CACHE_GRAVATAR_MAX_SIZE` | 10000 | Gravatar cache max entries |
| `runtime.rateLimitIpCapacity` | `RATE_LIMIT_IP_CAPACITY` | 10 | Per-IP token bucket |
| `runtime.rateLimitIpRefillPerMinute` | `RATE_LIMIT_IP_REFILL_PER_MINUTE` | 10 | Per-IP refill rate |
| `runtime.rateLimitAccountCapacity` | `RATE_LIMIT_ACCOUNT_CAPACITY` | 20 | Per-account token bucket |
| `runtime.rateLimitAccountWindowMs` | `RATE_LIMIT_ACCOUNT_WINDOW_MS` | 900000 | Per-account refill window |

### Sub-configs

- `segment.*` — Segment analytics (`SEGMENT_WRITEKEY`, `SEGMENT_ENABLED`)
- `email.*` — SMTP settings (`EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, etc.)
- `jwt.*` — JWT auth (`JWT_SECRET`, `JWT_ENABLED`, `JWT_ISSUER`)
- `appleOAuth.*` — Sign in with Apple (`APPLE_OAUTH_ENABLED`, etc.)
- `pushNotifications.*` — Push notification service

## Environment Variable Precedence

1. `APP_PROFILE` selects the YAML file
2. YAML file values are loaded
3. Each field is overridden by its env var (if set)
4. Missing fields use defaults from data class constructors

Example: `JDBC_URL=jdbc:postgresql://prod:5432/outerstellar APP_PROFILE=prod mvn exec:java`
