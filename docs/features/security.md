# Security

## Authentication

- **Session tokens**: Opaque 192-bit tokens (`oss_` prefix), HMAC-SHA256 hashed in DB
- **API keys**: Prefix `osk_`, stored as hash, scoped to user
- **JWT**: Optional, configurable via `jwt.*` settings
- **Multi-realm**: `SessionRealm` + `ApiKeyRealm` chain, pluggable via `AuthRealm` interface
- **Dev mode**: `devMode=true` enables auto-login only for direct loopback connections in the `dev` or `test` profile.
  Requests without a source address and requests carrying proxy-forwarding headers are denied.
- **TOTP lifecycle**: Enabling, regenerating, or disabling TOTP requires confirmation of the authenticated user's current
  password. Enrollment is persisted only after both the password and an authenticator code are valid. One-time recovery
  codes are then shown for copying or download; only their BCrypt hashes are stored. TOTP seeds are encrypted at rest
  with AES-GCM using a key derived from `TOKEN_PEPPER`; legacy plaintext seeds are encrypted on first TOTP verification.

## Secure startup

- `ADMIN_PASSWORD` is required when the initial `admin` account does not exist.
- The password must satisfy the normal platform password policy.
- Administrator bootstrap completes before the HTTP server opens its port.
- Existing installations do not require `ADMIN_PASSWORD` on subsequent starts.
- Generated starter applications use secure defaults and never provide a known administrator password.
- `TOKEN_PEPPER` must be deployment-specific and contain at least 32 UTF-8 bytes. There is no production fallback;
  application assembly fails before binding a port when it is absent or too short. Rotating it invalidates existing
  sessions, API keys, and outstanding password-reset tokens and makes already-encrypted TOTP seeds unreadable; restore
  the prior value before attempting a planned key migration.

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
The default policy permits scripts only from the application origin with a per-response nonce. Production JTE templates
must use delegated `data-*` controls handled by `/platform.js`; inline event attributes such as `onclick` are rejected
during Maven validation and are also covered by an authenticated rendering integration test.

Default policy:
```
default-src 'self'; script-src 'self' {nonce};
style-src 'self' 'unsafe-inline'; font-src 'self';
connect-src 'self' wss:; img-src 'self' data: https:;
base-uri 'self'; form-action 'self'
```

`{nonce}` is replaced per request with a cryptographically random `nonce-...` source. Platform layouts expose the same
value as `ShellView.cspNonce` and render it on script tags.

## SSRF Protection

Avatar URL validation via `UrlValidator` object:
- HTTPS/HTTP scheme enforced
- 2048 char max length
- Private IP patterns blocked: localhost, RFC 1918, IPv6 link-local/unique-local, `.local`, `.internal`

## Session Security

- `Secure` flag on session cookie (configurable, defaults `true`)
- `SameSite=Strict` on preference cookies
- HSTS header: `max-age=31536000; includeSubDomains`
- Session timeout with sliding window extension; each active request refreshes both the database deadline and browser
  cookie `Max-Age`, while expiry and logout responses clear the cookie.
- All password changes invalidate existing sessions
- Account lockout after `maxFailedLoginAttempts` failures (default 10)
- Passwords are validated, hashed, and matched exactly as entered, including leading/trailing whitespace.
- Password-reset input is validated before the one-time token is claimed, so a correctable weak-password error does not
  consume the token.

## Health Endpoint

`/health`, `/health/live`, `/health/ready`, and `/debug/routes` accept direct loopback connections with no forwarding
headers. Remote probes must send `Authorization: Bearer <MANAGEMENT_TOKEN>`, where the configured token contains at
least 32 UTF-8 bytes and no whitespace. Requests without a transport source fail closed; the client-controlled `Host`
header is never used for authorization.

## Audit Logging

All security events logged to `plt_audit_log`:
- Authentication failures (with reason)
- API key creation/deletion
- Password changes
- Admin operations (user enable/disable, role change, unlock)
