#!/usr/bin/env bash
# Runs the release gate for one candidate on one emulator configuration.
#
#   scripts/verify.sh --candidate corrected --avd prs-pixel --runs 3
#   scripts/verify.sh --candidate faulty    --avd prs-pixel --expect-failure DUPLICATE_ORDER
#   scripts/verify.sh --candidate corrected --avd op-small-api34 --config secondary
#
# Builds both APKs from the current checkout, boots (or reuses) the emulator, starts a fresh
# backend, runs every scenario, and writes runs/<timestamp>-<candidate>-<avd>/report.{json,html}.
# Exit code is the verifier's: 0 = gate met expectations, 1 = it did not, 3 = environment broken.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

candidate="" avd="prs-pixel" config="primary" runs=1 extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --candidate) candidate="$2"; shift 2 ;;
    --avd) avd="$2"; shift 2 ;;
    --config) config="$2"; shift 2 ;;
    --runs) runs="$2"; shift 2 ;;
    --expect-failure|--scenarios|--rerun-failures|--required-clean-runs) extra+=("$1" "$2"); shift 2 ;;
    *) echo "unknown option $1" >&2; exit 64 ;;
  esac
done
case "$candidate" in corrected|faulty) ;; *) echo "--candidate corrected|faulty is required" >&2; exit 64 ;; esac
Flavor="${candidate^}"

echo "== build ($candidate)"
./gradlew -q ":app:assemble${Flavor}Debug" :e2e:assembleDebug :backend:installDist :verifier:installDist

echo "== emulator ($avd)"
serial=$(scripts/emulator.sh start "$avd" | tail -1)
echo "serial $serial"

out="runs/$(date -u +%Y%m%dT%H%M%SZ)-$candidate-$avd"
backend_dir="$out/backend"
echo "== backend"
scripts/backend.sh start "$backend_dir"
trap 'scripts/backend.sh stop "$backend_dir"' EXIT

dirty=()
[ -n "$(git status --porcelain)" ] && dirty=(--source-dirty)

echo "== verify -> $out"
set +e
verifier/build/install/orderproof-verifier/bin/orderproof-verifier \
  --candidate "$candidate" --config "$config" --serial "$serial" --adb "$(command -v adb)" \
  --app-apk "app/build/outputs/apk/$candidate/debug/app-$candidate-debug.apk" \
  --test-apk e2e/build/outputs/apk/debug/e2e-debug.apk \
  --db "$backend_dir/orderproof.db" --token-file "$backend_dir/control.token" \
  --runs "$runs" --source-commit "$(git rev-parse HEAD)" "${dirty[@]}" "${extra[@]}" --out "$out"
code=$?
set -e
# The token is per-run and dead once the backend stops, but never leave it beside a report.
rm -f "$backend_dir/control.token"
exit $code
