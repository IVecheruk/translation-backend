param(
    [Parameter(Mandatory = $true)]
    [string]$SecretsDirectory
)

$ErrorActionPreference = "Stop"
$resolvedDirectory = (Resolve-Path -LiteralPath $SecretsDirectory).Path

$requiredFiles = @(
    "postgres_password",
    "rabbitmq_password",
    "jwt_secret",
    "minio_backend_access_key",
    "minio_backend_secret_key",
    "tribute_api_key",
    "smtp_username",
    "smtp_password",
    "alertmanager.yml"
)

$values = @{}
foreach ($name in $requiredFiles) {
    $path = Join-Path $resolvedDirectory $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required production secret is missing: $name"
    }
    $value = (Get-Content -LiteralPath $path -Raw).Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Production secret is empty: $name"
    }
    $values[$name] = $value
}

foreach ($passwordName in @(
    "postgres_password",
    "rabbitmq_password",
    "minio_backend_secret_key",
    "smtp_password"
)) {
    if ($values[$passwordName].Length -lt 16) {
        throw "Production password is shorter than 16 characters: $passwordName"
    }
}

try {
    $jwtBytes = [Convert]::FromBase64String($values["jwt_secret"])
} catch {
    throw "jwt_secret must be valid Base64"
}
if ($jwtBytes.Length -lt 32) {
    throw "jwt_secret must contain at least 32 decoded bytes"
}

if ($values["postgres_password"] -ceq $values["rabbitmq_password"] -or
        $values["postgres_password"] -ceq $values["minio_backend_secret_key"] -or
        $values["rabbitmq_password"] -ceq $values["minio_backend_secret_key"]) {
    throw "PostgreSQL, RabbitMQ, and MinIO passwords must be independent"
}

if ($values["alertmanager.yml"] -match "example\.invalid") {
    throw "Alertmanager still contains the example notification endpoint"
}

Write-Output "Production secret structure validation passed"
