# Security

## Authentication

- **Session tokens**: Opaque 192-bit tokens (`oss_` prefix), SHA-256 hashed in DB
- **API keys**: Prefix `osk_`, stored as hash, scoped to user
- **JWT**: Optional, configurable via `jwt.*` settings
- **Multi-realm**: `SessionRealm` + `ApiKeyRealm` chain, pluggable via `AuthRealm` interface
- **Dev mode**: `devMode=true` enables auto-login as admin (localhost-only via Host header check)

## Authorization

- Route-level `SecurityRules.authenticated()` and `SecurityRules.hasRole(role)` filters
- `UserRole` enum: `ADMIN`, `USER` (two copies exist — model and security package, use clean build)
- `PermissionResolver` interface for fine-grained permissions

## CSRF

- Double-submit cookie pattern
- `_csrf` cookie + form field or `X-CSRF-Token` header
- Configurable via `csrfEnabled`
- Safe methods (GET/HEAD/OPTIONS) skip validation

## Rate Limiting

- **Per-IP**: Token bucket, 10 req/min, keyed by client IP
- **Per-account**: Token bucket, 20 req/15min, keyed by `username` or `email` from request body
- **Sensitive paths**: Reset endpoints throttled to 5 req/15min
- Configurable via `runtime.rateLimit*` settings

## CSP

`Content-Security-Policy` header configurable via `CSP_POLICY` env var / `cspPolicy` YAML field.

Default policy:
```
default-src 'self'; script-src 'self' 'unsafe-inline';
style-src 'self' 'unsafe-inline'; font-src 'self';
connect-src 'self' ws: wss:; img-src 'self' data:;
```

## SSRF Protection

Avatar URL validation via `UrlValidator` object:
- HTTPS/HTTP scheme enforced
- 2048 char max length
- Private IP patterns blocked: localhost, RFC 1918, IPv6 link-local/unique-local, `.local`, `.internal`

## Session Security

- `Secure` flag on session cookie (configurable, defaults `true`)
- `SameSite=Strict` on preference cookies
- HSTS header: `max-age=31536000; includeSubDomains`
- Session timeout with sliding window extension
- All password changes invalidate existing sessions
- Account lockout after `maxFailedLoginAttempts` failures (default 10)

## Health Endpoint

`/health` is restricted to localhost via Host header check (not IP-based, works through Docker port mapping).

## Audit Logging

All security events logged to `plt_audit_log`:
- Authentication failures (with reason)
- API key creation/deletion
- Password changes
- Admin operations (user enable/disable, role change, unlock)
