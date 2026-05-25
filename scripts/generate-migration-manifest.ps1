param(
    [string]$MigrationDir,
    [string]$OutputFile
)

if (-not $MigrationDir -or -not $OutputFile) {
    Write-Error "Usage: generate-migration-manifest.ps1 -MigrationDir <dir> -OutputFile <file>"
    exit 1
}

if (-not (Test-Path -LiteralPath $MigrationDir)) {
    Write-Error "Migration directory not found: $MigrationDir"
    exit 1
}

$files = Get-ChildItem -LiteralPath $MigrationDir -Filter "V*.sql" | Sort-Object Name
$names = $files | ForEach-Object { $_.BaseName }

$parentDir = [System.IO.Path]::GetDirectoryName([System.IO.Path]::GetFullPath($OutputFile))
if ($parentDir -and -not (Test-Path -LiteralPath $parentDir)) {
    New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
}

$names | Set-Content -LiteralPath $OutputFile -Encoding UTF8NoBOM
Write-Host "Generated manifest with $($names.Count) migrations -> $OutputFile"
