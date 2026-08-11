[CmdletBinding()]
param(
    [Parameter()]
    [switch]$KeepInfrastructure
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$projectName = "translatelab-test-$PID"
$composeFile = Join-Path $projectRoot "compose.test.yaml"
$composeArguments = @(
    "compose",
    "--project-name", $projectName,
    "--file", $composeFile,
    "--profile", "test"
)

Push-Location $projectRoot
try {
    docker volume create translatelab-maven-test-cache | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to create the isolated Maven dependency cache"
    }

    Write-Output "Validating isolated test topology"
    docker @composeArguments config --quiet
    if ($LASTEXITCODE -ne 0) {
        throw "Isolated test Compose validation failed"
    }

    Write-Output "Running the complete suite on pinned Java 21"
    docker @composeArguments up `
        --abort-on-container-exit `
        --exit-code-from test-runner `
        test-runner
    if ($LASTEXITCODE -ne 0) {
        throw "Isolated Java 21 test suite failed"
    }

    $runnerContainer = docker @composeArguments ps `
        --all `
        --quiet `
        test-runner
    if ([string]::IsNullOrWhiteSpace($runnerContainer)) {
        throw "Unable to locate the completed isolated test container"
    }

    $qualityArtifacts = Join-Path `
        $projectRoot `
        "target/isolated-quality"
    $coverageDirectory = Join-Path $qualityArtifacts "jacoco"
    $testReportsDirectory = Join-Path `
        $qualityArtifacts `
        "surefire-reports"
    New-Item -ItemType Directory -Force `
        -Path $coverageDirectory | Out-Null
    New-Item -ItemType Directory -Force `
        -Path $testReportsDirectory | Out-Null

    docker cp `
        "${runnerContainer}:/workspace/target/site/jacoco/." `
        $coverageDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to export the isolated JaCoCo report"
    }
    docker cp `
        "${runnerContainer}:/workspace/target/surefire-reports/." `
        $testReportsDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to export the isolated Surefire reports"
    }
} finally {
    if (-not $KeepInfrastructure) {
        docker @composeArguments down `
            --volumes `
            --remove-orphans 2>$null | Out-Null
    }
    Pop-Location
}

Write-Output "Isolated Java 21 quality gate passed"
Write-Output "Reports: target/isolated-quality"
