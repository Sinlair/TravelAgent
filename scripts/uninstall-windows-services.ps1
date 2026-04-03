param(
    [string]$NssmPath
)

$ErrorActionPreference = "Stop"

if (-not $NssmPath) {
    throw "Please provide -NssmPath to nssm.exe"
}
if (-not (Test-Path $NssmPath)) {
    throw "nssm.exe not found: $NssmPath"
}

$services = @("TravelAgentFrontend", "TravelAgentBackend", "TravelAgentMcp")
foreach ($service in $services) {
    & $NssmPath stop $service confirm 2>$null | Out-Null
    & $NssmPath remove $service confirm 2>$null | Out-Null
    Write-Host "Removed $service (if present)"
}
