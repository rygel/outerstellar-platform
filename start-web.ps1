$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDirectory = Join-Path $projectRoot "target\dev-runtime"
$watcherPidFile = Join-Path $runtimeDirectory "watcher.pid"
$tailwindPidFile = Join-Path $runtimeDirectory "tailwind.pid"
$appPidFile = Join-Path $runtimeDirectory "app.pid"
$serverPidFile = Join-Path $runtimeDirectory "server.pid"
$watcherLog = Join-Path $runtimeDirectory "watcher.log"
$watcherErrorLog = Join-Path $runtimeDirectory "watcher.err.log"
$tailwindLog = Join-Path $runtimeDirectory "tailwind.log"
$tailwindErrorLog = Join-Path $runtimeDirectory "tailwind.err.log"
$appLog = Join-Path $runtimeDirectory "app.log"
$appErrorLog = Join-Path $runtimeDirectory "app.err.log"

$mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if (-not $mavenCommand) {
    $mavenCommand = Get-Command mvn -ErrorAction Stop
}

$npmCommand = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npmCommand) {
    $npmCommand = Get-Command npm -ErrorAction Stop
}

& (Join-Path $projectRoot "stop-web.ps1")

New-Item -ItemType Directory -Path $runtimeDirectory -Force | Out-Null
Remove-Item $watcherLog, $watcherErrorLog, $appLog, $appErrorLog, $tailwindLog, $tailwindErrorLog -Force -ErrorAction SilentlyContinue

$env:DEV_MODE = "true"
$port = if ($env:PORT) { [int]$env:PORT } else { 8080 }

Write-Host "Starting watcher..." -ForegroundColor Cyan
$watcherProcess = Start-Process `
    -FilePath $mavenCommand.Source `
    -ArgumentList "-Pruntime-dev", "-pl", "web", "fizzed-watcher:run" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $watcherLog `
    -RedirectStandardError $watcherErrorLog `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Starting Tailwind watcher..." -ForegroundColor Cyan
$tailwindProcess = Start-Process `
    -FilePath $npmCommand.Source `
    -ArgumentList "run", "watch:css" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $tailwindLog `
    -RedirectStandardError $tailwindErrorLog `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Starting web application..." -ForegroundColor Green
$appProcess = Start-Process `
    -FilePath $mavenCommand.Source `
    -ArgumentList "-Pruntime-dev", "-pl", "web", "compile", "exec:java" `
    -WorkingDirectory $projectRoot `
    -RedirectStandardOutput $appLog `
    -RedirectStandardError $appErrorLog `
    -WindowStyle Hidden `
    -PassThru

Set-Content -Path $watcherPidFile -Value $watcherProcess.Id
Set-Content -Path $tailwindPidFile -Value $tailwindProcess.Id
Set-Content -Path $appPidFile -Value $appProcess.Id

$healthUrl = "http://localhost:$port/health"
$started = $false
$serverProcessId = 0

Write-Host "Waiting for application to become healthy at $healthUrl..." -ForegroundColor Cyan
Write-Host "Monitoring $appLog..." -ForegroundColor Gray

$lastLineCount = 0
for ($attempt = 0; $attempt -lt 40; $attempt++) {
    Start-Sleep -Seconds 1
    
    if (Test-Path $appLog) {
        $currentLines = Get-Content $appLog
        $newLineCount = $currentLines.Count
        if ($newLineCount -gt $lastLineCount) {
            $currentLines | Select-Object -Skip $lastLineCount | ForEach-Object {
                Write-Host "  > $_" -ForegroundColor DarkGray
            }
            $lastLineCount = $newLineCount
        }
    }

    if ($appProcess.HasExited) {
        Write-Host "`nERROR: Web application process has exited!" -ForegroundColor Red
        if (Test-Path $appErrorLog) {
            Write-Host "--- Last 20 lines of $appErrorLog ---" -ForegroundColor Yellow
            Get-Content $appErrorLog -Tail 20
        }
        & (Join-Path $projectRoot "stop-web.ps1")
        throw "Web application exited early. Check logs in $runtimeDirectory"
    }

    try {
        $response = Invoke-WebRequest -Uri $healthUrl -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($response -and $response.StatusCode -eq 200) {
            Write-Host "`n[HEALTHY] Application is responding on $healthUrl" -ForegroundColor Green
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
        # Check if something is at least listening on the port
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($conn) {
            Write-Host "  (Port $port is listening, waiting for /health to respond...)" -ForegroundColor Gray
        }
    }
}

if (-not $started) {
    Write-Host "`nERROR: Web application did not become healthy in time." -ForegroundColor Red
    if (Test-Path $appErrorLog) {
        Write-Host "--- Last 20 lines of $appErrorLog ---" -ForegroundColor Yellow
        Get-Content $appErrorLog -Tail 20
    }
    & (Join-Path $projectRoot "stop-web.ps1")
    throw "Web application did not become healthy at $healthUrl."
}

Write-Host "`n[SUCCESS] Web stack is up and running." -ForegroundColor Green
Write-Host "-------------------------------------------" -ForegroundColor Gray
Write-Host "  App PID:      $($appProcess.Id)"
Write-Host "  Watcher PID:  $($watcherProcess.Id)"
Write-Host "  Tailwind PID: $($tailwindProcess.Id)"
if ($serverProcessId -gt 0) {
    Write-Host "  Server PID:   $serverProcessId"
}
Write-Host "  Health URL:   $healthUrl"
Write-Host "-------------------------------------------" -ForegroundColor Gray
