#!/usr/bin/env bash
# Everything that runs without an emulator. CI runs exactly this; run it locally before pushing.
# The emulator release gate (scripts/verify.sh) is a local-only stage: see docs/ci.md.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

scripts/check-defect-isolation.sh

./gradlew --no-daemon --stacktrace \
  :backend:test :oracle:test :verifier:test \
  :app:testCorrectedDebugUnitTest :app:testFaultyDebugUnitTest \
  :app:lintCorrectedDebug :app:lintFaultyDebug \
  :app:assembleCorrectedDebug :app:assembleFaultyDebug \
  :app:assembleCorrectedDebugAndroidTest :app:assembleFaultyDebugAndroidTest \
  :e2e:assembleDebug :backend:installDist :verifier:installDist

python3 -I - <<'PY'
import glob, re
total = failed = 0
for path in glob.glob("*/build/test-results/**/*.xml", recursive=True):
    head = open(path).read(600)
    m = re.search(r'tests="(\d+)".*?failures="(\d+)".*?errors="(\d+)"', head, re.S)
    if m:
        total += int(m[1]); failed += int(m[2]) + int(m[3])
print(f"JVM tests: {total} run, {failed} failed")
PY
