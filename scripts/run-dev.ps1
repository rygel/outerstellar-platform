$env:DEV_MODE = "true"
$env:APP_PROFILE = "dev"
$markerOwner = if ($env:AGENT_OWNER) { $env:AGENT_OWNER } else { "outerstellar-run-dev" }
$markerTask = if ($env:AGENT_TASK) { $env:AGENT_TASK } else { "web-dev" }
$env:AGENT_OWNER = $markerOwner
$env:AGENT_TASK = $markerTask
$env:MAVEN_OPTS = (($env:MAVEN_OPTS, "-Dagent.owner=$markerOwner -Dagent.task=$markerTask") | Where-Object { $_ }) -join " "

Write-Host "Starting outerstellar-platform development mode..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# Stop existing instances first
& (Join-Path $projectRoot "stop-web.ps1")

$watcherJob = Start-Job -Name "OuterstellarWatcher" -ScriptBlock {
    param($root)
    Set-Location $root
    mvn -pl platform-web fizzed-watcher:run
} -ArgumentList $projectRoot

try {
    Write-Host "Watcher started in the background. Launching application..." -ForegroundColor Green
    Set-Location $projectRoot
    mvn -pl platform-web compile exec:java
}
finally {
    if (Get-Job -Name "OuterstellarWatcher" -ErrorAction SilentlyContinue) {
        Stop-Job -Name "OuterstellarWatcher"
        Remove-Job -Name "OuterstellarWatcher"
    }
}
