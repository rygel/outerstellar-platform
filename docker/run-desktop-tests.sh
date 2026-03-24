#!/bin/bash
set -e

CONTAINER_NAME="outerstellar-test-desktop-run"
IMAGE_NAME="outerstellar-test-desktop"
REPORT_DIR="platform-desktop/target/surefire-reports-docker"
BUILD_TIMEOUT=1200  # 20 minutes for first build (downloads Maven deps into image)
RUN_TIMEOUT=600     # 10 minutes for test execution (deps already in image layer)

# Use podman if available, otherwise docker
if command -v podman &>/dev/null; then
    RUNTIME=podman
elif command -v docker &>/dev/null; then
    RUNTIME=docker
else
    echo "ERROR: Neither podman nor docker found."
    exit 1
fi
echo "Using container runtime: $RUNTIME"

# Prevent Git Bash from mangling paths on Windows
export MSYS_NO_PATHCONV=1

# Find Maven settings.xml for GitHub Packages auth.
# Do NOT mount the full .m2/repository — Windows-to-Linux volume mounts hang Podman.
SETTINGS_FILE=""
if [ -f "$USERPROFILE/.m2/settings.xml" ]; then
    SETTINGS_FILE="$USERPROFILE/.m2/settings.xml"
elif [ -f "$HOME/.m2/settings.xml" ]; then
    SETTINGS_FILE="$HOME/.m2/settings.xml"
fi

MOUNT_ARGS=()
if [ -n "$SETTINGS_FILE" ]; then
    MOUNT_ARGS+=("-v" "$SETTINGS_FILE:/root/.m2/settings.xml:ro")
    echo "Mounting Maven settings from: $SETTINGS_FILE"
else
    echo "WARNING: No settings.xml found — GitHub Packages auth will fail inside container"
fi

echo "=== Building test image (timeout: ${BUILD_TIMEOUT}s) ==="
echo "    First build downloads Maven Central deps into image — subsequent builds use cache."
BUILD_EXIT=0
timeout "$BUILD_TIMEOUT" $RUNTIME build -t "$IMAGE_NAME" -f docker/Dockerfile.test-desktop . || BUILD_EXIT=$?

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
timeout "$RUN_TIMEOUT" $RUNTIME run --name "$CONTAINER_NAME" "${MOUNT_ARGS[@]}" "$IMAGE_NAME" || TEST_EXIT=$?

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
