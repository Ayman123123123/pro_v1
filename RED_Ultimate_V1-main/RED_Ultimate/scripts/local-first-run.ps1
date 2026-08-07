param(
    [Parameter(Mandatory = $true)][string]$ServerIp,
    [ValidateRange(1024, 65535)][int]$HttpPort = 8088,
    [switch]$BuildAndroid
)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent $Root
$EnvFile = Join-Path $Root ".env"

if ($ServerIp -notmatch '^([0-9]{1,3}\.){3}[0-9]{1,3}$') { throw "ServerIp must be a local IPv4 address" }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker Desktop is required" }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not running" }
& docker compose version *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Compose v2 is required" }

$DockerMemoryBytes = [double](& docker info --format '{{.MemTotal}}')
if ($LASTEXITCODE -ne 0) { throw "Unable to read Docker memory limit" }
$DockerMemoryGiB = $DockerMemoryBytes / 1GB
if ($DockerMemoryGiB -lt 5.5) {
    throw ("Docker has only {0:N1} GiB available. RED needs at least 5.5 GiB available after VM overhead (6 GiB configured; 8 GiB recommended), then restart Docker Desktop and retry." -f $DockerMemoryGiB)
}
Write-Host ("Docker memory preflight: {0:N1} GiB PASS" -f $DockerMemoryGiB)

function New-Hex([int]$Bytes) {
    $buffer = New-Object byte[] $Bytes
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buffer) } finally { $rng.Dispose() }
    return (-join ($buffer | ForEach-Object { $_.ToString("x2") }))
}

function Wait-ContainerReady([string]$Name) {
    foreach ($attempt in 1..30) {
        $state = (& docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $Name 2>$null)
        if ($LASTEXITCODE -eq 0 -and $state) {
            $parts = $state.Trim().Split('|')
            $runtime = $parts[0]
            $health = $parts[1]
            if ($runtime -eq 'running' -and ($health -eq 'healthy' -or $health -eq 'none')) {
                Write-Host "$Name readiness: PASS ($runtime/$health)"
                return
            }
            if ($runtime -eq 'exited' -or $runtime -eq 'dead' -or $runtime -eq 'restarting') {
                & docker logs --tail 80 $Name
                throw "$Name failed readiness: $runtime/$health"
            }
        }
        Start-Sleep -Seconds 3
    }
    & docker inspect $Name --format '{{json .State}}'
    throw "$Name did not become ready"
}

$OpenSslExe = $null
$OpenSslCommand = Get-Command openssl -ErrorAction SilentlyContinue
if ($OpenSslCommand) { $OpenSslExe = $OpenSslCommand.Source }
if (-not $OpenSslExe) {
    $OpenSslCandidates = @(
        (Join-Path $env:ProgramFiles "Git\usr\bin\openssl.exe"),
        (Join-Path $env:ProgramFiles "Git\mingw64\bin\openssl.exe")
    )
    $OpenSslExe = $OpenSslCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}

if (-not (Test-Path $EnvFile)) {
    $text = Get-Content (Join-Path $Root ".env.example") -Raw
    $replacements = @{
        "replace_with_a_long_random_database_password" = (New-Hex 32)
        "replace_with_a_long_random_mongodb_password" = (New-Hex 32)
        "replace_with_a_long_random_minio_password" = (New-Hex 32)
        "replace_with_a_long_random_redis_password" = (New-Hex 32)
        "replace_with_a_long_random_asterisk_password" = (New-Hex 32)
        "replace_with_a_long_random_turn_secret" = (New-Hex 32)
        "replace_with_at_least_32_random_characters" = (New-Hex 48)
        "replace_with_at_least_14_random_characters" = (New-Hex 20)
        "replace_with_the_gateway_password" = (New-Hex 24)
        "192.168.1.50" = $ServerIp
    }
    foreach ($entry in $replacements.GetEnumerator()) { $text = $text.Replace($entry.Key, $entry.Value) }
    [IO.File]::WriteAllText($EnvFile, $text, [Text.UTF8Encoding]::new($false))
    Write-Host "Created private RED_Ultimate/.env"
} else {
    Write-Host "Using existing RED_Ultimate/.env (secrets are not overwritten)."
}

# Port 80 is commonly reserved by HTTP.sys/IIS on Windows. Keep the internal Nginx port at 80,
# but expose a configurable unprivileged host port and ensure browser CORS includes that origin.
$envText = Get-Content $EnvFile -Raw
if ($envText -match '(?m)^RED_HTTP_PORT=.*$') {
    $envText = [regex]::Replace($envText, '(?m)^RED_HTTP_PORT=.*$', "RED_HTTP_PORT=$HttpPort")
} else {
    $envText = $envText.TrimEnd() + "`r`nRED_HTTP_PORT=$HttpPort`r`n"
}
$requiredOrigins = @("http://localhost:$HttpPort", "http://127.0.0.1:$HttpPort", "http://${ServerIp}:$HttpPort")
$originMatch = [regex]::Match($envText, '(?m)^ALLOWED_ORIGINS=(.*)$')
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
    if ($OpenSslExe) {
        Write-Host "Generating identity authority with local OpenSSL: $OpenSslExe"
        & $OpenSslExe genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out $PrivateKey
        if ($LASTEXITCODE -ne 0) { throw "Identity private key generation failed" }
        & $OpenSslExe pkey -in $PrivateKey -pubout -out $PublicKey
        if ($LASTEXITCODE -ne 0) { throw "Identity public key generation failed" }
    } else {
        Write-Host "Host OpenSSL not found; generating identity authority inside an ephemeral Alpine container."
        & docker run --rm --volume "${Secrets}:/keys" alpine:3.20 sh -ec "apk add --no-cache openssl >/dev/null; umask 077; openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out /keys/red_identity_private_key.pem; openssl pkey -in /keys/red_identity_private_key.pem -pubout -out /keys/red_identity_public_key.pem"
        if ($LASTEXITCODE -ne 0) { throw "Containerized identity key generation failed" }
    }
    if (-not (Test-Path $PrivateKey) -or -not (Test-Path $PublicKey)) { throw "Identity authority files were not created" }
    Write-Host "Created local identity authority keys; back up secrets securely."
} else {
    Write-Host "Using existing identity authority keys (not overwritten)."
}

Push-Location $Root
try {
    & docker compose --env-file $EnvFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose validation failed" }
    Write-Host "Docker Compose configuration: PASS"
    & docker compose --env-file $EnvFile build
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose build failed" }
    & docker compose --env-file $EnvFile up -d
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose startup failed" }
    # Nginx resolves Docker service names when loading its config. Recreate/restart after upstream
    # containers so cached addresses cannot point at a replaced backend or SFU container.
    & docker compose --env-file $EnvFile restart nginx
    if ($LASTEXITCODE -ne 0) { throw "Nginx restart after upstream startup failed" }
    Start-Sleep -Seconds 3

    Write-Host -NoNewline "Waiting for backend"
    $healthy = $false
    foreach ($attempt in 1..60) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$HttpPort/health" -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { $healthy = $true; break }
        } catch { }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 3
    }
    if (-not $healthy) {
        & docker compose --env-file $EnvFile ps
        & docker compose --env-file $EnvFile logs --tail=120 backend
        throw "Backend did not become healthy"
    }
    Write-Host " PASS"
    Write-Host -NoNewline "Waiting for SFU"
    $sfuHealthy = $false
    foreach ($attempt in 1..60) {
        try {
            $sfu = Invoke-WebRequest -Uri "http://127.0.0.1:$HttpPort/sfu-health" -UseBasicParsing -TimeoutSec 3
            if ($sfu.StatusCode -eq 200) { $sfuHealthy = $true; break }
        } catch { }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 3
    }
    if (-not $sfuHealthy) {
        & docker logs --tail 100 red-media-sfu
        throw "SFU did not become healthy through Nginx"
    }
    Write-Host " PASS"
    Wait-ContainerReady "red-admin-ui"
    Wait-ContainerReady "red-pstn-gateway"
} finally { Pop-Location }

if ($BuildAndroid) {
    Push-Location $RepoRoot
    try {
        & (Join-Path $PSScriptRoot "prefetch-android-crypto.ps1")
        if (-not $?) { throw "Verified libsignal prefetch failed" }
        $Artifacts = Join-Path $RepoRoot "local-artifacts"
        New-Item -ItemType Directory -Force $Artifacts | Out-Null
        & docker build --file Dockerfile --target android-artifact --build-arg "RED_SERVER_URL=http://${ServerIp}:$HttpPort" --output "type=local,dest=$Artifacts" .
        if ($LASTEXITCODE -ne 0) { throw "Verified Android artifact build failed" }
        if (-not (Test-Path (Join-Path $Artifacts "red-app-debug.apk"))) { throw "Android build finished without an APK" }
        Write-Host "Verified APK saved under local-artifacts/red-app-debug.apk."
    } finally { Pop-Location }
}

Write-Host ""
Write-Host "RED local first run is ready: http://${ServerIp}:$HttpPort/"
Write-Host "Health endpoint: http://${ServerIp}:$HttpPort/health"
Write-Host "Admin credentials remain only in RED_Ultimate/.env."
