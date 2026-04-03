param(
    [int]$BackendPort = 18080,
    [switch]$SkipFrontendBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$bundledJava = Join-Path $repoRoot ".tooling\jdk-21\jdk-21.0.10+7\bin\java.exe"
$java = if (Test-Path $bundledJava) { $bundledJava } else { "java" }
$jarPath = Join-Path $repoRoot "travel-agent-app\target\travel-agent-app.jar"
$backendUrl = "http://localhost:$BackendPort"
$stdoutLog = Join-Path $repoRoot "release-smoke.backend.stdout.log"
$stderrLog = Join-Path $repoRoot "release-smoke.backend.stderr.log"
$samplePayload = @{
    message = "Plan a 2 day Hangzhou trip from Shanghai with a 1800 CNY budget. I want a relaxed pace, West Lake, local food, and a museum."
} | ConvertTo-Json -Depth 5

function Wait-ForUrl {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -TimeoutSec 3
            return $response
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "Timed out waiting for $Url"
}

function Wait-ForPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $tcp = Test-NetConnection -ComputerName localhost -Port $Port -WarningAction SilentlyContinue
        if ($tcp.TcpTestSucceeded) {
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for localhost:$Port"
}

Write-Host "Building backend jar for release smoke..."
.\mvnw.cmd -pl travel-agent-app -am -DskipTests package | Out-Null

if (-not $SkipFrontendBuild) {
    Write-Host "Building frontend artifacts for release smoke..."
    Push-Location web
    try {
        npm.cmd run build | Out-Null
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found at $jarPath"
}

if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force }
if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force }

$env:SPRING_AI_OPENAI_API_KEY = "dummy-local-smoke"
$env:TRAVEL_AGENT_TOOL_PROVIDER = "LOCAL"
$env:TRAVEL_AGENT_AMAP_MCP_ENABLED = "false"
$env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED = "false"
$env:TRAVEL_AGENT_MILVUS_ENABLED = "false"
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"

Write-Host "Starting backend on port $BackendPort ..."
$backendProcess = Start-Process -FilePath $java `
    -ArgumentList "-jar", $jarPath, "--server.port=$BackendPort" `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

try {
    Wait-ForPort -Port $BackendPort
    try {
        $healthJson = & curl.exe -s "$backendUrl/actuator/health"
        $health = if ($healthJson) { $healthJson | ConvertFrom-Json } else { @{ status = "UNAVAILABLE" } }
    } catch {
        $health = @{ status = "UNAVAILABLE" }
    }

    $chatResponse = Invoke-RestMethod -Uri "$backendUrl/api/conversations/chat" `
        -Method Post `
        -ContentType "application/json" `
        -Body $samplePayload `
        -TimeoutSec 20

    if ($chatResponse.code -ne "0000") {
        throw "Chat API returned non-success wrapper: $($chatResponse | ConvertTo-Json -Depth 6)"
    }

    $data = $chatResponse.data
    if (-not $data.conversationId) {
        throw "Chat API did not return conversationId"
    }
    if ($data.agentType -ne "TRAVEL_PLANNER") {
        throw "Expected TRAVEL_PLANNER but got $($data.agentType)"
    }
    if (-not $data.travelPlan) {
        throw "Planner smoke did not return a structured travelPlan"
    }
    if (-not $data.travelPlan.weatherSnapshot) {
        throw "Planner smoke did not include weatherSnapshot"
    }
    if (-not $data.travelPlan.knowledgeRetrieval) {
        throw "Planner smoke did not include knowledgeRetrieval"
    }
    if (-not $data.travelPlan.days -or $data.travelPlan.days.Count -lt 1) {
        throw "Planner smoke did not include itinerary days"
    }

    $frontendDist = Join-Path $repoRoot "web\dist\index.html"
    if (-not $SkipFrontendBuild -and -not (Test-Path $frontendDist)) {
        throw "Frontend build artifact missing at $frontendDist"
    }

    Write-Host ""
    Write-Host "Release smoke passed."
    Write-Host "Health: $($health.status)"
    Write-Host "Conversation: $($data.conversationId)"
    Write-Host "Agent: $($data.agentType)"
    Write-Host "Days: $($data.travelPlan.days.Count)"
    Write-Host "Knowledge hints: $($data.travelPlan.knowledgeRetrieval.selections.Count)"
    Write-Host "Weather city: $($data.travelPlan.weatherSnapshot.city)"
    if (-not $SkipFrontendBuild) {
        Write-Host "Frontend artifact: $frontendDist"
    }
}
finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force
    }
}
