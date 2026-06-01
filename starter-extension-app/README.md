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
   mvn -pl starter-host exec:java
   ```

3. Open `http://localhost:8080`.

The launcher seeds an `admin` user on first boot. Set `ADMIN_PASSWORD` before startup if you do not want to use the scaffold default.

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
