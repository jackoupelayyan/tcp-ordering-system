#!/usr/bin/env bash
# Starts the interactive ordering client.
# Usage: ./run-client.sh [host]   (port via $PORT, default 5000)
set -euo pipefail
cd "$(dirname "$0")"

[ -d out ] || ./build.sh

JAVA="${JAVA:-java}"
HOST="${1:-localhost}"
exec "$JAVA" -cp out com.jackoupelayyan.ordering.OrderClient "$HOST" "${PORT:-5000}"
