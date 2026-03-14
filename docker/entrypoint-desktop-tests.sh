#!/bin/bash
set -e

echo "=== Starting Xvfb ==="
Xvfb :99 -screen 0 1024x768x24 -nolisten tcp &
XVFB_PID=$!

echo "Waiting for Xvfb to accept connections..."
for i in $(seq 1 30); do
    DISPLAY=:99 xdpyinfo >/dev/null 2>&1 && echo "Xvfb ready (attempt $i)." && break
    if [ "$i" -eq 30 ]; then
        echo "ERROR: Xvfb did not become ready in 30 seconds"
        kill $XVFB_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

export DISPLAY=:99

echo "=== Running desktop tests ==="
exec mvn test -pl desktop -am \
    -Ddesktop.headless=false \
    -Denforcer.skip=true -Ddetekt.skip=true -Dspotbugs.skip=true \
    -Dcheckstyle.skip=true -Dpmd.skip=true -Dcpd.skip=true
