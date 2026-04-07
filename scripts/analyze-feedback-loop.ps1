param(
    [string]$BackendUrl = "http://localhost:18080",
    [int]$Limit = 200,
    [string]$JsonOutFile = ".\\data\\exports\\feedback-loop-summary.json",
    [string]$ReportOutFile = ".\\data\\exports\\feedback-loop-report.md"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

function Resolve-OutPath([string]$PathValue) {
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $PathValue))
    $dir = Split-Path -Parent $resolved
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    return $resolved
}

function Format-Rate([object]$Value) {
    if ($null -eq $Value) {
        return "0.00"
    }
    return ("{0:N2}" -f [double]$Value)
}

function Add-BreakdownSection([System.Collections.Generic.List[string]]$Lines, [string]$Title, $Items, [string]$KeyColumn) {
    if ($null -eq $Items -or $Items.Count -eq 0) {
        return
    }
    $Lines.Add("")
    $Lines.Add("## $Title")
    $Lines.Add("")
    $Lines.Add("| $KeyColumn | Total | Accepted | Partial | Rejected | Accepted % | Usable % |")
    $Lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: |")
    foreach ($item in $Items) {
        $Lines.Add("| $($item.key) | $($item.totalCount) | $($item.acceptedCount) | $($item.partialCount) | $($item.rejectedCount) | $(Format-Rate $item.acceptedRatePct) | $(Format-Rate $item.usableRatePct) |")
    }
}

function Add-FindingSection([System.Collections.Generic.List[string]]$Lines, $Items) {
    if ($null -eq $Items -or $Items.Count -eq 0) {
        return
    }
    $Lines.Add("")
    $Lines.Add("## Key Findings")
    $Lines.Add("")
    $index = 1
    foreach ($item in $Items) {
        $Lines.Add("$index. [$($item.type)] $($item.key): total $($item.totalCount), accepted $($item.acceptedCount), partial $($item.partialCount), rejected $($item.rejectedCount), usable $(Format-Rate $item.usableRatePct)%")
        $Lines.Add("   Recommendation: $($item.recommendation)")
        $index += 1
    }
}

$jsonPath = Resolve-OutPath $JsonOutFile
$reportPath = Resolve-OutPath $ReportOutFile

$response = Invoke-RestMethod -Uri "$BackendUrl/api/conversations/feedback/summary?limit=$Limit" -TimeoutSec 30
if ($response.code -ne "0000") {
    throw "Feedback summary API returned $($response.code): $($response.info)"
}

$summary = $response.data
$summary | ConvertTo-Json -Depth 10 | Set-Content -Path $jsonPath -Encoding utf8

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# Feedback Loop Report")
$lines.Add("")
$lines.Add("- Generated at: $($summary.generatedAt)")
$lines.Add("- Sampled feedback: $($summary.sampleCount)")
$lines.Add("- Limit applied: $($summary.limitApplied)")
$lines.Add("- Accepted: $($summary.acceptedCount) ($(Format-Rate $summary.acceptedRatePct)%)")
$lines.Add("- Partial: $($summary.partialCount)")
$lines.Add("- Rejected: $($summary.rejectedCount)")
$lines.Add("- Usable (accepted + partial): $(Format-Rate $summary.usableRatePct)%")
$lines.Add("- Structured plans returned: $($summary.structuredPlanCount) ($(Format-Rate $summary.structuredPlanCoveragePct)%)")

Add-BreakdownSection -Lines $lines -Title "Top Reason Codes" -Items $summary.topReasonCodes -KeyColumn "Reason"
Add-BreakdownSection -Lines $lines -Title "Top Destinations" -Items $summary.topDestinations -KeyColumn "Destination"
Add-BreakdownSection -Lines $lines -Title "Top Agent Types" -Items $summary.topAgentTypes -KeyColumn "Agent"
Add-FindingSection -Lines $lines -Items $summary.keyFindings

$lines -join [Environment]::NewLine | Set-Content -Path $reportPath -Encoding utf8

Write-Host "Feedback loop summary exported to $jsonPath"
Write-Host "Feedback loop report exported to $reportPath"
