param(
    [ValidateRange(0, 65535)]
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'Create the root .env from .env.example and configure PostgreSQL first.'
}

# Read only backend settings. Never evaluate .env as PowerShell or log its values.
$settings = @{}
$allowed = @('POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USER',
             'POSTGRES_PASSWORD', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT',
             'AWS_REGION', 'AWS_PROFILE', 'S3_BUCKET_NAME', 'AUTH_ADMIN_USERNAME', 'AUTH_ADMIN_EMAIL',
             'AUTH_ADMIN_PASSWORD', 'SESSION_COOKIE_SECURE', 'FORWARD_HEADERS_STRATEGY', 'ANALYTICS_RETENTION_DAYS', 'ANALYTICS_PUBLIC_ORIGIN', 'OPENAI_API_KEY', 'OPENAI_MODEL')
foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
    if ($line.Trim() -match '^(?:export\s+)?([A-Z_]+)\s*=(.*)$') {
        $key = $matches[1]
        $value = $matches[2].Trim()
        if ($allowed -contains $key) {
            if ($value.Length -ge 2 -and
                (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                 ($value.StartsWith("'") -and $value.EndsWith("'")))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $settings[$key] = $value
        }
    }
}

foreach ($key in @('AWS_REGION', 'AWS_PROFILE', 'S3_BUCKET_NAME', 'AUTH_ADMIN_USERNAME', 'AUTH_ADMIN_EMAIL',
                    'AUTH_ADMIN_PASSWORD', 'SESSION_COOKIE_SECURE', 'FORWARD_HEADERS_STRATEGY', 'ANALYTICS_RETENTION_DAYS', 'ANALYTICS_PUBLIC_ORIGIN', 'OPENAI_API_KEY', 'OPENAI_MODEL')) {
    if ($settings[$key]) {
        [Environment]::SetEnvironmentVariable($key, $settings[$key], 'Process')
    }
}

if (-not $settings['POSTGRES_HOST']) { $settings['POSTGRES_HOST'] = 'localhost' }
if (-not $settings['POSTGRES_PORT']) { $settings['POSTGRES_PORT'] = '15432' }
if (-not $settings['POSTGRES_DB']) { $settings['POSTGRES_DB'] = 'devflow' }
if (-not $settings['POSTGRES_USER']) { $settings['POSTGRES_USER'] = 'devflow' }

# Root .env is authoritative for this launcher, replacing stale shell credentials.
$env:DB_URL = if ($settings['DB_URL']) { $settings['DB_URL'] } else {
    "jdbc:postgresql://$($settings['POSTGRES_HOST']):$($settings['POSTGRES_PORT'])/$($settings['POSTGRES_DB'])"
}
$env:DB_USERNAME = if ($settings['DB_USERNAME']) { $settings['DB_USERNAME'] } else { $settings['POSTGRES_USER'] }
$env:DB_PASSWORD = if ($settings.ContainsKey('DB_PASSWORD')) { $settings['DB_PASSWORD'] } else { $settings['POSTGRES_PASSWORD'] }
if ([string]::IsNullOrEmpty($env:DB_PASSWORD)) { throw 'Configure DB_PASSWORD or POSTGRES_PASSWORD in root .env.' }
$env:SERVER_PORT = if ($Port) { [string]$Port } elseif ($settings['SERVER_PORT']) { $settings['SERVER_PORT'] } else { '8080' }
$env:SERVER_ADDRESS = '127.0.0.1'
if (-not $env:SESSION_COOKIE_SECURE) { $env:SESSION_COOKIE_SECURE = 'false' }

if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
    Write-Warning 'AI drafts are disabled. Set OPENAI_API_KEY in the root .env or in the backend process environment and restart.'
}
Write-Host 'Starting local DevFlow backend on 127.0.0.1 using root .env (credentials hidden).'
Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\gradlew.bat bootRun --no-daemon
    $backendExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $backendExitCode
