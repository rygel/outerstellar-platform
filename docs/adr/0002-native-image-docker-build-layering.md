# ADR-0002: Docker build layering strategy

## Status: Accepted

## Context

Three separate Dockerfiles (`Dockerfile`, `Dockerfile.native`, `Dockerfile.test-desktop`) share identical dependency download and Maven build stages. Any change to the build process must be replicated across all three files. The `deps` and `builder` stages are copy-pasted, leading to divergence.

## Decision

### Replace three Dockerfiles with one unified `Dockerfile.build`

Use BuildKit multi-target builds. All targets share `deps` + `builder` stages:

```
              ┌── deps (shared) ── builder (shared) ──┬── jvm-runtime (Alpine JRE)
              │                                        ├── native-compile → native-runtime (Debian slim)
              │                                        └── (separate deps) → desktop-test (Xvfb)
```

Usage:
```bash
podman build --target jvm-runtime -f docker/Dockerfile.build -t outerstellar-jvm .
podman build --target native-runtime -f docker/Dockerfile.build -t outerstellar-native .
podman build --target desktop-test -f docker/Dockerfile.build -t outerstellar-test-desktop .
```

### Desktop tests have their own deps stage

Desktop tests don't need Node.js or npm. They have a separate `desktop-deps` stage that installs Xvfb and resolves desktop-module Maven dependencies only. This keeps the test image at ~794 MB instead of ~1 GB.

### BuildKit cache mounts

Maven and npm downloads use `--mount=type=cache` to persist across builds without creating layers:
- First build: downloads everything (~60s)
- Subsequent builds: only downloads changed dependencies (~5-10s)
- Not invalidated by source changes — it's a mount, not a layer

### Optimization level build-arg for native builds

The `native-compile` stage accepts `OPT_LEVEL`:
- CI PR builds: `--build-arg OPT_LEVEL=1` (fast compilation)
- CI push to main: `--build-arg OPT_LEVEL=2` (optimized binary)
- Local: defaults to GraalVM default (no flag)

## Consequences

### What becomes easier

- **Single source of truth** for the build process — one Dockerfile, not three.
- **Shared caching** — JVM and native builds use the same `deps` + `builder` layers. Building one target warms the cache for the other.
- **Less drift** — no copy-pasted stages that silently diverge.

### What becomes harder

- **BuildKit required**. The `--mount=type=cache` syntax requires BuildKit. Podman 5.x supports this natively.
- **Desktop deps are separate**. Desktop tests don't inherit the web deps caching. This is intentional — desktop tests don't need Node.js or web-module dependencies.

### Image sizes

| Target | Size | Notes |
|---|---|---|
| `jvm-runtime` | ~249 MB | Alpine JRE |
| `native-runtime` | ~203 MB | Debian slim + libstdc++6 |
| `desktop-test` | ~794 MB | Full JDK + Maven + Xvfb (unavoidable) |
