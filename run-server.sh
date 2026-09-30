#!/usr/bin/env bash
# Starts the ordering server. Port: first argument, else $PORT, else 5000.
set -euo pipefail
cd "$(dirname "$0")"

[ -d out ] || ./build.sh

JAVA="${JAVA:-java}"
PORT_ARG="${1:-${PORT:-5000}}"
exec "$JAVA" -cp out com.jackoupelayyan.ordering.OrderServer "$PORT_ARG"
