# Architecture Decision Records

This directory records significant architectural decisions for the Outerstellar Platform.

## What belongs here

Decisions that are **expensive to reverse** or that **constrain future work**. This includes technology choices, data model conventions, API contracts, and cross-cutting concerns.

## What does NOT belong here

- Implementation details (put those in code comments or design docs)
- Bug fixes (put those in commit messages)
- Feature specs (put those in `docs/superpowers/specs/`)

## Creating a new ADR

1. Copy `TEMPLATE.md` to `NNNN-short-title.md` (increment the number)
2. Fill in every section
3. Commit alongside the code that enacts the decision

## Index

| ADR | Title | Status |
|-----|-------|--------|
| 0001 | UTC-only timestamps in the database | Accepted |
| 0002 | Native image Docker build layering | Accepted |
| 0003 | Move i18n into platform | Accepted |
| 0004 | Platform Flyway migrations must be namespaced | Accepted |

## Format

Each ADR contains:

- **Status** — Proposed, Accepted, Deprecated, Superseded by ADR-NNNN
- **Context** — the forces at play, the problem being solved
- **Decision** — what we chose and why
- **Consequences** — what becomes easier or harder as a result
