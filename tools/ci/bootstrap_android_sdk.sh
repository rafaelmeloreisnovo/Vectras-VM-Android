#!/usr/bin/env bash
set -euo pipefail

if ! command -v sdkmanager >/dev/null 2>&1; then
  echo "[ERROR] sdkmanager not found in PATH"
  exit 3
fi

(yes 2>/dev/null || true) | sdkmanager --licenses >/dev/null
sdkmanager \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0" \
  "ndk;26.3.11579264" \
  "cmake;3.22.1"

echo "[OK] Android SDK bootstrap complete"
