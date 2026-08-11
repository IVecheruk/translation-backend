[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$Image = "translation-backend:release-candidate",

    [Parameter()]
    [switch]$SkipVulnerabilityScan
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$projectName = "translatelab-smoke-$PID"
$composeFile = Join-Path $projectRoot "compose.test.yaml"
$previousSmokeImage = $env:BACKEND_SMOKE_IMAGE

Push-Location $projectRoot
try {
    & (Join-Path $PSScriptRoot "scan-static-config.ps1")
    & (Join-Path $PSScriptRoot "test-isolated.ps1")

    Write-Output "Building release candidate only after all tests passed"
    docker build --tag $Image .
    if ($LASTEXITCODE -ne 0) {
        throw "Release candidate image build failed"
    }

    $env:BACKEND_SMOKE_IMAGE = $Image
    $composeArguments = @(
        "compose",
        "--project-name", $projectName,
        "--file", $composeFile,
        "--profile", "smoke"
    )

    Write-Output "Smoke-testing the exact built image"
    docker @composeArguments up `
        --abort-on-container-exit `
        --exit-code-from smoke-check `
        smoke-check
    if ($LASTEXITCODE -ne 0) {
        throw "Release candidate smoke test failed"
    }

    if (-not $SkipVulnerabilityScan) {
        & (Join-Path $PSScriptRoot "security-scan.ps1") `
            -Image $Image
    }
} finally {
    if (Test-Path variable:composeArguments) {
        docker @composeArguments down `
            --volumes `
            --remove-orphans 2>$null | Out-Null
    }
    $env:BACKEND_SMOKE_IMAGE = $previousSmokeImage
    Pop-Location
}

Write-Output "Release verification passed for $Image"
