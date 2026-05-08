# GraalVM Native Image Tracing Agent Guide

## Purpose

The tracing agent runs your JVM app normally and watches every reflection call,
resource load, dynamic proxy, and JNI access at runtime. On shutdown it writes
complete config files that `native-image` needs to compile your app ahead of
time.

Without the agent you have to hand-write `reflect-config.json`,
`resource-config.json`, etc. The agent generates them automatically from actual
runtime behavior.

## How It Works

```
1. Start app with -agentlib:native-image-agent=...
2. Agent instruments JVM — intercepts Class.forName(), Method.invoke(),
   getResourceAsStream(), Proxy.newProxyInstance(), etc.
3. Exercise endpoints to trigger all code paths
4. Each code path causes reflection/resource calls → agent records them
5. Graceful shutdown (Ctrl+C / SIGINT) → agent writes config JSON files
6. Merge agent configs into src/main/resources/META-INF/native-image/...
```

Key detail: the agent only sees code paths you actually exercise. If you miss
an endpoint, its reflection calls won't be recorded and native-image will fail
at runtime. Hit everything important.

## Prerequisites

- GraalVM JDK installed (this project uses GraalVM CE 25.0.2)
- `native-image` on PATH (verify: `native-image --version`)
- Dev PostgreSQL running on port 5434
- Fat JAR built: `mvn package -pl platform-web -am -DskipTests`

## Running the Agent

### One-command script (PowerShell)

```powershell
# Configuration
$agentDir = "C:\Users\ALEXAN~1\AppData\Local\Temp\opencode\native-image-agent"
$jarPath = "platform-web/target/outerstellar-platform-web-1.5.1-SNAPSHOT-jar-with-dependencies.jar"
$appPort = "9091"
$timeoutSeconds = 120  # Hard limit — kill after 2 minutes

# Clean previous run
Remove-Item "$agentDir\*" -Force -ErrorAction SilentlyContinue

# Set app environment
$env:JDBC_URL = "jdbc:postgresql://localhost:5434/outerstellar"
$env:JDBC_USER = "outerstellar"
$env:JDBC_PASSWORD = "outerstellar"
$env:DEVMODE = "true"
$env:SESSIONCOOKIESECURE = "false"
$env:ADMIN_PASSWORD = "test123"
$env:PORT = $appPort

# Start app with agent + periodic config writes (every 15s)
$proc = Start-Process -FilePath "java" `
    -ArgumentList @(
        "-agentlib:native-image-agent=config-output-dir=$agentDir,config-write-period-secs=15",
        "-jar", $jarPath
    ) `
    -PassThru `
    -RedirectStandardOutput "$agentDir\app-stdout.log" `
    -RedirectStandardError "$agentDir\app-stderr.log" `
    -NoNewWindow

Write-Output "App PID: $($proc.Id) — timeout: ${timeoutSeconds}s"
```

### config-write-period-secs flag

This flag makes the agent write configs every N seconds instead of only on
shutdown. This is the safety net — even if the process is killed, you get
partial configs. Without it, configs are only written on graceful shutdown.

## Exercising Endpoints

After the app starts (~10-15s), hit all important endpoints:

```powershell
$base = "http://localhost:$appPort"

# Health check
curl -sf "$base/health"

# Login page (public)
curl -sf "$base/"

# Static assets
curl -sf "$base/static/css/main.css" | Out-Null

# API — will return 401 but exercises the auth filter chain
curl -sf "$base/api/messages" | Out-Null

# OpenAPI spec
curl -sf "$base/openapi" | Out-Null

# Metrics
curl -sf "$base/metrics" | Out-Null
```

Each request triggers different code paths: routing, serialization, template
rendering, security filters, metrics, etc. The agent records all of it.

## Stopping the App Gracefully

```powershell
# Send SIGINT (Ctrl+C equivalent) — allows agent to flush final configs
$proc.Kill()  # This sends SIGTERM on Windows
Start-Sleep -Seconds 3

# Verify configs were written
Get-ChildItem "$agentDir\*.json" | Select-Object Name, Length
```

Expected output files:
- `reflect-config.json` — classes accessed via reflection
- `resource-config.json` — resources loaded at runtime
- `proxy-config.json` — dynamic proxy interfaces (usually empty for this project)
- `jni-config.json` — JNI calls (usually empty for this project)

## Hard Timeout Safety

Never let the agent run indefinitely. Always set a deadline:

```powershell
$deadline = (Get-Date).AddSeconds($timeoutSeconds)

while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
}

if (-not $proc.HasExited) {
    Write-Output "TIMEOUT: Killing process after ${timeoutSeconds}s"
    Stop-Process -Id $proc.Id -Force
}
```

Recommended timeout: **120 seconds** (2 minutes). The app starts in ~10s,
exercising endpoints takes ~30s, leaving 80s of buffer.

## Merging Agent Configs

Agent-generated configs are a superset of hand-written ones. Merge carefully:

```powershell
# Compare agent output with hand-written configs
code --diff platform-web/src/main/resources/META-INF/native-image/io.github.rygel/outerstellar-platform-web/reflect-config.json `
           $agentDir/reflect-config.json
```

Strategy:
1. Agent configs are authoritative — they reflect actual runtime behavior
2. Keep hand-written entries that the agent missed (rare, but possible for
   code paths not exercised during the run)
3. Remove hand-written entries that duplicate agent entries
4. The merged result is your final config

## Common Issues

| Problem | Cause | Fix |
|---------|-------|-----|
| "Output directory is locked" | Previous run didn't shut down cleanly | Delete `.lock` file in agent dir |
| No JSON files after shutdown | Process was killed with `-Force` | Use `config-write-period-secs=15` flag |
| "Address already in use" | Port occupied by another process | Change `$env:PORT` to a free port |
| App fails to connect to DB | Wrong JDBC_URL or DB not running | Check port 5434, verify credentials |
| Missing reflection errors at native-image time | Code path not exercised during agent run | Re-run agent and exercise that endpoint |

## Timeline

| Phase | Duration |
|-------|----------|
| App startup | 10-15s |
| Exercise endpoints | 30-60s |
| Graceful shutdown + config write | 3-5s |
| **Total** | **~1-2 minutes** |

If the agent runs longer than 5 minutes, something is wrong — kill it and
investigate.
