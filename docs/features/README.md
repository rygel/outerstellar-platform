# Outerstellar Platform — Feature Documentation

## Overview

Outerstellar is a full-stack web + desktop platform for team communication and contact management. It runs as an http4k web server with HTMX frontend and a Java Swing desktop client with two-way sync.

## Document Index

| Document | Description |
|---|---|
| [Web Application](web-application.md) | Auth, messages, contacts, notifications, search |
| [Desktop Client](desktop-client.md) | Swing UI, two-way sync, offline support |
| [Configuration](configuration.md) | AppConfig, RuntimeConfig, env vars, profiles |
| [SEO & Metadata](seo-metadata.md) | Open Graph, Twitter Card, JSON-LD, sitemap |
| [Plugin System](plugin-system.md) | PlatformPlugin interface, migrations, templates |
| [Security](security.md) | Auth, CSRF, rate limiting, CSP, SSRF protection |
| [API Reference](api-reference.md) | REST endpoints, WebSocket, sync protocol |
| [Deployment](deployment.md) | JVM tuning, Docker, native-image, profiles |
| [Performance](performance.md) | Caching, query optimization, startup timing |

## Quick Start

```bash
# Start PostgreSQL
podman compose -f docker/podman-compose.yml up -d

# Build and start web app
mvn clean install -DskipTests
./scripts/start-web.ps1
```

The web app starts on `http://localhost:8080`. See [Configuration](configuration.md) for environment variables.
