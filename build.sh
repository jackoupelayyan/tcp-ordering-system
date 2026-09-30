#!/usr/bin/env bash
# Compiles all sources with plain javac (zero dependencies).
set -euo pipefail
cd "$(dirname "$0")"

JAVAC="${JAVAC:-javac}"
OUT="out"

mkdir -p "$OUT"
# --release 17 keeps the bytecode runnable on any modern JDK (17+).
# shellcheck disable=SC2046
"$JAVAC" --release 17 -d "$OUT" $(find src -name '*.java')

echo "Built OK -> $OUT/"
