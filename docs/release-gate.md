# Release gate

The gate answers one question: may this exact APK go to a phone? It runs every scenario on an
emulator, judges each one from customer-visible UI and the backend's persisted ledger, and returns
PASS or FAIL with reasons and evidence.

## Running it

```bash
scripts/verify.sh --candidate corrected --runs 3              # primary: prs-pixel
scripts/verify.sh --candidate corrected --avd op-small-api34 --config secondary --runs 1
scripts/verify.sh --candidate faulty --expect-failure DUPLICATE_ORDER
scripts/verify.sh --candidate corrected --scenarios lostResponseAfterCommit,repeatedTaps --runs 1
```

| Option | Default | Meaning |
|---|---|---|
| `--candidate corrected\|faulty` | required | Which flavor to build and judge |
| `--avd NAME` | `prs-pixel` | Emulator to start or reuse |
| `--config LABEL` | `primary` | Label written into the report |
| `--runs N` | `1` | Full passes over the selected scenarios |
| `--required-clean-runs N` | `--runs` | Clean runs needed for PASS |
| `--rerun-failures N` | `1` | Diagnostic reruns per failure; never counted |
| `--scenarios a,b` | all | Subset, by id |
| `--expect-failure CODE` | — | Demo mode: succeed only if the gate fails for exactly this code |

Output goes to `runs/<UTC timestamp>-<candidate>-<avd>/`:

```
report.html  report.json                          verdict, matrix, identity, reproduction
backend/orderproof.db  backend/backend.log        the run's ledger and server log (token deleted)
runs/run-01/<scenario>/attempt-1/
  instrumentation.txt  logcat.txt  app.log        what the test runner and the app said
  backend-events.json  ledger.json  oracle.json   what the backend recorded and how it was judged
  NN-<label>.png  recording.mp4                   screenshots; video for the central scenario
  failure-hierarchy.xml                           view tree, captured when a scenario fails
```

Exit codes: `0` gate passed, or `--expect-failure` was met. `1` gate failed, or the expectation was
not met. `3` the verification itself could not run (no device, install failed, backend down).
`64` bad usage.

## How a scenario is classified

The rules are applied in this order (`verifier/.../judge/Classifier.kt`):

1. **Wrong or extra candidate → INFRASTRUCTURE_FAILURE.** The app log names its candidate on
   startup. If it is not the build under test, or another candidate also ran, the ledger cannot be
   trusted.
2. **Any ledger violation other than a missing order → PRODUCT_FAILURE.** This includes duplicate
   orders, extra payments, wrong totals, orphan payments and stock drift. It applies even if every
   UI assertion passed, and the report then says so explicitly ("customer-visible UI passed N
   assertions").
3. **Unreadable ledger, runner crash, or failed reset → INFRASTRUCTURE_FAILURE.**
4. **A failed UI assertion → PRODUCT_FAILURE.**
5. **Missing order while the UI claimed success → PRODUCT_FAILURE.**
6. **The scenario did not finish → INCONCLUSIVE.** For example, a launch timed out. A missing
   order here is shown as a consequence, not a cause.
7. **Required evidence missing → INCONCLUSIVE.**
8. Otherwise **PASS**.

Required evidence for every scenario: instrumentation output, the scenario start marker, the app
log, backend events, the ledger, and every screenshot the scenario took. The central scenario
(`lostResponseAfterCommit`) also requires its screen recording.

## How the gate decides

- Only first attempts count. A rerun can show that a failure is flaky but never turns it into a
  pass.
- **PASS** needs every counted execution to pass with complete evidence, at least
  `--required-clean-runs` clean runs, and a build identity that matches the source commit.
- **Failure codes** are stable strings: oracle codes (`DUPLICATE_ORDER`, `MULTIPLE_PAYMENTS`,
  `ORDER_MISMATCH`, …), `UI_ASSERTION`, `INFRASTRUCTURE`, `INCONCLUSIVE`, `MISSING_EVIDENCE`,
  `INSUFFICIENT_RUNS` and `BUILD_IDENTITY_MISMATCH`.
- **The first failure** (earliest run, then earliest start) is preserved and linked to its evidence.
- **Flaky** means a counted failure whose rerun passed, or a scenario whose counted outcomes differ
  across runs. It is reported, not forgiven.
- **`--expect-failure DUPLICATE_ORDER`** is met only when the gate fails and the codes are exactly
  `[DUPLICATE_ORDER]`. A faulty run that also had an inconclusive scenario does *not* meet the
  expectation, because "it failed for the right reason" must not hide a broken harness.

## Build identity

The report records the commit (and whether the tree was dirty), the sha256 of the app and test
APKs, the installed `versionName`/`versionCode` from `dumpsys package`, and the commit the app
reports in its own log (`BuildConfig.GIT_COMMIT`). If the reported commit is not a prefix of the
source commit, the gate fails with `BUILD_IDENTITY_MISMATCH`.

## Safety

- The control token is written to a 0600 file, is passed to the instrumentation as an argument,
  is redacted from every captured log and report, and is deleted as soon as the verifier finishes, whatever its exit code.
- Before installing, the verifier uninstalls the other candidate, so only one Ember Kitchen build
  is on the device.
- The control server listens only on `127.0.0.1` and reaches the emulator through `adb reverse`.
