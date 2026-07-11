# Outerstellar Extension Starter

This directory is a copyable starter project for new platform extensions.

It keeps the same boundary the platform expects in production:

- `starter-extension` depends only on `outerstellar-platform-extension-api`
- `starter-host` boots the real host through `outerstellar-platform-web`

## What you get

- a minimal `PlatformExtension`
- a local launcher that mounts it into the platform
- a contract test you can extend before you boot the whole stack
- a local PostgreSQL compose file

## First run

1. Start PostgreSQL:

   ```powershell
   podman compose -f compose.yaml up -d
   ```

2. Start the app:

   ```powershell
   $env:APP_PROFILE = "dev"
   $env:ADMIN_PASSWORD = "choose-a-strong-first-admin-password"
   mvn -pl starter-host exec:java
   ```

3. Open `http://localhost:8080`.

The launcher seeds an `admin` user before opening the HTTP port. `ADMIN_PASSWORD` is required on the first boot and
must satisfy the platform password policy. Later starts do not require the variable once the administrator exists.

`APP_PROFILE=dev` explicitly enables local-only development conveniences. The checked-in default configuration keeps
development auto-login, the developer dashboard, insecure cookies, and wildcard CORS disabled.
For any non-development profile, set a deployment-specific `TOKEN_PEPPER` containing at least 32 UTF-8 bytes; the host
refuses to start without it.

## Rename checklist

1. Rename `com.example.outerstellar.starter` to your package.
2. Rename `StarterPlatformExtension`, `starter-extension`, and `starter-host`.
3. Change `id = "starter"` and `appLabel = "Starter App"`.
4. Update the root coordinates in `pom.xml`.
5. Point `outerstellar-platform.version` at the platform version you want to target.

## Files to edit first

- `starter-extension/src/main/kotlin/.../StarterPlatformExtension.kt`
- `starter-host/src/main/kotlin/.../Main.kt`
- `starter-host/src/main/resources/application.yaml`

## Why two modules?

New extensions should depend on `outerstellar-platform-extension-api` for extension code and use `outerstellar-platform-web` only in the launcher or full-stack tests. This starter keeps that split so copying it teaches the supported structure from the beginning.
