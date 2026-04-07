param(
    [string]$BackendUrl = "http://localhost:8080",
    [string]$EnvFile = ".env.travel-agent",
    [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$checks = [System.Collections.Generic.List[object]]::new()
$bundledJava = Join-Path $repoRoot ".tooling\jdk-21\jdk-21.0.10+7\bin\java.exe"

function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        return
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $index = $line.IndexOf("=")
        if ($index -lt 1) {
            return
        }
        $name = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()
        [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Add-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )
    $checks.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
}

function Test-EnvConfigured {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    $normalized = $Value.Trim().ToLowerInvariant()
    return -not ($normalized.Contains("dummy") -or $normalized.Contains("placeholder") -or $normalized.Contains("example"))
}

Import-EnvFile (Join-Path $repoRoot $EnvFile)

try {
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    if (Test-Path $bundledJava) {
        $javaVersion = & $bundledJava -version 2>&1 | Select-String "version"
    } else {
        $javaVersion = & java -version 2>&1 | Select-String "version"
    }
    $ErrorActionPreference = $previousErrorAction
    if (-not $javaVersion) {
        throw "Java version output missing"
    }
    Add-Check "Java" "PASS" ($javaVersion -join "")
} catch {
    $ErrorActionPreference = "Stop"
    Add-Check "Java" "FAIL" "Java runtime check failed"
}

$openAiConfigured = Test-EnvConfigured $env:SPRING_AI_OPENAI_API_KEY
Add-Check "OpenAI" ($(if ($openAiConfigured) { "PASS" } else { "WARN" })) ($(if ($openAiConfigured) { "API key configured" } else { "API key missing or placeholder-like" }))

$toolProvider = if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_TOOL_PROVIDER)) { "MCP" } else { $env:TRAVEL_AGENT_TOOL_PROVIDER }
Add-Check "ToolProvider" "PASS" "Configured provider: $toolProvider"

if ($toolProvider -eq "MCP") {
    $mcpUrl = if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_AMAP_MCP_BASE_URL)) { "http://localhost:8090" } else { $env:TRAVEL_AGENT_AMAP_MCP_BASE_URL }
    try {
        $uri = [System.Uri]$mcpUrl
        $port = if ($uri.Port -gt 0) { $uri.Port } else { 80 }
        $tcp = Test-NetConnection -ComputerName $uri.Host -Port $port -WarningAction SilentlyContinue
        Add-Check "MCP" ($(if ($tcp.TcpTestSucceeded) { "PASS" } else { "FAIL" })) "$mcpUrl reachable=$($tcp.TcpTestSucceeded)"
    } catch {
        Add-Check "MCP" "FAIL" "Invalid MCP url: $mcpUrl"
    }
}

$knowledgeVectorEnabled = if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED)) {
    if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_MILVUS_ENABLED)) { $false } else { $env:TRAVEL_AGENT_MILVUS_ENABLED -eq "true" }
} else {
    $env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED -eq "true"
}

if ($knowledgeVectorEnabled) {
    $milvusUri = if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI)) {
        if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_MILVUS_URI)) { "http://localhost:19530" } else { $env:TRAVEL_AGENT_MILVUS_URI }
    } else {
        $env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_URI
    }
    try {
        $uri = [System.Uri]$milvusUri
        $port = if ($uri.Port -gt 0) { $uri.Port } else { 19530 }
        $tcp = Test-NetConnection -ComputerName $uri.Host -Port $port -WarningAction SilentlyContinue
        Add-Check "KnowledgeVector" ($(if ($tcp.TcpTestSucceeded) { "PASS" } else { "FAIL" })) "$milvusUri reachable=$($tcp.TcpTestSucceeded)"
    } catch {
        Add-Check "KnowledgeVector" "FAIL" "Invalid Milvus uri: $milvusUri"
    }
}

$knowledgeFile = Join-Path $repoRoot "travel-agent-infrastructure\src\main\resources\travel-knowledge.cleaned.json"
if (Test-Path $knowledgeFile) {
    try {
        $records = Get-Content $knowledgeFile -Raw -Encoding UTF8 | ConvertFrom-Json
        $recordCount = @($records).Count
        if ($recordCount -gt 0) {
            Add-Check "KnowledgeData" "PASS" "cleaned records=$recordCount"
        } else {
            Add-Check "KnowledgeData" "FAIL" "cleaned knowledge file is empty"
        }
    } catch {
        Add-Check "KnowledgeData" "FAIL" "Failed to parse cleaned knowledge file"
    }
} else {
    Add-Check "KnowledgeData" "FAIL" "travel-knowledge.cleaned.json missing"
}

if (-not $SkipHealthCheck) {
    try {
        $health = Invoke-RestMethod -Uri "$BackendUrl/actuator/health" -TimeoutSec 5
        Add-Check "BackendHealth" ($(if ($health.status -eq "UP") { "PASS" } else { "FAIL" })) "health=$($health.status)"
    } catch {
        Add-Check "BackendHealth" "WARN" "Backend not reachable at $BackendUrl/actuator/health"
    }
}

$checks | Format-Table -AutoSize

$failed = @($checks | Where-Object { $_.Status -eq "FAIL" }).Count
if ($failed -gt 0) {
    exit 1
}
