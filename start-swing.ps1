$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $projectRoot
mvn -f desktop/pom.xml -am compile exec:java
