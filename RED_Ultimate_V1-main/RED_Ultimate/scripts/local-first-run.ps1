param(
    [string]$ServerIp,
    [ValidateRange(1024, 65535)][int]$HttpPort = 8088,
    [switch]$BuildAndroid
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent $Root
$EnvFile = Join-Path $Root ".env"

if (-not $ServerIp) {
    $ServerIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown' } |
        Select-Object -First 1 -ExpandProperty IPAddress)
}
if (-not $ServerIp -or $ServerIp -notmatch '^([0-9]{1,3}\.){3}[0-9]{1,3}$') {
    throw "Pass a local server IPv4 address with -ServerIp"
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker Desktop is required" }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not running" }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Compose v2 is required" }

function New-Hex([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return (-join ($buffer | ForEach-Object { $_.ToString("x2") }))
}

if (-not (Test-Path $EnvFile)) {
    $text = Get-Content (Join-Path $Root ".env.example") -Raw
    $replacements = @{
        "replace_with_a_long_random_database_password" = (New-Hex 32)
        "replace_with_a_long_random_mongodb_password" = (New-Hex 32)
        "replace_with_a_long_random_minio_password" = (New-Hex 32)
        "replace_with_a_long_random_redis_password" = (New-Hex 32)
        "replace_with_a_long_random_turn_secret" = (New-Hex 32)
        "replace_with_at_least_32_random_characters" = (New-Hex 48)
        "replace_with_at_least_14_random_characters" = (New-Hex 20)
        "192.168.0.244" = $ServerIp
    }
    foreach ($entry in $replacements.GetEnumerator()) { $text = $text.Replace($entry.Key, $entry.Value) }
    [IO.File]::WriteAllText($EnvFile, $text, [Text.UTF8Encoding]::new($false))
    Write-Host "Created private RED_Ultimate/.env"
} else {
    Write-Host "Using existing RED_Ultimate/.env (secrets are not overwritten)."
}

$envText = Get-Content $EnvFile -Raw
$envText = $envText.Replace('192.168.0.244', $ServerIp)
$envText = [regex]::Replace($envText, '(?m)^RED_HTTP_PORT=.*$', "RED_HTTP_PORT=$HttpPort")
$originMatch = [regex]::Match($envText, '(?m)^ALLOWED_ORIGINS=(.*)$')
$requiredOrigins = @("http://localhost:$HttpPort", "http://127.0.0.1:$HttpPort", "http://${ServerIp}:$HttpPort")
if ($originMatch.Success) {
    $origins = @($originMatch.Groups[1].Value.Split(',') + $requiredOrigins | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique)
    $envText = [regex]::Replace($envText, '(?m)^ALLOWED_ORIGINS=.*$', "ALLOWED_ORIGINS=$($origins -join ',')")
} else {
    $envText = $envText.TrimEnd() + "`r`nALLOWED_ORIGINS=$($requiredOrigins -join ',')`r`n"
}
[IO.File]::WriteAllText($EnvFile, $envText, [Text.UTF8Encoding]::new($false))
Write-Host "Local HTTP endpoint: http://${ServerIp}:$HttpPort"

$Secrets = Join-Path $Root "secrets"
$PrivateKey = Join-Path $Secrets "red_identity_private_key.pem"
$PublicKey = Join-Path $Secrets "red_identity_public_key.pem"
if (-not (Test-Path $PrivateKey)) {
    New-Item -ItemType Directory -Force $Secrets | Out-Null
    $openssl = Get-Command openssl -ErrorAction SilentlyContinue
    if ($openssl) {
        & $openssl.Source genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out $PrivateKey
        & $openssl.Source pkey -in $PrivateKey -pubout -out $PublicKey
    } else {
        & docker run --rm --volume "${Secrets}:/keys" alpine:3.20 sh -ec "apk add --no-cache openssl >/dev/null; umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out /keys/red_identity_private_key.pem; openssl pkey -in /keys/red_identity_private_key.pem -pubout -out /keys/red_identity_public_key.pem"
    }
    if (-not (Test-Path $PublicKey)) { throw "Identity authority files were not created" }
}

Push-Location $Root
try {
    & docker compose --env-file $EnvFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose validation failed" }
    & docker compose --env-file $EnvFile build
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose build failed" }
    & docker compose --env-file $EnvFile up -d
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose startup failed" }

    $healthy = $false
    foreach ($attempt in 1..60) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$HttpPort/health" -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { $healthy = $true; break }
        } catch { }
        Start-Sleep -Seconds 3
    }
    if (-not $healthy) {
        & docker compose --env-file $EnvFile ps
        & docker compose --env-file $EnvFile logs --tail=120 backend
        throw "Backend did not become healthy"
    }
    Write-Host "PASS  http://${ServerIp}:$HttpPort/health" -ForegroundColor Green
} finally { Pop-Location }

if ($BuildAndroid) {
    Push-Location $RepoRoot
    try {
        $Artifacts = Join-Path $RepoRoot "local-artifacts"
        New-Item -ItemType Directory -Force $Artifacts | Out-Null
        & docker build --file Dockerfile --target android-artifact --build-arg "RED_SERVER_URL=http://${ServerIp}:$HttpPort" --output "type=local,dest=$Artifacts" .
        if ($LASTEXITCODE -ne 0) { throw "Verified Android artifact build failed" }
        if (-not (Test-Path (Join-Path $Artifacts "red-app-debug.apk"))) { throw "Android build finished without an APK" }
    } finally { Pop-Location }
}

Write-Host "RED local first run is ready: http://${ServerIp}:$HttpPort/"
