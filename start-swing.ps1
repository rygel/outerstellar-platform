$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

Set-Location $projectRoot
mvn compile exec:java "-Dexec.mainClass=dev.outerstellar.starter.swing.SwingSyncAppKt"
