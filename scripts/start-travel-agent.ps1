param(
    [ValidateSet("LOCAL", "MCP")]
    [string]$ToolProvider = "LOCAL",
    [string]$EnvFile = ".env.travel-agent",
    [int]$BackendPort = 18080,
    [int]$FrontendPort = 4173,
    [switch]$Build,
    [switch]$StartFrontend,
    [switch]$StartMcp,
    [switch]$RunPreflight
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$runtimeDir = Join-Path $repoRoot "data\runtime"
$bundledJava = Join-Path $repoRoot ".tooling\jdk-21\jdk-21.0.10+7\bin\java.exe"
$java = if (Test-Path $bundledJava) { $bundledJava } else { "java" }
$backendJar = Join-Path $repoRoot "travel-agent-app\target\travel-agent-app.jar"
$backendStdout = Join-Path $runtimeDir "backend.stdout.log"
$backendStderr = Join-Path $runtimeDir "backend.stderr.log"
$mcpStdout = Join-Path $runtimeDir "mcp.stdout.log"
$mcpStderr = Join-Path $runtimeDir "mcp.stderr.log"
$frontendStdout = Join-Path $runtimeDir "frontend.stdout.log"
$frontendStderr = Join-Path $runtimeDir "frontend.stderr.log"
$backendPidFile = Join-Path $runtimeDir "backend.pid"
$mcpPidFile = Join-Path $runtimeDir "mcp.pid"
$frontendPidFile = Join-Path $runtimeDir "frontend.pid"
$mcpBaseUrl = if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_AMAP_MCP_BASE_URL)) { "http://localhost:8090" } else { $env:TRAVEL_AGENT_AMAP_MCP_BASE_URL }

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

function Write-Pid {
    param(
        [string]$Path,
        [int]$ProcessId
    )
    Set-Content -Path $Path -Value $ProcessId -Encoding ascii
}

function Wait-ForPort {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 120
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

New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
Import-EnvFile (Join-Path $repoRoot $EnvFile)

$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
$env:TRAVEL_AGENT_TOOL_PROVIDER = $ToolProvider
$env:TRAVEL_AGENT_ALLOWED_ORIGIN = "http://localhost:$FrontendPort"

if ([string]::IsNullOrWhiteSpace($env:SPRING_AI_OPENAI_API_KEY)) {
    $env:SPRING_AI_OPENAI_API_KEY = "dummy-local-startup"
}
if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED)) {
    $env:TRAVEL_AGENT_KNOWLEDGE_VECTOR_ENABLED = "false"
}
if ([string]::IsNullOrWhiteSpace($env:TRAVEL_AGENT_MILVUS_ENABLED)) {
    $env:TRAVEL_AGENT_MILVUS_ENABLED = "false"
}

if ($ToolProvider -eq "LOCAL") {
    $env:TRAVEL_AGENT_AMAP_MCP_ENABLED = "false"
} else {
    $env:TRAVEL_AGENT_AMAP_MCP_ENABLED = "true"
}

if ($RunPreflight) {
    $preflightArgs = @("-ExecutionPolicy", "Bypass", "-File", ".\scripts\preflight-travel-agent.ps1", "-EnvFile", $EnvFile)
    if ($ToolProvider -eq "LOCAL") {
        $preflightArgs += "-SkipHealthCheck"
    }
    powershell @preflightArgs
}

if ($Build -or -not (Test-Path $backendJar)) {
    Write-Host "Building backend jar..."
    .\mvnw.cmd -pl travel-agent-app -am -DskipTests package | Out-Null
}

if ($StartFrontend) {
    Write-Host "Building frontend..."
    Push-Location web
    try {
        npm.cmd run build | Out-Null
    } finally {
        Pop-Location
    }
}

if ($ToolProvider -eq "MCP" -and $StartMcp) {
    Write-Host "Starting MCP server..."
    $mcpProcess = Start-Process -FilePath (Join-Path $repoRoot "mvnw.cmd") `
        -ArgumentList "-pl", "travel-agent-amap-mcp-server", "-am", "spring-boot:run" `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $mcpStdout `
        -RedirectStandardError $mcpStderr `
        -PassThru
    Write-Pid -Path $mcpPidFile -ProcessId $mcpProcess.Id
}

Write-Host "Starting backend on port $BackendPort..."
$backendProcess = Start-Process -FilePath $java `
    -ArgumentList "-jar", $backendJar, "--server.port=$BackendPort" `
    -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $backendStdout `
        -RedirectStandardError $backendStderr `
        -PassThru
Write-Pid -Path $backendPidFile -ProcessId $backendProcess.Id
try {
    Wait-ForPort -Port $BackendPort
} catch {
    if (Test-Path $backendStdout) {
        Write-Host ""
        Write-Host "Backend stdout tail:"
        Get-Content $backendStdout -Tail 80
    }
    if (Test-Path $backendStderr) {
        Write-Host ""
        Write-Host "Backend stderr tail:"
        Get-Content $backendStderr -Tail 80
    }
    throw
}

if ($StartFrontend) {
    Write-Host "Starting frontend preview on port $FrontendPort..."
    $frontendProcess = Start-Process -FilePath "npm.cmd" `
        -ArgumentList "run", "preview", "--", "--host", "0.0.0.0", "--port", "$FrontendPort" `
        -WorkingDirectory (Join-Path $repoRoot "web") `
        -RedirectStandardOutput $frontendStdout `
        -RedirectStandardError $frontendStderr `
        -PassThru
    Write-Pid -Path $frontendPidFile -ProcessId $frontendProcess.Id
}

Write-Host ""
Write-Host "Travel Agent started."
Write-Host "Backend:  http://localhost:$BackendPort"
if ($StartFrontend) {
    Write-Host "Frontend: http://localhost:$FrontendPort"
}
if ($ToolProvider -eq "MCP" -and $StartMcp) {
    Write-Host "MCP:      $mcpBaseUrl"
}
Write-Host "Logs:     $runtimeDir"
