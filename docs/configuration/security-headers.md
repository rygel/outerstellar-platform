# Security Response Headers Configuration

The platform applies security response headers on every HTTP response. All headers are configurable globally via YAML or environment variables, and can be overridden per-route using Ant-style glob patterns.

## Global configuration

### YAML

```yaml
securityHeaders:
  permissionsPolicy: "camera=(), microphone=(), geolocation=()"
  referrerPolicy: "strict-origin-when-cross-origin"
  xFrameOptions: "DENY"
  xContentTypeOptions: "nosniff"
  strictTransportSecurity: "max-age=31536000; includeSubDomains"
```

### Environment variables

| YAML Key | Env Var | Default |
|---|---|---|
| `securityHeaders.permissionsPolicy` | `PERMISSIONS_POLICY` | `camera=(), microphone=(), geolocation=()` |
| `securityHeaders.referrerPolicy` | `REFERRER_POLICY` | `strict-origin-when-cross-origin` |
| `securityHeaders.xFrameOptions` | `X_FRAME_OPTIONS` | `DENY` |
| `securityHeaders.xContentTypeOptions` | `X_CONTENT_TYPE_OPTIONS` | `nosniff` |
| `securityHeaders.strictTransportSecurity` | `STRICT_TRANSPORT_SECURITY` | `max-age=31536000; includeSubDomains` |

Set `STRICT_TRANSPORT_SECURITY` to an empty string to suppress HSTS (useful in dev mode).

CSP is configured separately via `cspPolicy` / `CSP_POLICY` (existing, unchanged).
CORS is configured separately via `corsOrigins` / `CORSORIGINS`. Values are comma-separated exact
`http[s]://host[:port]` origins. Empty configuration disables CORS; `*` allows any origin and cannot be combined with
explicit origins. Invalid values fail during application assembly. Per-route lists follow the same rules. For an
explicit allowlist, the matching request origin is echoed and `Vary: Origin` is added; a comma-separated list is never
written into `Access-Control-Allow-Origin`.

## Per-route overrides

Per-route overrides allow specific security headers to be changed for matching URL paths. Overrides use Ant-style glob patterns.

### Pattern syntax

| Pattern | Matches | Does NOT match |
|---|---|---|
| `/map/**` | `/map`, `/map/europe`, `/map/europe/france` | `/api/map` |
| `/api/*/users` | `/api/v1/users` | `/api/v1/admin/users` |
| `/static/*.css` | `/static/site.css` | `/static/sub/site.css` |
| `/login` | `/login` only | `/login/callback` |

- `*` matches one path segment (no slashes)
- `**` matches any number of segments
- Plain string without wildcards is exact match
- First matching pattern in the list wins

### YAML example

```yaml
securityHeaders:
  perRouteOverrides:
    - pattern: "/map/**"
      permissionsPolicy: "geolocation=(self), camera=(), microphone=()"
      csp: "default-src 'self'; script-src 'self' {nonce}; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.tile.openstreetmap.org; base-uri 'self'; form-action 'self'"
    - pattern: "/api/external/**"
      corsAllowedOrigins:
        - "*"
    - pattern: "/embed/*"
      xFrameOptions: "ALLOW-FROM https://example.com"
```

### Fields

Each per-route override has a `pattern` (required) and any combination of optional header overrides. Fields not set in the override fall back to the global config value.

| Field | Overrides |
|---|---|
| `permissionsPolicy` | `Permissions-Policy` header |
| `referrerPolicy` | `Referrer-Policy` header |
| `xFrameOptions` | `X-Frame-Options` header |
| `xContentTypeOptions` | `X-Content-Type-Options` header |
| `strictTransportSecurity` | `Strict-Transport-Security` header (blank = suppress) |
| `csp` | `Content-Security-Policy` header (`{nonce}` is substituted) |
| `corsAllowedOrigins` | Exact CORS allowlist for the route (`[]` disables CORS for that route) |

### Filter chain order

Per-route overrides are resolved inside the `securityHeaders` filter (position 5 in the chain), which sits above extension filters. On the response path, `securityHeaders` applies headers after extensions have modified the response. This means per-route overrides always win — an extension cannot accidentally regress a declared security policy.
