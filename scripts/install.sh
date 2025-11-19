#!/usr/bin/env bash
set -euo pipefail

# Repo coordinates
REPO="tomoscorbin/blueprint"

# Command name the user will type
BINARY_NAME="bp"

# Name of the jar asset attached to each release
JAR_NAME="blueprint-standalone.jar"

# Where to store the jar on disk
JAR_DIR="${BP_JAR_DIR:-$HOME/.local/share/blueprint}"

# Where to put the bp launcher script
DEFAULT_BIN_DIR="/usr/local/bin"
FALLBACK_BIN_DIR="$HOME/.local/bin"
BIN_DIR="${BP_BIN_DIR:-$DEFAULT_BIN_DIR}"

echo "Installing blueprintDE CLI (${BINARY_NAME})..."

# 1. Determine version/tag to install
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

# 2. Download the jar asset for that tag
DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG}/${JAR_NAME}"
TMP_JAR="$(mktemp)"

echo "Downloading ${DOWNLOAD_URL}..."
curl -fsSL "${DOWNLOAD_URL}" -o "${TMP_JAR}"

mkdir -p "${JAR_DIR}"
JAR_PATH="${JAR_DIR}/${JAR_NAME}"

mv "${TMP_JAR}" "${JAR_PATH}"

echo "Jar installed to ${JAR_PATH}"

# 3. Decide where to put the launcher script
if [ ! -d "${BIN_DIR}" ]; then
  echo "Bin dir ${BIN_DIR} does not exist."
  echo "Trying fallback ${FALLBACK_BIN_DIR}..."
  BIN_DIR="${FALLBACK_BIN_DIR}"
fi

mkdir -p "${BIN_DIR}"

LAUNCHER_PATH="${BIN_DIR}/${BINARY_NAME}"

# 4. Create the bp launcher script
cat > "${LAUNCHER_PATH}" <<EOF
#!/usr/bin/env bash
set -euo pipefail

JAR_PATH="${JAR_PATH}"

if ! command -v java >/dev/null 2>&1; then
  echo "Error: 'java' is not on PATH. Please install a JDK (e.g. Temurin 21) and try again." >&2
  exit 1
fi

exec java -jar "\${JAR_PATH}" "\$@"
EOF

chmod +x "${LAUNCHER_PATH}"

echo "Launcher installed to ${LAUNCHER_PATH}"

# 5. Final messages
echo
echo "Done."
echo "Make sure ${BIN_DIR} is on your PATH."
echo "Then run:"
echo "  ${BINARY_NAME} --help"
echo "  ${BINARY_NAME} new my-project"
