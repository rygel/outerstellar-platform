param()

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

if (-not (Get-Command podman -ErrorAction SilentlyContinue)) {
    throw "podman is required for desktop tests. Install/start Podman, then retry."
}

if (-not (Get-Command bash -ErrorAction SilentlyContinue)) {
    throw "bash is required to run docker/run-desktop-tests.sh. Install Git Bash or another Bash-compatible shell."
}

Push-Location $repoRoot
try {
    & bash "docker/run-desktop-tests.sh"
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $exitCode
