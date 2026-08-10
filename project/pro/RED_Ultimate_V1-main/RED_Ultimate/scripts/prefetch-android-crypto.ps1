$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Repository = Join-Path $Root "local-maven\org\signal"
$Base = "https://maven-central.storage-download.googleapis.com/maven2/org/signal"

$Artifacts = @(
    @{ Relative = "libsignal-android\0.86.5\libsignal-android-0.86.5.aar"; Sha256 = "771e9a188fa30d96cef5433638e7c9ec00975c7afb64f94cdff97281330a0b93" },
    @{ Relative = "libsignal-android\0.86.5\libsignal-android-0.86.5.module"; Sha256 = "87cadeb64ed5b92f6b27e48bf6c6c65141403a2516947b9fbca26a3619b0e04c" },
    @{ Relative = "libsignal-client\0.86.5\libsignal-client-0.86.5.jar"; Sha256 = "d300c272dae155b21ae8988f51b6b493001cd2d83f5d2f9ae15e743c7ff971bd" },
    @{ Relative = "libsignal-client\0.86.5\libsignal-client-0.86.5.module"; Sha256 = "72f97810822e465e6316df9a5a00ffe5e562454282cf3e578e3d641f22dfd3d4" }
)

function Test-Hash([string]$Path, [string]$Expected) {
    if (-not (Test-Path $Path)) { return $false }
    return (Get-FileHash -Algorithm SHA256 $Path).Hash.ToLowerInvariant() -eq $Expected
}

foreach ($artifact in $Artifacts) {
    $relativeUrl = $artifact.Relative.Replace('\', '/')
    $destination = Join-Path $Repository $artifact.Relative
    New-Item -ItemType Directory -Force (Split-Path -Parent $destination) | Out-Null
    if (Test-Hash $destination $artifact.Sha256) {
        Write-Host "Verified cache hit: $relativeUrl"
        continue
    }
    Remove-Item $destination -Force -ErrorAction SilentlyContinue
    $url = "$Base/$relativeUrl"
    Write-Host "Downloading with retries: $relativeUrl"
    if (Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue) {
        try { Start-BitsTransfer -Source $url -Destination $destination -DisplayName "RED verified crypto dependency" }
        catch { Remove-Item $destination -Force -ErrorAction SilentlyContinue; Write-Warning "BITS failed; falling back to curl.exe" }
    }
    if (-not (Test-Path $destination)) {
        & curl.exe --fail --location --retry 20 --retry-all-errors --retry-delay 5 --connect-timeout 30 --continue-at - --output $destination $url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $relativeUrl" }
    }
    if (-not (Test-Hash $destination $artifact.Sha256)) {
        Remove-Item $destination -Force -ErrorAction SilentlyContinue
        throw "SHA-256 verification failed for $relativeUrl"
    }
    Write-Host "SHA-256 PASS: $relativeUrl"
}

Write-Host "Verified libsignal cache ready under RED_Ultimate/local-maven."
