# Plugin Text & Template Override

**Date:** 2026-05-05  
**Status:** Approved  
**Scope:** platform-web, platform-core

## Problem

All ~90 user-facing strings in the JTE templates are hardcoded. Plugins cannot change any text — labels, headings, button copy, error messages, nav items — without forking the entire platform. The sidebar language selector is cosmetic (no i18n backend). The platform needs two capabilities:

1. Every string in every template resolved through a key system that plugins can override
2. Per-template override so plugins can replace individual `.kte` files without forking all of them

## Constraints

- Plugin-only override (no runtime multi-language switching by end users)
- One language at a time — the plugin controls all texts
- Backward compatible — existing apps without a plugin keep working with default English strings
- No new runtime dependencies

## Design

### 1. TextResolver interface

```kotlin
// platform-core
interface TextResolver {
    fun resolve(key: String, vararg args: Any?): String
}
```

- `resolve("nav.inbox")` returns `"Inbox"` by default
- `resolve("messages.confirm_delete", 5)` returns `"Delete 5 messages?"` using standard `String.format` substitution
- Key not found → return the key itself (fail visible, not silent)

### 2. DefaultTextResolver

```kotlin
// platform-core
class DefaultTextResolver : TextResolver {
    private val texts: Map<String, String> = load from "texts.properties"
    override fun resolve(key: String, vararg args: Any?): String {
        val template = texts[key] ?: return key
        return if (args.isEmpty()) template else String.format(template, *args)
    }
}
```

- Loads from `platform-web/src/main/resources/texts.properties`
- Immutable after construction
- Keys are dot-namespaced: `nav.inbox`, `auth.login`, `contacts.title`, `error.not_found`, etc.

### 3. texts.properties

New file at `platform-web/src/main/resources/texts.properties`. Contains all ~90 strings extracted from templates. Examples:

```properties
# Nav
nav.inbox=Inbox
nav.contacts=Contacts
nav.trash=Trash
nav.settings=Settings
nav.profile=Profile
nav.admin=Admin
nav.api_keys=API Keys
nav.audit_log=Audit Log
nav.dev_dashboard=Dev Dashboard

# Auth
auth.login=Login
auth.register=Register
auth.email=Email
auth.password=Password
auth.confirm_password=Confirm Password
auth.forgot_password=Forgot Password?
auth.reset_password=Reset Password
auth.no_account=Don't have an account?
auth.has_account=Already have an account?

# Messages
messages.inbox=Messages
messages.trash=Trash
messages.compose=Compose
messages.search_placeholder=Search messages...
messages.no_messages=No messages
messages.confirm_delete=Delete
messages.mark_read=Mark as read
messages.mark_unread=Mark as unread
messages.move_to_trash=Move to trash
messages.restore=Restore
messages.delete_permanently=Delete permanently

# Contacts
contacts.title=Contacts
contacts.add=Add Contact
contacts.search_placeholder=Search contacts...
contacts.no_contacts=No contacts
contacts.name=Name
contacts.email=Email
contacts.phone=Phone

# Settings
settings.title=Settings
settings.change_password=Change Password
settings.save=Save
settings.cancel=Cancel

# Theme/Layout selectors
selector.theme=Theme
selector.language=Language
selector.layout=Layout

# Common
common.save=Save
common.cancel=Cancel
common.delete=Delete
common.edit=Edit
common.confirm=Confirm
common.loading=Loading...
common.error=Error
common.back=Back
common.search=Search

# Errors
error.not_found=Page not found
error.forbidden=Access denied
error.server_error=Server error
error.unauthorized=Please log in

# Footer
footer.online=Online
footer.offline=Offline
footer.synced=Synced
footer.syncing=Syncing...
```

(Exact keys will be determined during extraction.)

### 4. ShellView changes

```kotlin
data class ShellView(
    // ... existing fields ...
    val textResolver: TextResolver = DefaultTextResolver(),
) {
    fun text(key: String, vararg args: Any?): String = textResolver.resolve(key, *args)
}
```

Templates access strings as `${shell.text("nav.inbox")}`.

### 5. PlatformPlugin changes

```kotlin
interface PlatformPlugin : PluginMigrationSource {
    // ... existing ...

    /** Text resolver for all UI strings. Override to provide custom translations. */
    val textResolver: TextResolver
        get() = DefaultTextResolver()

    /**
     * JTE template paths that this plugin overrides (e.g. `setOf("layouts/SidebarLayout.kte")`).
     * The host resolves these templates from the plugin's classpath instead of its own.
     */
    fun templateOverrides(): Set<String> = emptySet()
}
```

### 6. Template Override Mechanism

The host wraps its `TemplateRenderer` in a `PluginTemplateRenderer`:

```kotlin
class PluginTemplateRenderer(
    private val delegate: TemplateRenderer,
    private val overrideTemplates: Set<String>,
    private val pluginClassLoader: ClassLoader?,
) : TemplateRenderer {
    override fun invoke(viewModel: ViewModel): String {
        // For precompiled JTE: if the template class is in overrideTemplates,
        // resolve from pluginClassLoader instead of host classloader.
        // Otherwise delegate normally.
    }
}
```

At startup, `App.kt` (or the DI module) checks if a plugin is registered and if it declares template overrides. If so, it wraps the default renderer.

Plugin JTE files live in the plugin's own `src/main/jte/` and are precompiled by the plugin's own JTE Maven plugin execution into the plugin JAR.

### 7. DI Wiring

In `WebModule.kt`:

```kotlin
single<TemplateRenderer> {
    val plugin = getOrNull<PlatformPlugin>()
    val baseRenderer = // ... existing JTE renderer setup ...
    
    if (plugin != null && plugin.templateOverrides().isNotEmpty()) {
        PluginTemplateRenderer(baseRenderer, plugin.templateOverrides(), plugin::class.java.classLoader)
    } else {
        baseRenderer
    }
}

single<TextResolver> {
    val plugin = getOrNull<PlatformPlugin>()
    plugin?.textResolver ?: DefaultTextResolver()
}
```

`ShellView` construction passes the `TextResolver` from Koin.

## What Changes

| Area | Change |
|---|---|
| `platform-core` | New `TextResolver` interface, `DefaultTextResolver` class |
| `platform-web/src/main/resources/texts.properties` | New file with all ~90 strings |
| `ShellView` | Add `textResolver` field + `text()` convenience method |
| `PlatformPlugin` | Add `textResolver` property + `templateOverrides()` method |
| `PluginContext` | Expose `textResolver` |
| All 33 `.kte` files | Replace hardcoded strings with `${shell.text("key")}` calls |
| `PluginTemplateRenderer` | New class wrapping the default renderer |
| `WebModule.kt` | Wire `TextResolver` and wrap renderer if plugin declares overrides |
| Existing tests | Update any assertions on hardcoded strings to match text key resolution |

## Migration

- Apps without a plugin: zero changes. `DefaultTextResolver` provides all English strings.
- Apps with a plugin that doesn't override `textResolver`: same behavior, default strings.
- Apps with a plugin that overrides `textResolver`: plugin controls all texts.
- Plugin can override individual strings by extending `DefaultTextResolver` and overriding specific keys.

## Testing

- Unit test `DefaultTextResolver`: loads from properties, returns key for missing entries, formats args
- Unit test `ShellView.text()`: delegates to resolver
- Integration test: register a plugin with custom `TextResolver`, verify rendered HTML contains custom strings
- Integration test: register a plugin with `templateOverrides()`, verify plugin template is used
- Update existing template tests to assert on `shell.text("key")` output
