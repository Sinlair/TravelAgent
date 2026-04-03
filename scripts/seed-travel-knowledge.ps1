param(
    [string]$MilvusUri = $(if ($env:TRAVEL_AGENT_MILVUS_URI) { $env:TRAVEL_AGENT_MILVUS_URI } else { "http://localhost:19530" }),
    [string[]]$VerifyCities = @("Hangzhou", "Beijing", "Shanghai"),
    [switch]$SkipVerify
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$env:SPRING_AI_OPENAI_API_KEY = "dummy-for-seed"
$env:TRAVEL_AGENT_MILVUS_ENABLED = "true"
$env:TRAVEL_AGENT_MILVUS_URI = $MilvusUri
$env:TRAVEL_AGENT_AMAP_MCP_ENABLED = "false"
$env:TRAVEL_AGENT_TOOL_PROVIDER = "LOCAL"
$bundledJava = Join-Path $repoRoot ".tooling\jdk-21\jdk-21.0.10+7\bin\java.exe"
$java = if (Test-Path $bundledJava) { $bundledJava } else { "java" }

Write-Host "Seeding cleaned travel knowledge into Milvus at $MilvusUri ..."
.\mvnw.cmd "-Dmaven.repo.local=.m2\repository" -pl travel-agent-app -am -DskipTests package

$verifyEnabled = if ($SkipVerify) { "false" } else { "true" }
$verifyCitiesArg = ($VerifyCities | Where-Object { $_ -and $_.Trim() } | ForEach-Object { $_.Trim() }) -join ","

& $java -jar '.\travel-agent-app\target\travel-agent-app.jar' `
  --spring.main.web-application-type=none `
  --travel.agent.knowledge.seed.enabled=true `
  --travel.agent.knowledge.seed.verify.enabled=$verifyEnabled `
  --travel.agent.knowledge.seed.verify.sample-cities=$verifyCitiesArg
