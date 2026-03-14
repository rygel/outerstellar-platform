#!/bin/bash
set -e

CONTAINER_NAME="outerstellar-test-desktop-run"
IMAGE_NAME="outerstellar-test-desktop"
REPORT_DIR="desktop/target/surefire-reports-docker"

echo "=== Building test image ==="
docker build -t "$IMAGE_NAME" -f docker/Dockerfile.test-desktop .

echo "=== Running Swing tests inside Docker with Xvfb ==="
# Run and capture exit code without --rm so we can copy reports
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true
TEST_EXIT=0
docker run --name "$CONTAINER_NAME" "$IMAGE_NAME" || TEST_EXIT=$?

echo "=== Copying test reports to $REPORT_DIR ==="
mkdir -p "$REPORT_DIR"
docker cp "$CONTAINER_NAME:/app/desktop/target/surefire-reports/." "$REPORT_DIR/" 2>/dev/null || true

echo "=== Cleaning up container ==="
docker rm -f "$CONTAINER_NAME" 2>/dev/null || true

echo ""
echo "=== Test Reports ==="
if [ -d "$REPORT_DIR" ]; then
    # Show summary
    grep -h "Tests run:" "$REPORT_DIR"/*.txt 2>/dev/null | head -20
    echo ""
    # Show failures if any
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
    echo "No reports found."
fi

echo ""
echo "Reports saved to: $REPORT_DIR"
exit $TEST_EXIT
