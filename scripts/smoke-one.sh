#!/usr/bin/env bash
# Dev helper: rebuild + reinstall the e2e APK and run one scenario. Usage: scripts/smoke-one.sh <Class#method> [package]
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
./gradlew -q :e2e:assembleDebug
adb install -r -t e2e/build/outputs/apk/debug/e2e-debug.apk >/dev/null
adb shell cmd package compile -m speed -f dev.orderproof.e2e >/dev/null
adb logcat -c
adb shell am instrument -w -r -e targetPackage "${2:-dev.orderproof.emberkitchen}" -e controlToken "$(cat "${OP_TOKEN_FILE:-/tmp/op-run/control.token}")" \
  -e class "dev.orderproof.e2e.scenarios.$1" dev.orderproof.e2e/androidx.test.runner.AndroidJUnitRunner | grep -E "INSTRUMENTATION_CODE|^OK|stack=" | head -5 || true
adb logcat -d -s OrderProofE2E:I OrderProof:I | sed 's/.*OrderProof[E2]*: //' | cut -c1-220
