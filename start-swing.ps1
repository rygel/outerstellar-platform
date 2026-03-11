$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $projectRoot

# Build both core and desktop in one reactor so resource changes (e.g. i18n keys)
# are always picked up before launching Swing.
mvn -f pom.xml -Pruntime-dev -pl core,desktop -am compile
mvn -f pom.xml -Pruntime-dev -pl desktop exec:java
