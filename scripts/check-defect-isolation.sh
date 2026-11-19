#!/usr/bin/env bash
# Fails unless the two candidates differ in exactly one file each: candidate/CandidateBuild.kt.
# Everything else (screens, coordinator, network, tests) is shared source.
set -euo pipefail
cd "$(dirname "$0")/.."
expected="kotlin/dev/orderproof/emberkitchen/candidate/CandidateBuild.kt"
status=0
for flavor in corrected faulty; do
  files=$(cd "app/src/$flavor" && find . -type f | sed 's|^\./||' | sort)
  if [ "$files" != "$expected" ]; then
    echo "app/src/$flavor must contain only $expected; found:" >&2
    echo "$files" | sed 's/^/  /' >&2
    status=1
  fi
done
# Test code must not branch on the candidate: a test that knows which build it runs could pass both.
# (The e2e manifest names both packages for package visibility; that is not test logic.)
if grep -rnE --include=*.kt "CandidateBuild|IS_FAULTY|\.faulty\b" app/src/test app/src/androidTest e2e/src >/dev/null 2>&1; then
  echo "tests reference the candidate identity:" >&2
  grep -rnE --include=*.kt "CandidateBuild|IS_FAULTY|\.faulty\b" app/src/test app/src/androidTest e2e/src >&2
  status=1
fi
[ $status -eq 0 ] && echo "defect isolation: ok (one differing file per flavor; tests are candidate-agnostic)"
exit $status
