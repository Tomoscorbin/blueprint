#!/usr/bin/env bash
set -euo pipefail

REPO="tomoscorbin/blueprint"
BINARY_NAME="bp"

uname_os="$(uname -s)"
case "$uname_os" in
  Linux)  os="linux" ;;
  Darwin) os="macos" ;;
  *) echo "Unsupported OS: $uname_os" >&2; exit 1 ;;
esac

arch="amd64"  # you can refine later

asset="${BINARY_NAME}-${os}-${arch}.tmp"  # temporary name

version="${BLUEPRINT_VERSION:-latest}"

if [ "$version" = "latest" ]; then
  tag=$(curl -fsSL "https://api.github.com/repos/${REPO}/releases/latest" |
        grep '"tag_name"' | head -n1 | cut -d '"' -f4)
else
  tag="v${version}"
fi

url="https://github.com/${REPO}/releases/download/${tag}/${BINARY_NAME}-${os}-${arch}"

echo "Downloading ${url}..."
curl -fsSL "$url" -o "$asset"

chmod +x "$asset"

install_dir="${BLUEPRINT_INSTALL_DIR:-/usr/local/bin}"
echo "Installing to ${install_dir}/${BINARY_NAME}"

if [ ! -w "$install_dir" ]; then
  sudo mv "$asset" "${install_dir}/${BINARY_NAME}"
else
  mv "$asset" "${install_dir}/${BINARY_NAME}"
fi

echo "Installed ${BINARY_NAME} to ${install_dir}."
echo "Run: ${BINARY_NAME} --help"
