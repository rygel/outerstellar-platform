$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $projectRoot

# Stop existing instance
& (Join-Path $projectRoot "stop-swing.ps1")

# Build both core and desktop in one reactor so resource changes (e.g. i18n keys)
# are always picked up before launching Swing.
mvn -f pom.xml -Pruntime-dev -pl platform-core,platform-desktop -am compile

$runtimeDirectory = Join-Path $projectRoot "target\dev-runtime"
if (-not (Test-Path $runtimeDirectory)) {
    New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
}
$pidFile = Join-Path $runtimeDirectory "desktop.pid"

$env:DEV_MODE = "true"
Write-Host "Launching Swing application..." -ForegroundColor Green
# We run this in a way that we can capture the PID if possible, 
# but for now let's just run it. The stop script has a fallback to kill by main class.
# To properly record PID we would need to background it, but Swing is usually run in foreground dev.
# If we run in foreground, we can't easily write the PID file *of the java process* from this script 
# because mvn exec:java will block.
# However, the stop-swing.ps1 fallback handles this by looking for the main class.

mvn -f pom.xml -Pruntime-dev -pl platform-desktop exec:java
