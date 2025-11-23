param(
    [string]$Version = "latest"
)

$ErrorActionPreference = "Stop"

$repo       = "tomoscorbin/blueprint"
$binaryName = "bp"

Write-Host "Installing $binaryName for Windows..."

# 1. Determine OS architecture
$osArch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
Write-Host "Detected OS architecture: $osArch"

switch ($osArch.ToString()) {
    "X64" {
        $arch = "amd64"
    }
    "Arm64" {
        # Only keep this if/when you actually publish a windows-arm64 binary
        $arch = "arm64"
    }
    default {
        throw "Unsupported Windows architecture: $osArch. Currently supported: X64 (and Arm64 if available)."
    }
}

# 2. Resolve release
if ($Version -eq "latest") {
    Write-Host "Resolving latest release from GitHub..."
    $release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest"
} else {
    if ($Version -notmatch '^v') { $Version = "v$Version" }
    Write-Host "Resolving release $Version from GitHub..."
    $release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/tags/$Version"
}

$assetName = "$binaryName-windows-$arch.exe"
$asset     = $release.assets | Where-Object { $_.name -eq $assetName }

if (-not $asset) {
    throw "Could not find asset '$assetName' in release '$($release.tag_name)'."
}

# 3. Download to %LOCALAPPDATA%\bp\bp.exe
$targetDir = Join-Path $env:LOCALAPPDATA "bp"
$target    = Join-Path $targetDir "bp.exe"

Write-Host "Downloading $assetName ..."
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
Invoke-WebRequest $asset.browser_download_url -OutFile $target

Write-Host ""
Write-Host "Installed bp to: $target"
Write-Host ""
Write-Host "Add this directory to your user PATH if it's not already there:"
Write-Host "  $targetDir"
Write-Host ""
Write-Host "Then open a new PowerShell and run:"
Write-Host "  bp --help"
