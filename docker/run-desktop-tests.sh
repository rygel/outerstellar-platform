#!/bin/bash
set -e

CONTAINER_NAME="outerstellar-test-desktop-run"
IMAGE_NAME="outerstellar-test-desktop"
REPORT_DIR="platform-desktop/target/surefire-reports-docker"
BUILD_TIMEOUT=1200  # 20 minutes for first build (downloads Maven deps into image)
RUN_TIMEOUT=600     # 10 minutes for test execution (deps already in image layer)

# Use the Podman-native API. The Docker compatibility pipe is unreliable on this machine.
if command -v podman &>/dev/null; then
    RUNTIME=podman
elif command -v podman.exe &>/dev/null; then
    RUNTIME=podman.exe
else
    echo "ERROR: podman not found."
    exit 1
fi
echo "Using container runtime: $RUNTIME"

# Prevent Git Bash from mangling paths on Windows
export MSYS_NO_PATHCONV=1

# Mount Maven settings.xml when available so publish credentials and private mirrors still work.
# Do NOT mount the full .m2/repository — Windows-to-Linux volume mounts hang Podman.
SETTINGS_FILE=""
MOUNT_SOURCE=""
if [ "$RUNTIME" = "podman.exe" ] && command -v powershell.exe &>/dev/null; then
    WINDOWS_SETTINGS_FILE="$(
        powershell.exe -NoProfile -Command "[IO.Path]::Combine(\$env:USERPROFILE, '.m2', 'settings.xml')" \
            | tr -d '\r'
    )"
    if powershell.exe -NoProfile -Command "exit ([int] -not (Test-Path -LiteralPath '$WINDOWS_SETTINGS_FILE'))"; then
        SETTINGS_FILE="$WINDOWS_SETTINGS_FILE"
        MOUNT_SOURCE="$WINDOWS_SETTINGS_FILE"
    fi
elif command -v cygpath &>/dev/null && [ -n "${USERPROFILE:-}" ] && [ -f "$(cygpath -u "$USERPROFILE")/.m2/settings.xml" ]; then
    SETTINGS_FILE="$(cygpath -u "$USERPROFILE")/.m2/settings.xml"
elif [ -f "$HOME/.m2/settings.xml" ]; then
    SETTINGS_FILE="$HOME/.m2/settings.xml"
fi

MOUNT_ARGS=()
if [ -n "$SETTINGS_FILE" ]; then
    MOUNT_SOURCE="${MOUNT_SOURCE:-$SETTINGS_FILE}"
    if [ "$RUNTIME" = "podman.exe" ] && command -v cygpath &>/dev/null; then
        MOUNT_SOURCE="$(cygpath -w "$SETTINGS_FILE")"
    fi
    MOUNT_ARGS+=("-v" "$MOUNT_SOURCE:/root/.m2/settings.xml:ro")
    echo "Mounting Maven settings from: $SETTINGS_FILE"
else
    echo "No settings.xml found; continuing with public Maven repositories only"
fi

CONTAINER_ARGS=(
    "--network" "host"
    "-v" "/var/run/docker.sock:/var/run/docker.sock"
    "-e" "DOCKER_HOST=unix:///var/run/docker.sock"
)

echo "=== Building test image (timeout: ${BUILD_TIMEOUT}s) ==="
echo "    First build downloads Maven Central deps into image — subsequent builds use cache."
BUILD_EXIT=0
timeout "$BUILD_TIMEOUT" $RUNTIME build \
    -t "$IMAGE_NAME" \
    -f docker/Dockerfile.build \
    --target desktop-test \
    . || BUILD_EXIT=$?

if [ "$BUILD_EXIT" -eq 124 ]; then
    echo "ERROR: Image build timed out after ${BUILD_TIMEOUT}s"
    exit 1
elif [ "$BUILD_EXIT" -ne 0 ]; then
    echo "ERROR: Image build failed (exit $BUILD_EXIT)"
    exit "$BUILD_EXIT"
fi

echo "=== Running Swing tests (timeout: ${RUN_TIMEOUT}s) ==="
$RUNTIME rm -f "$CONTAINER_NAME" 2>/dev/null || true

TEST_EXIT=0
timeout "$RUN_TIMEOUT" $RUNTIME run --name "$CONTAINER_NAME" "${MOUNT_ARGS[@]}" "${CONTAINER_ARGS[@]}" "$IMAGE_NAME" || TEST_EXIT=$?

if [ "$TEST_EXIT" -eq 124 ]; then
    echo "ERROR: Tests timed out after ${RUN_TIMEOUT}s"
    $RUNTIME stop "$CONTAINER_NAME" 2>/dev/null || true
fi

echo "=== Copying test reports to $REPORT_DIR ==="
mkdir -p "$REPORT_DIR"
$RUNTIME cp "$CONTAINER_NAME:/app/platform-desktop/target/surefire-reports/." "$REPORT_DIR/" 2>/dev/null || true

echo "=== Cleaning up ==="
$RUNTIME rm -f "$CONTAINER_NAME" 2>/dev/null || true

echo ""
echo "=== Test Reports ==="
if [ -d "$REPORT_DIR" ] && ls "$REPORT_DIR"/*.txt &>/dev/null; then
    grep -h "Tests run:" "$REPORT_DIR"/*.txt 2>/dev/null | head -20
    echo ""
    FAILURES=$(grep -l "FAILURE\!\|ERROR\!" "$REPORT_DIR"/*.txt 2>/dev/null || true)
    if [ -n "$FAILURES" ]; then
        echo "=== FAILED TESTS ==="
        grep -h "FAILURE\!\|ERROR\!" $FAILURES
        echo ""
        echo "Full reports in: $REPORT_DIR"
    else
        echo "All tests passed."
    fi
else
    echo "No reports found — check container logs above for errors."
fi

echo ""
echo "Reports saved to: $REPORT_DIR"
exit $TEST_EXIT
