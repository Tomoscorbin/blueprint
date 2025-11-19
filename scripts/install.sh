#!/usr/bin/env bash
set -euo pipefail

REPO="tomoscorbin/blueprint"
BINARY_NAME="bp"

echo "Installing ${BINARY_NAME} ..."

# 1. Detect OS
uname_os="$(uname -s)"
case "$uname_os" in
  Linux)  os="linux" ;;
  Darwin) os="macos" ;;
  *)
    echo "Unsupported OS: $uname_os" >&2
    exit 1
    ;;
esac

# 2. Detect arch (simple version: x86_64 only)
uname_arch="$(uname -m)"
case "$uname_arch" in
  x86_64|amd64) arch="amd64" ;;
  *)
    echo "Unsupported architecture: $uname_arch" >&2
    echo "Right now only x86_64/amd64 builds are published." >&2
    exit 1
    ;;
esac

ASSET_NAME="${BINARY_NAME}-${os}-${arch}"

# 3. Resolve version/tag
VERSION="${BP_VERSION:-latest}"

if [ "$VERSION" = "latest" ]; then
  echo "Resolving latest release tag from GitHub..."
  TAG=$(
    curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" \
      | grep '"tag_name"' \
      | head -n1 \
      | cut -d '"' -f4
  )
else
  TAG="v${VERSION}"
fi

if [ -z "${TAG:-}" ]; then
  echo "Failed to resolve release tag" >&2
  exit 1
fi

echo "Using release tag: ${TAG}"
DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG}/${ASSET_NAME}"

# 4. Download to temp file
TMP_BIN="$(mktemp)"
echo "Downloading ${DOWNLOAD_URL} ..."
curl -fsSL "${DOWNLOAD_URL}" -o "${TMP_BIN}"
chmod +x "${TMP_BIN}"

# 5. Choose install dir (prefer ~/.local/bin if on PATH)
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

TARGET="${BIN_DIR}/${BINARY_NAME}"

# 6. Move into place (sudo only if needed)
if [ -w "${BIN_DIR}" ]; then
  mv "${TMP_BIN}" "${TARGET}"
else
  echo "Installing to ${TARGET} (may require sudo)..."
  sudo mv "${TMP_BIN}" "${TARGET}"
fi

echo
echo "Installed ${BINARY_NAME} to ${TARGET}"
echo "Make sure ${BIN_DIR} is on your PATH."
echo "Try:"
echo "  ${BINARY_NAME} --help"
echo "  ${BINARY_NAME} new my-project"
