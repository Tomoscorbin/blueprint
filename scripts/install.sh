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

# 6. Choose install dir (prefer ~/.local/bin if on PATH)
USER_BIN="$HOME/.local/bin"
SYSTEM_BIN="/usr/local/bin"

if [ -n "${BP_BIN_DIR:-}" ]; then
  BIN_DIR="${BP_BIN_DIR}"
else
  if echo ":$PATH:" | tr ':' '\n' | grep -qx "${USER_BIN}"; then
    BIN_DIR="${USER_BIN}"
  else
    BIN_DIR="${SYSTEM_BIN}"
  fi
fi

mkdir -p "${BIN_DIR}"

# On Windows, install as bp.exe; elsewhere just bp
if [ "$os" = "windows" ]; then
  TARGET="${BIN_DIR}/${BINARY_NAME}.exe"
else
  TARGET="${BIN_DIR}/${BINARY_NAME}"
fi

# 7. Move into place (sudo only if needed)
if [ -w "${BIN_DIR}" ]; then
  mv "${TMP_BIN}" "${TARGET}"
else
  if command -v sudo >/dev/null 2>&1; then
    echo "Installing to ${TARGET} (may require sudo)..."
    sudo mv "${TMP_BIN}" "${TARGET}"
  else
    echo "Directory ${BIN_DIR} is not writable and sudo is not available." >&2
    echo "Re-run with BP_BIN_DIR pointing to a writable directory (e.g. \$HOME/.local/bin)." >&2
    exit 1
  fi
fi

echo
echo "Installed ${BINARY_NAME} to ${TARGET}"
