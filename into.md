# Plan: Single-Plugin Native Executable

## Problem

The current native image is built as a platform-only executable. The renderer can now search multiple precompiled JTE registries, but GraalVM only includes classes and service providers that are present on the native-image build classpath.

That means a plugin template registry is invisible unless the plugin artifact is included when the native executable is built. The runtime symptom is:

```text
Template index not found in 1 generated class registries
```

The important clue is `1 generated class registries`: only the platform registry was baked into the executable.

## Target Model

Native images should be built as a composed application:

```text
platform libraries + exactly one selected plugin + app wiring = native executable
```

The native executable is not a generic plugin host. It is a customer/plugin-specific distribution where the one plugin is selected at build time.

## Project Structure

Use three layers:

1. Platform libraries
   - `platform-core`
   - `platform-extension-api`
   - `platform-web`
   - `platform-security`
   - persistence, sync, i18n, testkit, etc.

2. Plugin module
   - Owns plugin routes, templates, migrations, assets, and extension metadata.
   - Builds a normal plugin JAR.
   - Runs the JTE generator and emits its own precompiled template registry.

3. Application/distribution module
   - Depends on platform libraries and exactly one plugin.
   - Owns fat JAR, native executable, Docker image, runtime config, and release artifacts.
   - Native image must be built from this module, not from `platform-web` alone.

The existing `starter-extension-app/starter-host` is the likely place to prove this pattern first.

## Implementation Steps

### 1. Verify Current Registry Generation

Check that plugin modules using JTE emit:

- generated template classes
- generated `JteClassRegistry`
- generated `JteClassRegistryProvider`
- `META-INF/services/io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry`

If plugin artifacts do not contain the service file, fix `platform-jte-extensions` or the plugin JTE plugin configuration first.

### 2. Move Native Build Responsibility To The Distribution Module

Do not build plugin-capable native images from `platform-web`.

Add or update a distribution module, probably `starter-extension-app/starter-host`, so it depends on:

- platform runtime modules
- the selected plugin module

Then configure the native-image build from that distribution module's classpath.

### 3. Add A Pre-Native Registry Verification

Before invoking GraalVM, run a small JVM verification on the exact composed runtime classpath.

It must fail the build unless:

- `ServiceLoader.load(PrecompiledJteTemplateRegistry::class.java)` finds the platform registry
- it also finds the plugin registry
- the expected plugin template, such as `index`, resolves from the registry list

This check should produce a direct error message:

```text
Native plugin template registry missing. Build the native image from the composed app module that depends on the selected plugin.
```

### 4. Keep Direct Class Reachability

The generated plugin registry must keep direct class references to plugin JTE template classes. This is what makes GraalVM retain those classes.

If JTE still constructs templates reflectively, the generator must also emit native-image reflection metadata for plugin template classes.

### 5. Include Service Metadata In Native Image

Ensure native-image reachability metadata includes:

```text
META-INF/services/io.github.rygel.outerstellar.platform.extension.PrecompiledJteTemplateRegistry
```

The platform metadata already includes this glob. Confirm it is applied to the final composed native build, not only to `platform-web`.

### 6. Add Regression Coverage

Add focused tests for:

- production renderer can render a template that exists only in a second registry
- service discovery sees generated registry providers
- composed app/native preflight fails when only one registry is present
- composed app/native preflight passes when platform plus plugin registries are present

The regression should assert the exact failure mode:

```text
Template index not found in 1 generated class registries
```

or its replacement fail-fast preflight message.

### 7. Build The Composed Native Executable

Build command should target the app/distribution module, for example:

```powershell
mvn -pl starter-extension-app/starter-host -am package -Pnative
```

The exact module and profile may change after inspecting the current `starter-host` build configuration.

### 8. Validate The Result

Validation must include:

- composed JVM/fat JAR can render plugin routes
- pre-native registry verification reports at least two registries
- native executable starts
- native executable renders the plugin `index` route without 500

## Success Criteria

- The native build fails early if the plugin registry is missing.
- The native build is created from a module that depends on the selected plugin.
- Runtime logs no longer say there is only `1 generated class registries` for a plugin-capable native executable.
- Plugin template routes render successfully in the native executable.

## Non-Goals

- Dynamic runtime plugin loading in native image.
- Supporting multiple plugins in one native executable unless explicitly needed later.
- Treating `platform-web` as the plugin-capable final native artifact.

## Main Risk

The main risk is that the plugin JTE registry service file exists in the plugin JAR but is not copied/merged into the final native-image input. The pre-native verification step is the guardrail against that exact packaging mistake.
