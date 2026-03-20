$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDirectory = Join-Path $projectRoot "target\dev-runtime"
$pidFile = Join-Path $runtimeDirectory "desktop.pid"

if (Test-Path $pidFile) {
    $rawPid = Get-Content -Path $pidFile -ErrorAction SilentlyContinue
    $processId = 0
    [void][int]::TryParse($rawPid, [ref]$processId)

    if ($processId -gt 0) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) {
            Write-Host "Stopping existing Swing application (PID $processId)..." -ForegroundColor Yellow
            Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item -Path $pidFile -Force -ErrorAction SilentlyContinue
}

# Fallback: Kill by process name and command line if PID file is missing or failed
$javaProcesses = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue
foreach ($p in $javaProcesses) {
    if ($p.CommandLine -like "*io.github.rygel.outerstellar.platform.swing.SwingSyncAppKt*") {
        Write-Host "Stopping existing Swing application instance (PID $($p.ProcessId))..." -ForegroundColor Yellow
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Desktop app stopped." -ForegroundColor Green
