# ADR-0003: Move i18n Into Platform

## Status: Accepted

## Context

`outerstellar-i18n` was the last runtime dependency resolved from the old `outerstellar-framework` GitHub Packages feed. The validator library and validator Maven plugin already live in this repository, while the translation bundles themselves live in `platform-core/src/main/resources`.

The remaining i18n runtime API is small: `I18nService`, `Language`, `ParameterInjector`, and `Translatable`. It is used by platform core text resolution, the web shell, Swing, JavaFX, and tests. There is no evidence in this repository that it is independently reused outside the platform, and keeping it external forces every local and CI build to keep access to the old framework package feed.

## Decision

Move `outerstellar-i18n` into `outerstellar-platform` as a first-class Maven module and publish it from this repository.

The artifact coordinates remain `io.github.rygel:outerstellar-i18n`, and the package remains `io.github.rygel.outerstellar.i18n`, so existing source imports do not change. Versioning now follows the platform release version instead of the old framework version line.

The platform build no longer declares the `outerstellar-framework` GitHub Packages repository for runtime dependencies.

## Consequences

Platform builds become self-contained with respect to Outerstellar-owned runtime libraries. Local contributors no longer need a GitHub Packages token for `outerstellar-framework` just to resolve i18n.

The old framework repository no longer owns active platform runtime code. Any future i18n change is reviewed, tested, versioned, and released with the platform.

Consumers that rely on platform dependency management get the migrated i18n artifact automatically. Consumers that depend on `outerstellar-i18n` directly should move to the platform-published version line.

Keeping the existing package name preserves source compatibility for callers, but it means `io.github.rygel.outerstellar.i18n` remains an intentional exception to the platform package-root convention.
