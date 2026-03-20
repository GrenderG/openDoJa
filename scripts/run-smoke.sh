#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT_DIR/scripts/build.sh"
java -Djava.awt.headless=true -cp "$ROOT_DIR/out/classes" opendoja.demo.RuntimeSmoke
