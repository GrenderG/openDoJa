#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/out/classes"

mkdir -p "$OUT_DIR"
javac --release 21 -d "$OUT_DIR" $(find "$ROOT_DIR/src/main/java" -name '*.java' | sort)

if [ -d "$ROOT_DIR/src/main/resources" ]; then
  cp -R "$ROOT_DIR/src/main/resources/." "$OUT_DIR/"
fi

echo "Built classes into $OUT_DIR"
