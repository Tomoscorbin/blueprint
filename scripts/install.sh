#!/usr/bin/env bash
set -euo pipefail

REPO="tomoscorbin/blueprint"
BINARY_NAME="bp"

echo "Installing blueprint ..."

# 1. Detect OS
uname_os="$(uname -s)"
case "$uname_os" in
  Linux)
    os="linux"
    ;;
  Darwin)
    os="macos"
    ;;
  MINGW* | MSYS* | CYGWIN* | Windows_NT)
    os="windows"
    ;;
  *)
    echo "Unsupported OS: $uname_os" >&2
    exit 1
    ;;
esac

# 2. Detect arch
uname_arch="$(uname -m)"
case "$uname_arch" in
  x86_64 | amd64)
    arch="amd64"
    ;;
  arm64 | aarch64)
    arch="arm64"
    ;;
  *)
    echo "Unsupported architecture: $uname_arch" >&2
    echo "Currently published builds: linux-amd64, linux-arm64, macos-arm64, windows-amd64." >&2
    exit 1
    ;;
esac

# 3. Validate supported OS/arch combo
case "${os}-${arch}" in
  linux-amd64 | linux-arm64 | macos-arm64 | windows-amd64)
    # ok
    ;;
  *)
    echo "No prebuilt binary available for ${os}-${arch}." >&2
    echo "Currently published builds: linux-amd64, linux-arm64, macos-arm64, windows-amd64." >&2
    exit 1
    ;;
esac

ASSET_NAME="${BINARY_NAME}-${os}-${arch}"
# Windows asset has .exe suffix in the release
if [ "$os" = "windows" ]; then
  ASSET_NAME="${ASSET_NAME}.exe"
fi

# 4. Resolve version/tag
VERSION="${BP_VERSION:-latest}"

if [ "$VERSION" = "latest" ]; then
  echo "Resolving latest release tag from GitHub..."
  TAG=$(
    curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" |
      grep '"tag_name"' |
      head -n1 |
      cut -d '"' -f4
  )
else
  # Accept both "0.1.0" and "v0.1.0"
  case "$VERSION" in
    v*) TAG="$VERSION" ;;
    *)  TAG="v${VERSION}" ;;
  esac
fi

if [ -z "${TAG:-}" ]; then
  echo "Failed to resolve release tag" >&2
  exit 1
fi

echo "Using release tag: ${TAG}"
DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG}/${ASSET_NAME}"

# 5. Download to temp file
TMP_BIN="$(mktemp)"
echo "Downloading ${DOWNLOAD_URL} ..."
curl -fsSL "${DOWNLOAD_URL}" -o "${TMP_BIN}"
chmod +x "${TMP_BIN}"

# --- Windows install path ---------------------------------------------------
if [ "$os" = "windows" ]; then
  # On Windows we always install to a user-local directory.
  # BP_BIN_DIR can override, otherwise default to $HOME/.local/bin.
  BIN_DIR="${BP_BIN_DIR:-$HOME/.local/bin}"
  mkdir -p "$BIN_DIR"

  TARGET="${BIN_DIR}/${BINARY_NAME}.exe"
  mv "${TMP_BIN}" "${TARGET}"

  echo
  echo "Installed ${BINARY_NAME} to ${TARGET}"

  # Try to show a Windows-style path for PATH instructions
  if command -v cygpath >/dev/null 2>&1; then
    WIN_DIR="$(cygpath -w "$BIN_DIR")"
    echo
    echo "To use 'bp' from PowerShell/cmd, add this directory to your Windows PATH:"
    echo "  $WIN_DIR"
  else
    echo
    echo "Make sure the directory containing bp.exe is on your PATH."
  fi

  exit 0
fi

# --- Unix install path (Linux / macOS) --------------------------------------

USER_BIN="$HOME/.local/bin"
SYSTEM_BIN="/usr/local/bin"

if [ -n "${BP_BIN_DIR:-}" ]; then
  # Caller overrides everything
  BIN_DIR="${BP_BIN_DIR}"
else
  if [ "$os" = "windows" ]; then
    # On Windows, default to a per-user dir under LOCALAPPDATA
    if [ -n "${LOCALAPPDATA:-}" ]; then
      BIN_DIR="${LOCALAPPDATA}/bp"
    else
      # Fallback if LOCALAPPDATA is somehow missing
      BIN_DIR="${HOME}/.bp"
    fi
  else
    # Unix-y behaviour: prefer ~/.local/bin if it's on PATH, else /usr/local/bin
    USER_BIN="$HOME/.local/bin"
    SYSTEM_BIN="/usr/local/bin"

    if echo ":$PATH:" | tr ':' '\n' | grep -qx "${USER_BIN}"; then
      BIN_DIR="${USER_BIN}"
    else
      BIN_DIR="${SYSTEM_BIN}"
    fi
  fi
fi

mkdir -p "${BIN_DIR}"

TARGET="${BIN_DIR}/${BINARY_NAME}"

# 7. Move into place (sudo only if needed)
if [ -w "${BIN_DIR}" ]; then
  mv "${TMP_BIN}" "${TARGET}"
else
  if [ "$os" != "windows" ] && command -v sudo >/dev/null 2>&1; then
    echo "Installing to ${TARGET} (may require sudo)..."
    sudo mv "${TMP_BIN}" "${TARGET}"
  else
    echo "Directory ${BIN_DIR} is not writable." >&2
    if [ "$os" = "windows" ]; then
      echo "Re-run with BP_BIN_DIR pointing to a writable directory (e.g. %LOCALAPPDATA%\\bp)." >&2
    else
      echo "Re-run with BP_BIN_DIR pointing to a writable directory (e.g. \$HOME/.local/bin)." >&2
    fi
    exit 1
  fi
fi

echo
echo "Installed ${BINARY_NAME} to ${TARGET}"
