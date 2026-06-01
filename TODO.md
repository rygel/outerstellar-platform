# TODO

There are no open GitHub issues for this repository at the moment.

Last checked after PR #412 merged into `develop`.

## Recently completed

- #376: extension-host UI/root-route work
- #384: moved `outerstellar-i18n` into this repository
- #407: extension migration follow-ups
- #408: duplicate tracker for extension-host migration work

## Next workflow

When new work appears:

1. Start from latest `origin/develop`.
2. Create a fresh feature branch; do not reuse a merged branch.
3. Use Podman only, never the Docker CLI.
4. Run the relevant focused tests first.
5. Before commit, run:

```powershell
pwsh scripts/test.ps1
pwsh scripts/test-desktop.ps1
```
