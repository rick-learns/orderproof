# Executed results

The gate tables are generated from each run's `report.json` and were not edited by hand. Every run
executed on an Android emulator on one Linux host. `scripts/dist.sh --reports` copies the full bundles
(HTML report, screenshots, recordings, logs, backend events, ledgers) of the final runs to
`dist/reports/<run>/`; bundles are not committed. Results recorded outside the gate are in the last section.

## Gate verdicts

| Run | Config | Candidate | Device | Runs | Clean | Verdict | Failure codes | Expectation |
|---|---|---|---|---:|---:|---|---|---|
| `20261119T131321Z-corrected-prs-pixel` | primary | corrected | prs-pixel, API 37, 1080x2400 @ 420 | 3 | 3/3 | **PASS** | — | — |
| `20261119T132440Z-faulty-prs-pixel` | primary | faulty | prs-pixel, API 37, 1080x2400 @ 420 | 1 | 0/1 | **FAIL** | `DUPLICATE_ORDER` | met (`DUPLICATE_ORDER`) |
| `20261119T131000Z-corrected-op-small-api34` | secondary | corrected | op-small-api34, API 34, 720x1280 @ 320 | 1 | 1/1 | **PASS** | — | — |
| `20261119T130609Z-faulty-op-small-api34` | secondary | faulty | op-small-api34, API 34, 720x1280 @ 320 | 1 | 0/1 | **FAIL** | `DUPLICATE_ORDER` | met (`DUPLICATE_ORDER`) |

## Build identity

| Run | Source commit | Dirty | Commit reported by app | App APK sha256 | Test APK sha256 |
|---|---|---|---|---|---|
| `20261119T131321Z-corrected-prs-pixel` | `b8048197bda9` | False | b8048197bda9 | `9d7e9f6c1700dd55…` | `f5f7a449b76e43c7…` |
| `20261119T132440Z-faulty-prs-pixel` | `b8048197bda9` | False | b8048197bda9 | `68d479d0e3bb6b15…` | `f5f7a449b76e43c7…` |
| `20261119T131000Z-corrected-op-small-api34` | `b8048197bda9` | False | b8048197bda9 | `9d7e9f6c1700dd55…` | `f5f7a449b76e43c7…` |
| `20261119T130609Z-faulty-op-small-api34` | `b8048197bda9` | False | b8048197bda9 | `68d479d0e3bb6b15…` | `f5f7a449b76e43c7…` |

## Scenario outcomes (first attempts; reruns in parentheses)

| Scenario | `20261119T131321Z-corrected-prs-pixel` | `20261119T132440Z-faulty-prs-pixel` | `20261119T131000Z-corrected-op-small-api34` | `20261119T130609Z-faulty-op-small-api34` |
|---|---|---|---|---|
| normalCheckout | pass pass pass | pass | pass | pass |
| lostResponseAfterCommit | pass pass pass | PRODUCT (PRODUCT) | pass | PRODUCT (PRODUCT) |
| processDeathDuringSubmission | pass pass pass | PRODUCT (PRODUCT) | pass | PRODUCT (PRODUCT) |
| repeatedTaps | pass pass pass | pass | pass | pass |
| itemUnavailableBeforeSubmit | pass pass pass | pass | pass | pass |
| couponExpiresWhileBackgrounded | pass pass pass | pass | pass | pass |
| backendOutageRecovers | pass pass pass | pass | pass | pass |
| largeTextSmallScreen | pass pass pass | pass | pass | pass |

## Why the faulty candidate failed

`20261119T132440Z-faulty-prs-pixel`:

- **lostResponseAfterCommit**: DUPLICATE_ORDER: Checkout attempt 2eb99787-f214-456d-8e23-505799885c63 produced 2 orders (EK-1001, EK-1002) and 2 captured payments totalling 2804 for one customer intent.
- **lostResponseAfterCommit**: customer-visible UI passed 4 assertion(s)
- **processDeathDuringSubmission**: DUPLICATE_ORDER: Checkout attempt f64334f4-9dec-489a-a260-7fa455d2e359 produced 2 orders (EK-1001, EK-1002) and 2 captured payments totalling 1288 for one customer intent.
- **processDeathDuringSubmission**: customer-visible UI passed 3 assertion(s)

`20261119T130609Z-faulty-op-small-api34`:

- **lostResponseAfterCommit**: DUPLICATE_ORDER: Checkout attempt 3d1af319-e952-428f-ba4a-bf1aabfb58ce produced 2 orders (EK-1001, EK-1002) and 2 captured payments totalling 2804 for one customer intent.
- **lostResponseAfterCommit**: customer-visible UI passed 4 assertion(s)
- **processDeathDuringSubmission**: DUPLICATE_ORDER: Checkout attempt e92734b2-5fbb-4135-a453-b49d4ef29895 produced 2 orders (EK-1001, EK-1002) and 2 captured payments totalling 1288 for one customer intent.
- **processDeathDuringSubmission**: customer-visible UI passed 3 assertion(s)

## Flakiness

No scenario was flaky in these runs: every first attempt matched its rerun, or there was no rerun.

## Timings (diagnostics only; no verdict depends on them)

| Run | Total | Slowest scenario |
|---|---:|---|
| `20261119T131321Z-corrected-prs-pixel` | 11.2 min | largeTextSmallScreen (36 s) |
| `20261119T132440Z-faulty-prs-pixel` | 4.3 min | largeTextSmallScreen (44 s) |
| `20261119T131000Z-corrected-op-small-api34` | 2.8 min | normalCheckout (32 s) |
| `20261119T130609Z-faulty-op-small-api34` | 3.8 min | largeTextSmallScreen (39 s) |

## Earlier runs (superseded by the runs above)

Kept for the record, including the one that did not meet its expectation; see
[failure-and-recovery.md](failure-and-recovery.md#4-harness-and-infrastructure-problems-found-on-the-way).

Runs on a dirty tree were development runs; some ran only the scenarios being worked on.

| Run | Commit | Candidate | Device | Scenarios | Clean | Verdict | Failure codes | Expectation | Flaky |
|---|---|---|---|---:|---:|---|---|---|---|
| `20261119T111729Z-faulty-prs-pixel` | `62ecda2` (dirty) | faulty | prs-pixel | 8 | 0/1 | FAIL | INFRASTRUCTURE, INCONCLUSIVE | **not met** | 1 |
| `20261119T112346Z-faulty-prs-pixel` | `62ecda2` (dirty) | faulty | prs-pixel | 1 | 0/1 | FAIL | INCONCLUSIVE | — | 0 |
| `20261119T112454Z-faulty-prs-pixel` | `62ecda2` (dirty) | faulty | prs-pixel | 2 | 1/1 | PASS | — | — | 0 |
| `20261119T112622Z-faulty-prs-pixel` | `62ecda2` (dirty) | faulty | prs-pixel | 8 | 0/1 | FAIL | DUPLICATE_ORDER | met | 0 |
| `20261119T113221Z-corrected-prs-pixel` | `6d88013` | corrected | prs-pixel | 8 | 3/3 | PASS | — | — | 0 |
| `20261119T114210Z-faulty-prs-pixel` | `6d88013` | faulty | prs-pixel | 8 | 0/1 | FAIL | DUPLICATE_ORDER | met | 0 |
| `20261119T114735Z-corrected-op-small-api34` | `6d88013` | corrected | op-small-api34 | 8 | 1/1 | PASS | — | — | 0 |
| `20261119T115214Z-faulty-op-small-api34` | `6d88013` | faulty | op-small-api34 | 8 | 0/1 | FAIL | INCONCLUSIVE, DUPLICATE_ORDER | **not met** | 1 |
| `20261119T115825Z-faulty-op-small-api34` | `3d91def` | faulty | op-small-api34 | 8 | 0/1 | FAIL | DUPLICATE_ORDER | met | 0 |
| `20261119T120217Z-corrected-op-small-api34` | `3d91def` | corrected | op-small-api34 | 8 | 1/1 | PASS | — | — | 0 |
| `20261119T120543Z-corrected-prs-pixel` | `3d91def` | corrected | prs-pixel | 8 | 3/3 | PASS | — | — | 0 |
| `20261119T121806Z-faulty-prs-pixel` | `3d91def` | faulty | prs-pixel | 8 | 0/1 | FAIL | DUPLICATE_ORDER | met | 0 |
| `20261119T123616Z-corrected-prs-pixel` | `ee2d1ca` | corrected | prs-pixel | 8 | 3/3 | PASS | — | — | 0 |
| `20261119T124621Z-faulty-prs-pixel` | `ee2d1ca` | faulty | prs-pixel | 8 | 0/1 | FAIL | DUPLICATE_ORDER | met | 0 |
| `20261119T125329Z-corrected-op-small-api34` | `ee2d1ca` | corrected | op-small-api34 | 8 | 1/1 | PASS | — | — | 0 |
| `20261119T125645Z-faulty-op-small-api34` | `ee2d1ca` | faulty | op-small-api34 | 8 | 0/1 | FAIL | DUPLICATE_ORDER, INCONCLUSIVE, MISSING_EVIDENCE | **not met** | 1 |

## Recorded outside the gate

Not generated from `report.json`; copied from the tools' own output at `b804819`.

**Compose UI tests** (Gradle `connected{Corrected,Faulty}DebugAndroidTest` JUnit XML, same emulators):

| AVD | corrected | faulty |
|---|---|---|
| prs-pixel (API 37) | 10 tests, 0 failures, 0 errors | 10 tests, 0 failures, 0 errors |
| op-small-api34 (API 34) | 10 tests, 0 failures, 0 errors | 10 tests, 0 failures, 0 errors |

**JVM tests and lint** (`scripts/ci.sh`): 120 tests run, 0 failed; lint reports no issues for either flavor.

**Labeled APKs** (`scripts/dist.sh`): the sha256 of `EmberKitchen-corrected-1.0.0-b8048197bda9.apk`,
`EmberKitchen-FAULTY-DO-NOT-SHIP-1.0.0-b8048197bda9.apk` and `OrderProof-e2e-b8048197bda9.apk` match
the app and test APK hashes recorded by the four final reports above.

**Demo video** (`scripts/demo-video.sh`): `dist/orderproof-demo.mp4`, 22.8 s, 1100×1480. It plays the
lost-response recordings from the two primary runs side by side, with captions taken from their
reports. `docs/images/demo-still.png` is its last frame.
