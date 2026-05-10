# AOT Compilation Guide — GraalVM Native Image

This guide covers building and running the Outerstellar Platform web server as a GraalVM native image. A native binary starts in milliseconds, uses less memory, and has no JVM warmup penalty.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [How It Works](#how-it-works)
4. [Building Locally (Windows)](#building-locally-windows)
5. [Building with Docker (Linux)](#building-with-docker-linux)
6. [Running the Native Binary](#running-the-native-binary)
7. [Configuration Reference](#configuration-reference)
8. [Reachability Metadata](#reachability-metadata)
9. [JTE Template Workaround](#jte-template-workaround)
10. [UPX Compression](#upx-compression)
11. [Updating Metadata with the Tracing Agent](#updating-metadata-with-the-tracing-agent)
12. [Troubleshooting](#troubleshooting)

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| GraalVM JDK | 25+ | Must include `native-image` (`gu install native-image` on older versions) |
| Maven | 3.9+ | Standard project requirement |
| PostgreSQL | 18 | Running and accessible at build time is **not** required — only at runtime |
| C++ toolchain | OS-specific | MSVC on Windows, GCC on Linux. GraalVM prints an error if missing. |

Verify GraalVM is active:

```powershell
java -version
# openjdk version "25" ... GraalVM CE

native-image --version
# native-image 25.0.2 ... GraalVM
```

---

## Quick Start

```powershell
# 1. Set GraalVM as JAVA_HOME
$env:JAVA_HOME = 'C:\path\to\graalvm-ce-25'

# 2. Build fat JAR + native image in one step
mvn -pl platform-web -Pnative package -DskipTests -am

# 3. Run the binary
$env:JDBC_URL = 'jdbc:postgresql://localhost:5434/outerstellar'
$env:JDBC_USER = 'outerstellar'
$env:JDBC_PASSWORD = 'outerstellar'
$env:JTE_PRODUCTION = 'true'
$env:SESSIONCOOKIESECURE = 'false'
./platform-web/target/outerstellar-web.exe
```

The server starts on port 8080. Visit `http://localhost:8080/health` to verify.

---

## How It Works

The native image build has three phases:

```
┌─────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  mvn package     │     │  JTE precompile   │     │  native-image    │
│  (fat JAR)       │────▶│  (Kotlin sources) │────▶│  (AOT compile)   │
│  shade plugin    │     │  jte-maven-plugin │     │  GraalVM build   │
└─────────────────┘     └──────────────────┘     └──────────────────┘
```

1. **Fat JAR** — `maven-shade-plugin` bundles all dependencies into a single `-jar-with-dependencies.jar`.
2. **JTE precompile** — `jte-maven-plugin:precompile` converts `.kte` templates into Kotlin source files. These compile into classes that are registered in `JteClassRegistry`.
3. **Native image** — `native-maven-plugin` invokes GraalVM's `native-image` compiler. It reads the reachability metadata and produces a platform-specific executable.

### Key Build Args

| Arg | Purpose |
|---|---|
| `--no-fallback` | Fail if native image cannot be built (no JVM fallback) |
| `--enable-http` / `--enable-https` | Include Netty HTTP/HTTPS support |
| `-DJTE_PRODUCTION=true` | Activate the registry-based JTE renderer |
| `--initialize-at-build-time=org.slf4j,ch.qos.logback` | Eagerly init logging at build time |
| `--initialize-at-run-time=io.netty.channel.DefaultFileRegion` | Defer Netty file region init to runtime |

---

## Building Locally (Windows)

### Step 1: Prepare the environment

```powershell
# Use GraalVM JDK
$env:JAVA_HOME = 'C:\path\to\graalvm-ce-25'

# Verify
native-image --version
```

### Step 2: Build

The `-Pnative` profile handles everything — fat JAR, JTE precompilation, and native image compilation:

```powershell
mvn -pl platform-web -Pnative package -DskipTests -am
```

Build time is approximately 2 minutes on a modern machine. The binary appears at:

```
platform-web/target/outerstellar-web.exe    (~211 MB)
```

### Step 3: Verify

```powershell
# Quick sanity check — binary starts and responds
$env:JDBC_URL = 'jdbc:postgresql://localhost:5434/outerstellar'
$env:JDBC_USER = 'outerstellar'
$env:JDBC_PASSWORD = 'outerstellar'
$env:JTE_PRODUCTION = 'true'
$env:PORT = '9091'

./platform-web/target/outerstellar-web.exe
# Wait for "Server started on port 9091" output

# In another terminal:
curl http://localhost:9091/health
# {"status":"UP","database":{"status":"UP"}}
```

---

## Building with Docker (Linux)

For production Linux binaries, use the multi-stage `Dockerfile.native`:

```bash
# Build the native image inside a container
docker build -f docker/Dockerfile.native -t outerstellar-platform:native .

# Run it
docker run --rm \
  -p 8080:8080 \
  -e JDBC_URL=jdbc:postgresql://host.docker.internal:5432/outerstellar \
  -e JDBC_USER=outerstellar \
  -e JDBC_PASSWORD=outerstellar \
  -e SESSIONCOOKIESECURE=false \
  outerstellar-platform:native
```

### Dockerfile stages

| Stage | Base image | Purpose |
|---|---|---|
| `builder` | `maven:3.9-eclipse-temurin-21` | Compiles fat JAR with Maven + Node.js (for Tailwind) |
| `native-builder` | `ghcr.io/graalvm/native-image-community:25.0.2` | Runs `native-image` on the fat JAR |
| Final | `debian:bookworm-slim` | Minimal runtime image with `libstdc++6` only |

The final image is ~220 MB. The binary runs as a non-root user (`appuser`).

---

## Running the Native Binary

### Required Environment Variables

| Variable | Example | Required | Notes |
|---|---|---|---|
| `JDBC_URL` | `jdbc:postgresql://db:5432/outerstellar` | Yes | PostgreSQL connection string |
| `JDBC_USER` | `outerstellar` | Yes | Database user |
| `JDBC_PASSWORD` | `outerstellar` | Yes | Database password |
| `JTE_PRODUCTION` | `true` | Yes | Must be `true` for native image |

### Optional Environment Variables

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8080` | HTTP listen port |
| `APP_PROFILE` | `default` | Config profile (`dev`, `prod`, `postgres`) |
| `SESSIONCOOKIESECURE` | `false` | Set `true` behind HTTPS |
| `DEVMODE` | `false` | Enables dev auto-login (never in production) |
| `ADMIN_PASSWORD` | (random) | Initial admin password |
| `CSRFENABLED` | `true` | CSRF double-submit cookie |

### Runtime Behavior

- **Startup time**: ~50-200ms (vs 10-15s on JVM)
- **Memory**: ~30-50 MB RSS (vs 200-400 MB JVM)
- **Flyway migrations**: Run automatically on startup, same as JVM mode
- **Template rendering**: Uses `JteClassRegistry` (see [JTE Template Workaround](#jte-template-workaround))

### Systemd Unit Example (Linux)

```ini
[Unit]
Description=Outerstellar Platform
After=network.target postgresql.service

[Service]
Type=simple
User=outerstellar
WorkingDirectory=/opt/outerstellar
ExecStart=/opt/outerstellar/outerstellar-web
Environment=JDBC_URL=jdbc:postgresql://localhost:5432/outerstellar
Environment=JDBC_USER=outerstellar
Environment=JDBC_PASSWORD=changeme
Environment=JTE_PRODUCTION=true
Environment=SESSIONCOOKIESECURE=true
Environment=APP_PROFILE=prod
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

### Reverse Proxy (Recommended)

The application does not implement gzip compression internally. Compression, TLS termination, and HSTS headers should be handled by a reverse proxy. This keeps the native binary simple and leverages battle-tested infrastructure.

**Caddy example:**

```
outerstellar.example.com {
    encode gzip
    reverse_proxy localhost:8080
}
```

**nginx example:**

```nginx
server {
    listen 443 ssl http2;
    server_name outerstellar.example.com;

    add_header Strict-Transport-Security "max-age=31536000" always;

    location / {
        proxy_pass http://127.0.0.1:8080;
        gzip on;
        gzip_types text/html text/css application/javascript application/json;
    }
}
```

Set `SESSIONCOOKIESECURE=true` and `APP_PROFILE=prod` when running behind a reverse proxy that terminates TLS.

---

## Configuration Reference

### Maven Profiles

| Profile | Purpose | Command |
|---|---|---|
| `native` | Build native image | `mvn -pl platform-web -Pnative package -DskipTests -am` |
| `native-upx` | Compress binary with UPX | `mvn -pl platform-web -Pnative,native-upx package -DskipTests -am` |

### POM Configuration

The native image configuration lives in `platform-web/pom.xml` under the `native` profile:

```xml
<profile>
    <id>native</id>
    <build>
        <plugins>
            <!-- JTE precompile step -->
            <plugin>
                <groupId>gg.jte</groupId>
                <artifactId>jte-maven-plugin</artifactId>
                <!-- Precompiles .kte templates to Kotlin source files -->
            </plugin>

            <!-- GraalVM native image -->
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>${native.maven.plugin.version}</version>
                <configuration>
                    <imageName>outerstellar-web</imageName>
                    <mainClass>io.github.rygel.outerstellar.platform.MainKt</mainClass>
                    <classpath>
                        <param>${project.build.directory}/${project.artifactId}-${project.version}-jar-with-dependencies.jar</param>
                    </classpath>
                    <buildArgs>
                        <buildArg>--no-fallback</buildArg>
                        <buildArg>--enable-http</buildArg>
                        <buildArg>--enable-https</buildArg>
                        <buildArg>-DJTE_PRODUCTION=true</buildArg>
                        <buildArg>--initialize-at-build-time=org.slf4j,ch.qos.logback</buildArg>
                        <buildArg>--initialize-at-run-time=io.netty.channel.DefaultFileRegion</buildArg>
                        <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

---

## Reachability Metadata

GraalVM's native image compiler performs closed-world analysis — it only includes classes that are statically reachable. Any code accessed via reflection, resource loading, or dynamic proxies must be explicitly registered.

The metadata file is:

```
platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json
```

### What's Registered

| Category | Count | Examples |
|---|---|---|
| Logback classes | ~10 | `PatternLayoutEncoder`, `ConsoleAppender`, `DefaultJoranConfigurator` |
| JTE generated templates | 33 | `JteHomePageGenerated`, `JteAuthPageGenerated`, etc. |
| Kotlin serialization | ~20 | Companion objects, serializers |
| hoplite config parsers | ~5 | YAML, property file parsers |
| Resource bundles | 4 | `messages.properties`, `messages_fr.properties`, `themes.json` |

### When to Update

Update the metadata when:

- Adding new JTE templates (add the generated class to both `JteClassRegistry` and `reachability-metadata.json`)
- Adding new dependencies that use reflection (run the tracing agent to detect them)
- Changing logging configuration (Logback classes may differ)
- Adding new i18n resource bundles

### Format

The file uses GraalVM's reachability metadata format:

```json
{
  "reflection": [
    {
      "type": "com.example.SomeClass",
      "methods": [
        { "name": "<init>", "parameterTypes": [] }
      ]
    }
  ],
  "resources": {
    "includes": [
      { "pattern": "themes\\.json$" },
      { "pattern": "messages.*\\.properties$" }
    ]
  }
}
```

---

## JTE Template Workaround

### The Problem

JTE 3.2.4's `TemplateEngine.createPrecompiled(...)` path uses `ClassLoader.loadClass(String)` to resolve generated template classes by name. This works on a normal JVM where the classloader can dynamically discover classes. In a GraalVM native image, the classpath is closed — string-based class loading fails even when the class is present in the binary.

### The Solution

`JteClassRegistry` (`platform-web/src/main/kotlin/.../infra/JteClassRegistry.kt`) maintains direct compile-time references to all 33 generated JTE template classes. In production mode, `JteInfra` bypasses `TemplateEngine` entirely and renders templates via JTE's low-level `Template(name, class)` API.

```
JVM mode:    TemplateEngine.createPrecompiled() → ClassLoader.loadClass() → render
Native mode: JteClassRegistry.getTemplateClass() → Template(name, class) → render
```

### Adding a New Template

When you add a new `.kte` template file, you must update **three places**:

1. **Create the template file** — e.g., `platform-web/src/main/jte/pages/myPage.kte`

2. **Add to `JteClassRegistry`** — After running `jte-maven-plugin:generate`, import the generated class and add it to the appropriate list:

   ```kotlin
   // In JteClassRegistry.kt
   import gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteMyPageGenerated

   private val pageClasses = listOf(
       // ... existing entries ...
       JteMyPageGenerated::class.java,
   )
   ```

3. **Add to `reachability-metadata.json`** — Register the generated class for reflection:

   ```json
   {
     "type": "gg.jte.generated.precompiled.outerstellar.io.github.rygel.outerstellar.platform.web.JteMyPageGenerated",
     "allDeclaredFields": true,
     "allDeclaredMethods": true
   }
   ```

The diagnostic log at startup confirms registration:

```
INFO  JteInfra - Production mode: JteClassRegistry has 34 template classes
INFO  JteInfra - Preloaded 34 template classes, 0 not found
```

### Future Removal

This workaround is needed because JTE 3.2.4 lacks a `NativeResourcesExtension`. When JTE releases a version with native-image support (expected in 3.2.5+), the registry can be replaced with `TemplateEngine.createPrecompiled(...)` and the standard JTE production path.

---

## UPX Compression

The native binary is ~211 MB. UPX can compress it to ~50-80 MB:

```powershell
# Requires 'upx' on PATH
mvn -pl platform-web -Pnative,native-upx package -DskipTests -am
```

**Caveats:**
- Some antivirus engines flag UPX-compressed binaries as malware (false positive)
- Decompression adds ~100ms to first startup
- Not recommended for Docker images where layers handle size efficiently

---

## Updating Metadata with the Tracing Agent

When you add dependencies or features that use reflection, the reachability metadata must be updated. The GraalVM tracing agent automates this by monitoring runtime behavior.

### Quick Procedure

```powershell
# 1. Build fat JAR
mvn -pl platform-web -am -DskipTests package

# 2. Set up agent output directory
$agentDir = "$env:TEMP\native-image-agent"
Remove-Item "$agentDir\*" -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -agentDir | Out-Null

# 3. Start app with tracing agent
$env:JDBC_URL = 'jdbc:postgresql://localhost:5434/outerstellar'
$env:JDBC_USER = 'outerstellar'
$env:JDBC_PASSWORD = 'outerstellar'
$env:JTE_PRODUCTION = 'true'
$env:PORT = '9091'

java -agentlib:native-image-agent=config-output-dir=$agentDir,config-write-period-secs=15 `
     -jar platform-web/target/outerstellar-platform-web-*-jar-with-dependencies.jar

# 4. Exercise ALL endpoints (in another terminal)
curl -sf http://localhost:9091/health
curl -sf http://localhost:9091/
curl -sf http://localhost:9091/static/css/main.css
curl -sf http://localhost:9091/api/messages
curl -sf http://localhost:9091/metrics

# 5. Stop the app (Ctrl+C or close the window)

# 6. Check agent output
Get-ChildItem "$agentDir\*.json" | Select-Object Name, Length
```

### Merging Agent Output

Agent-generated configs are authoritative — they reflect actual runtime behavior:

1. Open `platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reachability-metadata.json`
2. Compare with `$agentDir/reflect-config.json`
3. Add any new entries from the agent output
4. Keep hand-written entries that the agent missed (edge cases not exercised during the run)

For detailed instructions, see `docs/superpowers/guides/graalvm-tracing-agent.md`.

---

## Troubleshooting

### Build Failures

| Error | Cause | Fix |
|---|---|---|
| `native-image: command not found` | GraalVM not on PATH | Set `$env:JAVA_HOME` and add `$JAVA_HOME/bin` to PATH |
| `Error: A C++ compiler is required` | Missing build tools | Install MSVC (Windows) or `build-essential` (Linux) |
| `UnsupportedClassVersionError` | Wrong JDK version | Use JDK 21 or GraalVM 25+ |
| `ClassNotFoundException: gg.jte.generated...` | JTE templates not precompiled | Run with `-Pnative` profile which includes `jte-precompile` |
| `Build timed out` | Insufficient memory | Ensure 8+ GB RAM available; close other applications |

### Runtime Failures

| Error | Cause | Fix |
|---|---|---|
| `TemplateNotFoundException` | New template not in `JteClassRegistry` | Add generated class to registry + metadata (see [JTE Template Workaround](#jte-template-workaround)) |
| `NoClassDefFoundError` at runtime | Class not in reachability metadata | Re-run tracing agent and merge configs |
| `Flyway: Unable to scan classpath` | Flyway scanner can't enumerate classes | This is already handled — migrations use `classpath:db/migration` which works in native image |
| `logback.xml not found` | Logback config not in resource metadata | Add `logback.xml` pattern to `reachability-metadata.json` resources section |
| Binary exits immediately with no output | Missing `JTE_PRODUCTION=true` | Set `JTE_PRODUCTION=true` env var or `-Djte.production=true` |
| Port already in use | Another process on 8080 | Set `PORT` env var to a different port |

### Diagnostic Flags

Add these to the `native-maven-plugin` `<buildArgs>` for verbose output:

```
-H:+ReportExceptionStackTraces    # Full stack traces in native image errors
-H:Log=registerResources:3        # Log all resource registrations
-H:PrintAnalysisCallTree=type     # Print call tree for a specific type
```

### Checking What's Included

```powershell
# List all classes in the native image (requires GraalVM inspection)
native-image --allow-incomplete-classpath -H:+PrintClassPath <args>

# Check if a specific class is reachable
native-image -H:PrintAnalysisCallTree=com.example.MyClass <args>
```
