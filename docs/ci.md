# CI and local-only stages

## In CI (`.github/workflows/ci.yml`, runs `scripts/ci.sh`)

Every push to `main` and every pull request runs everything that needs no emulator. Changes that
touch only `docs/**` or Markdown files skip CI; a newer push to the same branch cancels the run in
progress:

| Stage | Command | What it proves |
|---|---|---|
| Defect isolation | `scripts/check-defect-isolation.sh` | Candidates differ in exactly one file; test code never names a candidate |
| Backend integration tests | `:backend:test` | Pricing, the order transaction, idempotent replay, key reuse, concurrent same-key submits, every injected fault, control-API auth and loopback binding |
| Oracle tests | `:oracle:test` | Every violation code, plus ledgers written by the real backend |
| Verifier tests | `:verifier:test` | Instrumentation parsing, classification rules, gate decisions, report escaping and token redaction |
| App unit tests (both flavors) | `:app:test{Corrected,Faulty}DebugUnitTest` | Checkout coordinator (persist-before-send, retries, reconciliation), HTTP outcome mapping, cart rules, money formatting, backend-address normalization |
| Android lint (both flavors) | `:app:lint{Corrected,Faulty}Debug` | No issues; warnings are errors |
| Builds | app, androidTest, e2e, backend, verifier | Everything the gate needs compiles |
| Labeled APKs | `scripts/dist.sh` | Uploaded as the `apks-<sha>` artifact with `SHA256SUMS` |

Test and lint reports are uploaded as an artifact even when a step fails.

## Local-only stages

These need a hardware-accelerated Android emulator. GitHub-hosted Linux runners can provide KVM, but
this project has not measured how stable the gate is there, and its rule is that infrastructure noise
must never be confused with product results. So these stages run locally on a dedicated machine, and
their reports are kept as evidence ([results.md](results.md)). On that machine, after the emulator
boots, the primary gate took 11.2 min (3 corrected runs) plus 4.3 min (faulty), and the secondary took
2.8 + 3.8 min:

| Stage | Command | Required result |
|---|---|---|
| Release gate, primary | `scripts/verify.sh --candidate corrected --runs 3` | PASS, 3/3 clean |
| Release gate, secondary | `scripts/verify.sh --candidate corrected --avd op-small-api34 --config secondary` | PASS, 1/1 clean |
| Defect demonstration | `scripts/verify.sh --candidate faulty --expect-failure DUPLICATE_ORDER` (each config) | Expectation met: FAIL for `[DUPLICATE_ORDER]` only |
| Compose UI tests | `./gradlew :app:connected{Corrected,Faulty}DebugAndroidTest` | 10/10 per flavor |
| Demo video | `scripts/demo-video.sh <faulty-run> <corrected-run>` | Generated from the run reports |

To move the gate into CI later, run `scripts/verify.sh` on a self-hosted runner with KVM and
upload `runs/<id>/` as an artifact. The verifier's exit codes (0, 1, 3) map directly to job
success, failure and an infrastructure error.
