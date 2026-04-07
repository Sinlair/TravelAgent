param(
    [string]$NssmPath,
    [string]$EnvFile = ".env.travel-agent",
    [int]$BackendPort = 18080,
    [int]$FrontendPort = 4173,
    [switch]$InstallMcp,
    [switch]$Build
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if (-not $NssmPath) {
    throw "Please provide -NssmPath to nssm.exe"
}
if (-not (Test-Path $NssmPath)) {
    throw "nssm.exe not found: $NssmPath"
}

$bundledJava = Join-Path $repoRoot ".tooling\jdk-21\jdk-21.0.10+7\bin\java.exe"
$java = if (Test-Path $bundledJava) { $bundledJava } else { "java" }
$node = "node"
$backendJar = Join-Path $repoRoot "travel-agent-app\target\travel-agent-app.jar"
$mcpJar = Join-Path $repoRoot "travel-agent-amap-mcp-server\target\travel-agent-amap-mcp-server.jar"
$frontendServer = Join-Path $repoRoot "web\server.mjs"
$frontendRoot = Join-Path $repoRoot "web\dist"

function Import-EnvPairs {
    param([string]$Path)
    $pairs = [System.Collections.Generic.List[string]]::new()
    if (-not (Test-Path $Path)) {
        return $pairs
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
        $pairs.Add("$name=$value")
    }
    return $pairs
}

function Contains-EnvName {
    param(
        [string[]]$Pairs,
        [string]$Name
    )
    return $Pairs | Where-Object { $_ -match ("^" + [Regex]::Escape($Name) + "=") } | Select-Object -First 1
}

function Ensure-Service {
    param(
        [string]$ServiceName,
        [string]$AppPath,
        [string[]]$Arguments,
        [string]$WorkDir,
        [string[]]$EnvironmentPairs
    )

    & $NssmPath install $ServiceName $AppPath | Out-Null
    & $NssmPath set $ServiceName AppDirectory $WorkDir | Out-Null
    & $NssmPath set $ServiceName AppParameters ($Arguments -join ' ') | Out-Null
    & $NssmPath set $ServiceName Start SERVICE_AUTO_START | Out-Null
    if ($EnvironmentPairs.Count -gt 0) {
        & $NssmPath set $ServiceName AppEnvironmentExtra ($EnvironmentPairs -join "`n") | Out-Null
    }
}

if ($Build -or -not (Test-Path $backendJar)) {
    .\mvnw.cmd -pl travel-agent-app -am -DskipTests package | Out-Null
}
if ($InstallMcp -and ($Build -or -not (Test-Path $mcpJar))) {
    .\mvnw.cmd -pl travel-agent-amap-mcp-server -am -DskipTests package | Out-Null
}
Push-Location web
try {
    npm.cmd run build | Out-Null
} finally {
    Pop-Location
}

$envPairs = Import-EnvPairs (Join-Path $repoRoot $EnvFile)
$commonPairs = [System.Collections.Generic.List[string]]::new()
$commonPairs.AddRange($envPairs)
if (-not (Contains-EnvName -Pairs $envPairs -Name "SPRING_AI_OPENAI_API_KEY")) {
    $commonPairs.Add("SPRING_AI_OPENAI_API_KEY=dummy-local-service")
}
$commonPairs.Add("TRAVEL_AGENT_ALLOWED_ORIGIN=http://localhost:$FrontendPort")
$commonPairs.Add("TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED=false")
$commonPairs.Add("TRAVEL_AGENT_MILVUS_ENABLED=false")
$commonPairs.Add("JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8")

Ensure-Service `
    -ServiceName "TravelAgentBackend" `
    -AppPath $java `
    -Arguments @("-jar", "`"$backendJar`"", "--server.port=$BackendPort", "--travel.agent.tool-provider=LOCAL", "--spring.ai.mcp.client.enabled=false") `
    -WorkDir $repoRoot `
    -EnvironmentPairs $commonPairs

Ensure-Service `
    -ServiceName "TravelAgentFrontend" `
    -AppPath $node `
    -Arguments @("`"$frontendServer`"", "--root", "`"$frontendRoot`"", "--port", "$FrontendPort", "--backend", "http://localhost:$BackendPort") `
    -WorkDir (Join-Path $repoRoot "web") `
    -EnvironmentPairs @()

if ($InstallMcp) {
    $mcpPairs = [System.Collections.Generic.List[string]]::new()
    $mcpPairs.AddRange($envPairs)
    Ensure-Service `
        -ServiceName "TravelAgentMcp" `
        -AppPath $java `
        -Arguments @("-jar", "`"$mcpJar`"") `
        -WorkDir $repoRoot `
        -EnvironmentPairs $mcpPairs
}

Write-Host "Windows services installed via NSSM."
Write-Host "Services:"
Write-Host "- TravelAgentBackend"
Write-Host "- TravelAgentFrontend"
if ($InstallMcp) {
    Write-Host "- TravelAgentMcp"
}
Write-Host "Use Services.msc or 'nssm start <service>' to start them."
