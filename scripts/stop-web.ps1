$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$runtimeDirectory = Join-Path $projectRoot "target\dev-runtime"

function Get-ProcessTreeIds {
    param(
        [int]$RootId
    )

    $directChildren = Get-CimInstance Win32_Process -Filter "ParentProcessId = $RootId" -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty ProcessId
    $allChildren = @()

    foreach ($childId in $directChildren) {
        $allChildren += Get-ProcessTreeIds -RootId $childId
    }

    return @($allChildren + $RootId)
}

$pidFiles = @(
    @{ Name = "web server"; Path = (Join-Path $runtimeDirectory "server.pid") },
    @{ Name = "web application"; Path = (Join-Path $runtimeDirectory "app.pid") },
    @{ Name = "tailwind watcher"; Path = (Join-Path $runtimeDirectory "tailwind.pid") },
    @{ Name = "watcher"; Path = (Join-Path $runtimeDirectory "watcher.pid") }
)

foreach ($pidFile in $pidFiles) {
    if (-not (Test-Path $pidFile.Path)) {
        continue
    }

    $rawPid = Get-Content -Path $pidFile.Path -ErrorAction SilentlyContinue
    $processId = 0
    [void][int]::TryParse($rawPid, [ref]$processId)

    if ($processId -gt 0) {
        $processTreeIds = Get-ProcessTreeIds -RootId $processId | Select-Object -Unique
        foreach ($candidateId in ($processTreeIds | Sort-Object -Descending)) {
            $process = Get-Process -Id $candidateId -ErrorAction SilentlyContinue
            if ($process) {
                Write-Host "Stopping $($pidFile.Name) (PID $candidateId)..." -ForegroundColor Yellow
                Stop-Process -Id $candidateId -Force -ErrorAction SilentlyContinue
            }
        }
    }

    Remove-Item -Path $pidFile.Path -Force -ErrorAction SilentlyContinue
}

# Fallback: Kill by process name and command line if PID file is missing or failed
$javaProcesses = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" -ErrorAction SilentlyContinue
foreach ($p in $javaProcesses) {
    if ($p.CommandLine -like "*dev.outerstellar.platform.MainKt*" -or $p.CommandLine -like "*fizzed-watcher*") {
        Write-Host "Stopping existing web application instance (PID $($p.ProcessId))..." -ForegroundColor Yellow
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Web stack stopped." -ForegroundColor Green
