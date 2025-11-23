param(
    # "latest" or an explicit version like "0.1.0" or "v0.1.0"
    [string]$Version = "latest"
)

$ErrorActionPreference = "Stop"

$repo       = "tomoscorbin/blueprint"
$binaryName = "bp"

Write-Host "Installing $binaryName for Windows..."

# 1. Determine OS architecture using Win32_OperatingSystem (works on your machine)
$osInfo        = Get-CimInstance Win32_OperatingSystem
$osArchString  = $osInfo.OSArchitecture  # e.g. "64-bit" or "32-bit"
Write-Host "Detected OS architecture: $osArchString"

if ($osArchString -notlike "*64*") {
    throw "Unsupported Windows architecture: $osArchString. Currently only 64-bit Windows is supported."
}

# We know we're on 64-bit Windows
$arch = "amd64"

# 2. Prepare GitHub API headers
$headers = @{ "User-Agent" = "$binaryName-installer" }

# 3. Resolve release metadata from GitHub
if ($Version -eq "latest") {
    Write-Host "Resolving latest release from GitHub..."
    $release = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$repo/releases/latest" `
        -Headers $headers
} else {
    if ($Version -notmatch '^v') { $Version = "v$Version" }
    Write-Host "Resolving release $Version from GitHub..."
    $release = Invoke-RestMethod `
        -Uri "https://api.github.com/repos/$repo/releases/tags/$Version" `
        -Headers $headers
}

if (-not $release) {
    throw "Failed to fetch release metadata from GitHub."
}

if (-not $release.assets) {
    throw "Release '$($release.tag_name)' does not contain any assets."
}

# 4. Find the correct asset for this platform
$assetName = "$binaryName-windows-$arch.exe"
$asset     = $release.assets | Where-Object { $_.name -eq $assetName }

if (-not $asset) {
    $available = ($release.assets | Select-Object -ExpandProperty name) -join ", "
    throw "Could not find asset '$assetName' in release '$($release.tag_name)'. Available assets: $available"
}

# 5. Decide install directory
#    - Use BP_BIN_DIR if set
#    - Otherwise default to %LOCALAPPDATA%\bp
if (-not [string]::IsNullOrWhiteSpace($env:BP_BIN_DIR)) {
    $targetDir = $env:BP_BIN_DIR
} else {
    $targetDir = Join-Path $env:LOCALAPPDATA $binaryName
}

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
$target = Join-Path $targetDir "$binaryName.exe"

# 6. Download the binary
Write-Host "Downloading $assetName to $target ..."
Invoke-WebRequest `
    -Uri $asset.browser_download_url `
    -Headers $headers `
    -OutFile $target

Write-Host ""
Write-Host "Installed $binaryName to: $target"
Write-Host ""
Write-Host "If '$targetDir' is not already on your PATH, add it to your user PATH,"
Write-Host "then open a new PowerShell window and run:"
Write-Host "  bp --help"
