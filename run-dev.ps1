$env:DEV_MODE = "true"
$env:APP_PROFILE = "dev"

Write-Host "Starting outerstellar-starter development mode..." -ForegroundColor Cyan

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

$watcherJob = Start-Job -Name "OuterstellarWatcher" -ScriptBlock {
    param($root)
    Set-Location $root
    mvn -pl web fizzed-watcher:run
} -ArgumentList $projectRoot

try {
    Write-Host "Watcher started in the background. Launching application..." -ForegroundColor Green
    Set-Location $projectRoot
    mvn -pl web compile exec:java
}
finally {
    if (Get-Job -Name "OuterstellarWatcher" -ErrorAction SilentlyContinue) {
        Stop-Job -Name "OuterstellarWatcher"
        Remove-Job -Name "OuterstellarWatcher"
    }
}
