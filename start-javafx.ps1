$ErrorActionPreference = "Stop"

Write-Host "Building platform-desktop-javafx..." -ForegroundColor Cyan
mvn -pl platform-desktop-javafx -am compile -DskipTests -q @args

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "Launching JavaFX desktop client..." -ForegroundColor Green
mvn -pl platform-desktop-javafx javafx:run @args
