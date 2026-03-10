$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDirectory = Join-Path $projectRoot "target\dev-runtime"
$watcherPidFile = Join-Path $runtimeDirectory "watcher.pid"
$appPidFile = Join-Path $runtimeDirectory "app.pid"
$serverPidFile = Join-Path $runtimeDirectory "server.pid"
$watcherLog = Join-Path $runtimeDirectory "watcher.log"
$watcherErrorLog = Join-Path $runtimeDirectory "watcher.err.log"
$appLog = Join-Path $runtimeDirectory "app.log"
$appErrorLog = Join-Path $runtimeDirectory "app.err.log"

$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $mavenCommand) {
    $mavenCommand = Get-Command mvn -ErrorAction Stop
}

& (Join-Path $projectRoot "stop-web.ps1")

New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
Remove-Item $watcherLog, $watcherErrorLog, $appLog, $appErrorLog -Force -ErrorAction SilentlyContinue

$env:DEV_MODE = "true"
$port = if ($env:PORT) { [int]$env:PORT } else { 8080 }

Write-Host "Starting watcher..." -ForegroundColor Cyan
$watcherProcess = Start-Process `
    -FilePath $mavenCommand.Source `
    -ArgumentList "-pl", "web", "fizzed-watcher:run" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $watcherLog `
    -RedirectStandardError $watcherErrorLog `
    -PassThru

Write-Host "Starting web application..." -ForegroundColor Green
$appProcess = Start-Process `
    -FilePath $mavenCommand.Source `
    -ArgumentList "-pl", "web", "compile", "exec:java" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $appLog `
    -RedirectStandardError $appErrorLog `
    -PassThru

Set-Content -Path $watcherPidFile -Value $watcherProcess.Id
Set-Content -Path $appPidFile -Value $appProcess.Id

$healthUrl = "http://localhost:$port/health"
$started = $false
$serverProcessId = 0

for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 1

    if ($appProcess.HasExited) {
        & (Join-Path $projectRoot "stop-web.ps1")
        throw "Web application exited early. Check $appLog and $appErrorLog."
    }

    try {
        $response = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue |
                Select-Object -First 1
            if ($listener) {
                $serverProcessId = $listener.OwningProcess
                Set-Content -Path $serverPidFile -Value $serverProcessId
            }
            $started = $true
            break
        }
    }
    catch {
        Start-Sleep -Milliseconds 250
    }
}

if (-not $started) {
    & (Join-Path $projectRoot "stop-web.ps1")
    throw "Web application did not become healthy at $healthUrl."
}

Write-Host "Web stack started." -ForegroundColor Green
Write-Host "  App PID: $($appProcess.Id)"
Write-Host "  Watcher PID: $($watcherProcess.Id)"
if ($serverProcessId -gt 0) {
    Write-Host "  Server PID: $serverProcessId"
}
Write-Host "  Health URL: $healthUrl"
