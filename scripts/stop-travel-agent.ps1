param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$runtimeDir = Join-Path $repoRoot "data\runtime"
$pidFiles = @(
    @{ Name = "frontend"; Path = (Join-Path $runtimeDir "frontend.pid") },
    @{ Name = "backend"; Path = (Join-Path $runtimeDir "backend.pid") },
    @{ Name = "mcp"; Path = (Join-Path $runtimeDir "mcp.pid") }
)

foreach ($pidFile in $pidFiles) {
    if (-not (Test-Path $pidFile.Path)) {
        continue
    }
    $pidValue = Get-Content $pidFile.Path -ErrorAction SilentlyContinue
    if ($pidValue -and ($pidValue -as [int])) {
        $process = Get-Process -Id ([int]$pidValue) -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $process.Id -Force
            Write-Host "Stopped $($pidFile.Name) process $($process.Id)"
        }
    }
    Remove-Item $pidFile.Path -Force -ErrorAction SilentlyContinue
}
