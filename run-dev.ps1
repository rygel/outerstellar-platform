$env:DEV_MODE = "true"

Write-Host "Starting outerstellar-starter development mode..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$watcherJob = Start-Job -Name "OuterstellarWatcher" -ScriptBlock {
    param($root)
    Set-Location $root
    mvn fizzed-watcher:run
} -ArgumentList $projectRoot

try {
    Write-Host "Watcher started in the background. Launching application..." -ForegroundColor Green
    Set-Location $projectRoot
    mvn compile exec:java
}
finally {
    if (Get-Job -Name "OuterstellarWatcher" -ErrorAction SilentlyContinue) {
        Stop-Job -Name "OuterstellarWatcher"
        Remove-Job -Name "OuterstellarWatcher"
    }
}
