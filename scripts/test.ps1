<#
.SYNOPSIS
Runs the full non-desktop reactor build with a 20-minute wall-clock timeout.

.DESCRIPTION
Wraps `mvn clean verify` with a hard timeout. If the build exceeds the
configured duration (default 20 minutes), the Maven process is killed and
an error is reported. This prevents hung builds from running indefinitely.

The timeout is a safety net — the full reactor normally completes in ~5 minutes.
If it hits 20 minutes, something is wrong (hung container, deadlock, etc.).

.EXAMPLE
# Run full build with default 20-minute timeout
pwsh scripts/test.ps1

# Run with 10-minute timeout for quick iteration
pwsh scripts/test.ps1 -TimeoutMinutes 10

# Run only platform-web tests
pwsh scripts/test.ps1 -Modules platform-web

# Skip quality checks for fast iteration
pwsh scripts/test.ps1 -SkipQuality

# Pass extra Maven args
pwsh scripts/test.ps1 -ExtraArgs "-DskipTests"
#>

param(
    [int]$TimeoutMinutes = 20,
    [string]$Modules = "outerstellar-i18n,platform-core,platform-security,platform-test-infrastructure,platform-plugin-api,platform-persistence-jdbi,platform-sync-client,platform-jte-extensions,platform-web,platform-seed",
    [switch]$SkipQuality,
    [string]$ExtraArgs = ""
)

$ErrorActionPreference = "Stop"

$mavenArgs = @(
    "clean", "verify",
    "-T4",
    "-pl", $Modules
)

if ($SkipQuality) {
    $mavenArgs += @(
        "-Ddetekt.skip=true",
        "-Dspotbugs.skip=true",
        "-Dspotless.check.skip=true",
        "-Dcheckstyle.skip=true",
        "-Dpmd.skip=true"
    )
}

if ($ExtraArgs) {
    $mavenArgs += $ExtraArgs -split '\s+'
}

$timeoutMs = $TimeoutMinutes * 60 * 1000
$startTime = Get-Date

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Test Runner (timeout: ${TimeoutMinutes}min)" -ForegroundColor Cyan
Write-Host " Modules: $Modules" -ForegroundColor Cyan
Write-Host " Started: $(Get-Date -Format 'HH:mm:ss')" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$proc = Start-Process -FilePath "mvn" -ArgumentList $mavenArgs -NoNewWindow -PassThru

$exited = $proc.WaitForExit($timeoutMs)

if (-not $exited) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host " BUILD TIMED OUT after ${TimeoutMinutes} minutes!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host "Something is wrong. The full reactor should complete in ~5 minutes." -ForegroundColor Yellow
    Write-Host "Likely causes:" -ForegroundColor Yellow
    Write-Host "  - Hung Testcontainers (Podman machine stopped?)" -ForegroundColor Yellow
    Write-Host "  - Deadlocked test" -ForegroundColor Yellow
    Write-Host "  - Infinite loop in test code" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Killing Maven process (PID: $($proc.Id))..." -ForegroundColor Red

    try {
        $proc.Kill()
        $childProcesses = Get-CimInstance Win32_Process | Where-Object {
            $_.ParentProcessId -eq $proc.Id
        }
        foreach ($child in $childProcesses) {
            Stop-Process -Id $child.ProcessId -Force -ErrorAction SilentlyContinue
        }
    } catch {
        Write-Host "Warning: Could not kill all child processes: $_" -ForegroundColor Yellow
    }

    exit 1
}

$elapsed = (Get-Date) - $startTime
$elapsedStr = "{0:hh\:mm\:ss}" -f $elapsed

Write-Host ""
Write-Host "========================================" -ForegroundColor $(if ($proc.ExitCode -eq 0) { "Green" } else { "Red" })
Write-Host " Build completed in $elapsedStr" -ForegroundColor $(if ($proc.ExitCode -eq 0) { "Green" } else { "Red" })
Write-Host " Exit code: $($proc.ExitCode)" -ForegroundColor $(if ($proc.ExitCode -eq 0) { "Green" } else { "Red" })
Write-Host "========================================" -ForegroundColor $(if ($proc.ExitCode -eq 0) { "Green" } else { "Red" })

if ($elapsed.TotalMinutes -gt ($TimeoutMinutes * 0.75)) {
    Write-Host ""
    Write-Host "WARNING: Build took $([math]::Round($elapsed.TotalMinutes, 1)) minutes (>75% of ${TimeoutMinutes}min limit)" -ForegroundColor Yellow
    Write-Host "This is slower than expected (~5 min). Consider investigating." -ForegroundColor Yellow
}

exit $proc.ExitCode
