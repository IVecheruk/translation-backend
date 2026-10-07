[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$applicationPropertiesPath = Join-Path `
    $projectRoot `
    "src/main/resources/application.properties"
$productionComposePath = Join-Path $projectRoot "compose.production.yaml"
$migrationDirectory = Join-Path `
    $projectRoot `
    "src/main/resources/db/migration"

$applicationProperties = Get-Content `
    -LiteralPath $applicationPropertiesPath `
    -Raw
$productionCompose = Get-Content `
    -LiteralPath $productionComposePath `
    -Raw

$requiredApplicationSettings = @(
    '(?m)^spring\.jpa\.hibernate\.ddl-auto=validate$',
    '(?m)^springdoc\.api-docs\.enabled=\$\{OPENAPI_DOCS_ENABLED:false\}$',
    '(?m)^springdoc\.swagger-ui\.enabled=\$\{OPENAPI_DOCS_ENABLED:false\}$',
    '(?m)^app\.security\.open-api-public-access=\$\{OPENAPI_PUBLIC_ACCESS:false\}$',
    '(?m)^spring\.jpa\.properties\.jakarta\.persistence\.lock\.timeout=\$\{DB_LOCK_TIMEOUT_MS:3000\}$'
)
foreach ($pattern in $requiredApplicationSettings) {
    if ($applicationProperties -notmatch $pattern) {
        throw "Required safe application default is missing: $pattern"
    }
}

$requiredProductionSettings = @(
    '(?m)^\s+OPENAPI_DOCS_ENABLED: "false"$',
    '(?m)^\s+OPENAPI_PUBLIC_ACCESS: "false"$',
    '(?m)^\s+MINIO_ALLOW_INSECURE_HTTP: "false"$',
    '(?m)^\s+REFRESH_TOKEN_COOKIE_SECURE: "true"$',
    '(?m)^\s+DB_LOCK_TIMEOUT_MS: \$\{DB_LOCK_TIMEOUT_MS:-3000\}$'
)
foreach ($pattern in $requiredProductionSettings) {
    if ($productionCompose -notmatch $pattern) {
        throw "Required safe production setting is missing: $pattern"
    }
}

$forbiddenProductionEnvironment = @(
    'JWT_SECRET:',
    'POSTGRES_PASSWORD:',
    'RABBITMQ_PASSWORD:',
    'MINIO_ROOT_PASSWORD:',
    'MINIO_BACKEND_SECRET_KEY:',
    'TRIBUTE_API_KEY:',
    'SMTP_PASSWORD:'
)
foreach ($setting in $forbiddenProductionEnvironment) {
    if ($productionCompose -cmatch "(?m)^\s+$([regex]::Escape($setting))") {
        throw "Production secret must not be passed through environment: $setting"
    }
}

$destructiveMigrationPattern =
    '(?im)^\s*(DROP\s+(TABLE|SCHEMA|DATABASE)|TRUNCATE\s+|DELETE\s+FROM\s+)'
$unsafeMigrations = Get-ChildItem `
    -LiteralPath $migrationDirectory `
    -Filter "*.sql" `
    -File | Select-String -Pattern $destructiveMigrationPattern
if ($unsafeMigrations) {
    $locations = $unsafeMigrations | ForEach-Object {
        "$($_.Path):$($_.LineNumber)"
    }
    throw "Destructive Flyway statement requires explicit review: $($locations -join ', ')"
}

Write-Output "Static migration and configuration scan passed"
