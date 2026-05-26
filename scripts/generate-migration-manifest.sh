#!/usr/bin/env bash
set -euo pipefail

MIGRATION_DIR="$1"
OUTPUT_FILE="$2"

if [ ! -d "$MIGRATION_DIR" ]; then
    echo "Error: Migration directory not found: $MIGRATION_DIR" >&2
    exit 1
fi

PARENT_DIR=$(dirname "$OUTPUT_FILE")
mkdir -p "$PARENT_DIR"

ls -1 "$MIGRATION_DIR"/V*.sql 2>/dev/null | xargs -I{} basename {} .sql | sort > "$OUTPUT_FILE"

COUNT=$(wc -l < "$OUTPUT_FILE")
echo "Generated manifest with $COUNT migrations -> $OUTPUT_FILE"
