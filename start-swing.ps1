$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $projectRoot
mvn -pl desktop compile exec:java
