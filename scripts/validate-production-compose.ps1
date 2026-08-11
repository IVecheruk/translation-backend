param(
    [string]$VariablesFile = "deploy/production/variables.example"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $VariablesFile)) {
    throw "Production variables file was not found: $VariablesFile"
}

$rendered = & docker compose `
    --env-file $VariablesFile `
    -f compose.production.yaml `
    config `
    --format json

if ($LASTEXITCODE -ne 0) {
    throw "Production Compose configuration is invalid"
}

$configuration = $rendered | ConvertFrom-Json
$digestPattern = "@sha256:[a-f0-9]{64}$"

foreach ($serviceProperty in $configuration.services.PSObject.Properties) {
    $serviceName = $serviceProperty.Name
    $service = $serviceProperty.Value

    if ($service.PSObject.Properties.Name -contains "build") {
        throw "Production service '$serviceName' must use a prebuilt image"
    }
    if ($service.PSObject.Properties.Name -contains "container_name") {
        throw "Production service '$serviceName' must not fix container_name"
    }
    if ($service.image -notmatch $digestPattern) {
        throw "Production image '$serviceName' is not pinned by digest"
    }

    $publishedPorts = @($service.ports | Where-Object {
        $_.PSObject.Properties.Name -contains "published"
    })
    if ($serviceName -ne "edge" -and $publishedPorts.Count -gt 0) {
        throw "Only the edge service may publish host ports"
    }
}

$backend = $configuration.services.backend
if (-not $backend.read_only) {
    throw "Backend root filesystem must be read-only"
}
if (@($backend.cap_drop) -notcontains "ALL") {
    throw "Backend must drop all Linux capabilities"
}
if (@($backend.security_opt) -notcontains "no-new-privileges:true") {
    throw "Backend must enable no-new-privileges"
}
if ($backend.environment.MINIO_ALLOW_INSECURE_HTTP -ne "false") {
    throw "Production MinIO HTTP must be disabled"
}
if (-not $backend.environment.MINIO_ENDPOINT.StartsWith("https://")) {
    throw "Production MinIO endpoint must use HTTPS"
}
if ($backend.environment.MANAGEMENT_SERVER_PORT -ne "8081") {
    throw "Operational endpoints must use the internal management port"
}

$backendEnvironmentNames = @($backend.environment.PSObject.Properties.Name)
foreach ($forbidden in @(
    "JWT_SECRET",
    "POSTGRES_PASSWORD",
    "RABBITMQ_PASSWORD",
    "MINIO_ROOT_USER",
    "MINIO_ROOT_PASSWORD",
    "MINIO_BACKEND_SECRET_KEY",
    "TRIBUTE_API_KEY",
    "SMTP_PASSWORD"
)) {
    if ($backendEnvironmentNames -contains $forbidden) {
        throw "Secret '$forbidden' must not be passed through environment"
    }
}

if (-not $configuration.networks.data.internal) {
    throw "Data network must be internal"
}
if (-not $configuration.networks.operations.internal) {
    throw "Operations network must be internal"
}

$edgePorts = @($configuration.services.edge.ports | Where-Object {
    $_.PSObject.Properties.Name -contains "published"
})
$publicTargets = @($edgePorts | ForEach-Object { [int]$_.target } | Sort-Object -Unique)
if (($publicTargets -join ",") -ne "80,443") {
    throw "Edge must be the only entrypoint and publish only ports 80/443"
}

Write-Output "Production Compose validation passed"
