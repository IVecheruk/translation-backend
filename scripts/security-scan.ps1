[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Image = "translation-backend:local",

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$OutputDirectory = "target/security"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$outputPath = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory
} else {
    Join-Path $projectRoot $OutputDirectory
}

New-Item -ItemType Directory -Path $outputPath -Force | Out-Null

$sbomPath = Join-Path $outputPath "translation-backend.cdx.json"
$reportPath = Join-Path $outputPath "translation-backend-cves.md"
$localImage = "local://$Image"

Write-Output "Generating CycloneDX SBOM for $Image"
docker scout sbom `
    --format cyclonedx `
    --output $sbomPath `
    $localImage

if ($LASTEXITCODE -ne 0) {
    throw "Docker Scout could not generate the image SBOM"
}

Write-Output "Scanning $Image for Critical and High vulnerabilities"
docker scout cves `
    --only-severity critical,high `
    --format markdown `
    --output $reportPath `
    --exit-code `
    $localImage

$scanExitCode = $LASTEXITCODE

if ($scanExitCode -eq 2) {
    Write-Error `
        -ErrorAction Continue `
        "Release gate failed: Critical or High vulnerabilities were found. Review $reportPath"
    exit 2
}

if ($scanExitCode -ne 0) {
    throw "Docker Scout vulnerability scan failed with exit code $scanExitCode"
}

Write-Output "Security scan passed"
Write-Output "SBOM: $sbomPath"
Write-Output "Vulnerability report: $reportPath"
