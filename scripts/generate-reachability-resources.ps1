<#
.SYNOPSIS
Generates the "resources" array entries for reachability-metadata.json from actual classpath files.

.DESCRIPTION
Scans known resource locations and outputs JSON entries suitable for pasting into
reachability-metadata.json. Does NOT modify the file - it prints the entries to stdout.
Reflection entries are manual and must be maintained by hand.

.EXAMPLE
pwsh scripts/generate-reachability-resources.ps1
#>

$ErrorActionPreference = "Stop"
$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")

$entries = [System.Collections.Generic.SortedSet[string]]::new()

# Migrations
$migrationDir = Join-Path $RootDir "platform-persistence-jdbi/src/main/resources/db/migration"
if (Test-Path $migrationDir) {
    Get-ChildItem -LiteralPath $migrationDir -Filter "V*.sql" -File | ForEach-Object {
        $entries.Add("db/migration/$($_.Name)") | Out-Null
    }
}

# Migration manifest
$entries.Add("db/migration/migrations.index") | Out-Null

# i18n bundles
$coreResources = Join-Path $RootDir "platform-core/src/main/resources"
if (Test-Path $coreResources) {
    Get-ChildItem -LiteralPath $coreResources -Filter "messages*.properties" -File | ForEach-Object {
        $entries.Add($_.Name) | Out-Null
    }
}

# Logback
if (Test-Path (Join-Path $coreResources "logback.xml")) {
    $entries.Add("logback.xml") | Out-Null
}
if (Test-Path (Join-Path $coreResources "logback-test.xml")) {
    $entries.Add("logback-test.xml") | Out-Null
}

# Application config
Get-ChildItem -LiteralPath $coreResources -Filter "application*.yaml" -File | ForEach-Object {
    $entries.Add($_.Name) | Out-Null
}

# Themes
$themesFile = Join-Path $coreResources "themes.json"
if (Test-Path $themesFile) {
    $entries.Add("themes.json") | Out-Null
}
$themesDir = Join-Path $coreResources "themes"
if (Test-Path $themesDir) {
    Get-ChildItem -LiteralPath $themesDir -Filter "*.json" -File | ForEach-Object {
        $entries.Add("themes/$($_.Name)") | Out-Null
    }
}

# Output as JSON array entries
Write-Output "// Auto-generated resource entries - paste into reachability-metadata.json resources array"
Write-Output "// Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Output ""
foreach ($entry in $entries) {
    Write-Output "    {`"glob`": `"$entry`"},"
}
