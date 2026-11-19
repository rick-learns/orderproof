#!/usr/bin/env bash
# Packages labeled, checksummed APKs into dist/ from a clean checkout:
#   dist/apk/EmberKitchen-corrected-<version>-<sha>.apk
#   dist/apk/EmberKitchen-FAULTY-DO-NOT-SHIP-<version>-<sha>.apk
#   dist/apk/OrderProof-e2e-<sha>.apk           (instrumentation that drives either candidate)
#   dist/apk/SHA256SUMS
# Optional: --reports <run-dir>... copies executed verifier runs into dist/reports/.
# Both app APKs are debug-signed: they are test candidates, not store releases.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

reports=()
allow_dirty=0
while [ $# -gt 0 ]; do
  case "$1" in
    --allow-dirty) allow_dirty=1; shift ;;
    --reports) shift; while [ $# -gt 0 ] && [[ "$1" != --* ]]; do reports+=("$1"); shift; done ;;
    *) echo "unknown option $1" >&2; exit 64 ;;
  esac
done
if [ -n "$(git status --porcelain)" ] && [ $allow_dirty -eq 0 ]; then
  echo "working tree is dirty; commit first (or pass --allow-dirty for a throwaway build)" >&2
  exit 1
fi

sha=$(git rev-parse --short=12 HEAD)
version=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
./gradlew -q :app:assembleCorrectedDebug :app:assembleFaultyDebug :e2e:assembleDebug

rm -rf dist/apk && mkdir -p dist/apk
cp app/build/outputs/apk/corrected/debug/app-corrected-debug.apk "dist/apk/EmberKitchen-corrected-$version-$sha.apk"
cp app/build/outputs/apk/faulty/debug/app-faulty-debug.apk "dist/apk/EmberKitchen-FAULTY-DO-NOT-SHIP-$version-$sha.apk"
cp e2e/build/outputs/apk/debug/e2e-debug.apk "dist/apk/OrderProof-e2e-$sha.apk"
(cd dist/apk && sha256sum -- *.apk > SHA256SUMS)

for run in "${reports[@]}"; do
  mkdir -p dist/reports
  rm -rf "dist/reports/$(basename "$run")"
  # The backend folder holds the run's database and server log; the token was deleted after the run.
  cp -r "$run" "dist/reports/$(basename "$run")"
done
cat dist/apk/SHA256SUMS
