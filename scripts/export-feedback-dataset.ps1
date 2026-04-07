param(
    [string]$BackendUrl = "http://localhost:18080",
    [int]$Limit = 200,
    [string]$OutFile = ".\\data\\exports\\feedback-dataset.json"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$outPath = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $OutFile))
$outDir = Split-Path -Parent $outPath
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$response = Invoke-RestMethod -Uri "$BackendUrl/api/conversations/feedback/export?limit=$Limit" -TimeoutSec 30
if ($response.code -ne "0000") {
    throw "Export API returned $($response.code): $($response.info)"
}

$json = $response.data | ConvertTo-Json -Depth 20
Set-Content -Path $outPath -Value $json -Encoding utf8

Write-Host "Feedback dataset exported to $outPath"
