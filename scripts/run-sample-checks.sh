#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PREFS_DIR="$ROOT_DIR/.opendoja/prefs"
mkdir -p "$PREFS_DIR"

"$ROOT_DIR/scripts/build.sh" >/dev/null

run_sample() {
  local name="$1"
  local jar_path="$2"
  local jam_path="$3"

  set +e
  timeout 8s java \
    -Djava.awt.headless=true \
    -Djava.util.prefs.userRoot="$PREFS_DIR" \
    -cp "$ROOT_DIR/out/classes:$jar_path" \
    opendoja.host.JamLauncher \
    "$jam_path"
  local code=$?
  set -e

  if [[ "$code" -eq 0 ]]; then
    echo "$name: exited before timeout"
    return 1
  fi
  if [[ "$code" -ne 124 ]]; then
    echo "$name: failed with exit code $code"
    return "$code"
  fi
  echo "$name: running after 8s"
}

run_sample \
  "Nose Hair Master 2" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Nose_Hair_Master_2_doja/Offline/bin/ENGLISH_PATCH__Nose_Hair_Master_2.jar" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Nose_Hair_Master_2_doja/Offline/bin/ENGLISH_PATCH__Nose_Hair_Master_2.jam"

run_sample \
  "FFVII Snowboarding" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Final_Fantasy_VII_Snowboarding_doja/Offline/bin/ENGLISH_PATCH__Final_Fantasy_VII_Snowboarding.jar" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Final_Fantasy_VII_Snowboarding_doja/Offline/bin/ENGLISH_PATCH__Final_Fantasy_VII_Snowboarding.jam"

run_sample \
  "Monster Hunter i for SH" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Monster_Hunter_i_for_SH_doja/bin/ENGLISH_PATCH__Monster_Hunter_i_for_SH.jar" \
  "$ROOT_DIR/resources/sample_games/ENGLISH_PATCH__Monster_Hunter_i_for_SH_doja/bin/ENGLISH_PATCH__Monster_Hunter_i_for_SH.jam"
