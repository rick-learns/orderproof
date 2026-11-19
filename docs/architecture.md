# Architecture

OrderProof has three parts. **Ember Kitchen** is a small native Android ordering app. A **local
backend** owns prices, stock, orders and simulated payments. A **release gate** drives the app on an
emulator, injects faults through the backend, and judges each run from the persisted ledger.

```
 ┌────────────── emulator ──────────────┐            ┌──────────── host ─────────────┐
 │                                      │  public    │ orderproof-backend            │
 │  Ember Kitchen (corrected | faulty)  │──API──────▶│  0.0.0.0:8787  /v1/...        │
 │       ▲  accessibility (UiAutomator) │  10.0.2.2  │  SQLite ledger, event log     │
 │       │                              │            │                               │
 │  e2e instrumentation APK ────────────┼─control───▶│  127.0.0.1:8788 /control/...  │
 │  (scenarios, evidence markers)       │ adb reverse│  token-protected, loopback    │
 └──────────────────────────────────────┘            └──────────────▲────────────────┘
                 ▲ adb: install, instrument, screenrecord, logcat    │ ledger, events
                 └──────────────── orderproof-verifier ──────────────┘
                                   oracle ▸ classifier ▸ gate ▸ report.json + report.html
```

## Modules

| Module | Kind | Responsibility |
|---|---|---|
| `:api-contract` | Kotlin/JVM | Public API DTOs, paths and headers shared by app and backend |
| `:control-contract` | Kotlin/JVM | Control API DTOs (faults, holds, stock, clock, ledger, events) |
| `:backend` | Kotlin/JVM app | JDK `HttpServer` with SQLite (JDBC). Pricing, checkout transaction, idempotency, seeding, fault injection, event log |
| `:app` | Android app | Ember Kitchen, built in two flavors: `corrected` and `faulty` |
| `:e2e` | Android test APK | Self-instrumenting UiAutomator scenarios that drive either candidate from outside its process |
| `:oracle` | Kotlin/JVM | Reads a ledger and judges it against hand-written expectations |
| `:verifier` | Kotlin/JVM app | Device prep, scenario runs, evidence capture, classification, gate, reports |

Dependencies point one way. The app depends only on `:api-contract`. `:e2e` depends on
`:control-contract` and never on the app's code. The oracle depends on `:control-contract`; it links
the backend only in its tests.

## The app

```
app/src/main/kotlin/dev/orderproof/emberkitchen/
  core/        Money formatting, AppLog (structured logcat lines with attempt/key/request ids)
  data/        OkHttp API client, menu repository, DataStore-backed stores, connection settings
  domain/      Cart, CheckoutAttempt, CheckoutCoordinator, CheckoutKeyPolicy, ApiOutcome
  ui/          One package per screen: connect, menu, item, cart, checkout, order; components; theme
app/src/corrected/…/candidate/CandidateBuild.kt   reuses the persisted key  (contract C3)
app/src/faulty/…/candidate/CandidateBuild.kt      mints a key per send      (deliberate defect)
```

- **Flavors.** The candidates share all source except `CandidateBuild.kt`. The faulty build has
  application id `dev.orderproof.emberkitchen.faulty`, version suffix `-FAULTY-DO-NOT-SHIP`, and the
  launcher label "Ember FAULTY demo", so it can never be mistaken for the real app.
- **`CheckoutCoordinator`** owns one checkout attempt at a time. It persists the attempt before
  sending (C6), maps transport results to `ApiOutcome` (C7), keeps Unknown and Unavailable attempts
  open for a safe retry (C8), reconciles on startup (C9), and serializes submissions (C10).
- **Transport.** OkHttp with `retryOnConnectionFailure(false)` and no connection reuse, so the only
  retries are the ones the customer asks for, and each one is visible in the logs.
- **State.** The cart, the open attempt, the last order and the backend URL live in small JSON
  slots in DataStore. Prices are never stored; every screen asks the backend.
- **UI.** Jetpack Compose with Material 3 in an Ember palette: orange `#B5421A` (5.6:1 against
  white text), charcoal `#2A2725` and cream.
  Screens are thin wiring over stateless content composables (for example `CheckoutContent`), so
  Compose UI tests can render every state directly. Test tags are exposed as resource ids
  (`testTagsAsResourceId`), so the external e2e driver can find the same elements.

## The backend

```
backend/src/main/kotlin/dev/orderproof/backend/
  config/   CLI flags          http/     servers, router, public API
  domain/   pricing, checkout, request fingerprint
  storage/  schema and repositories (orders, payments, idempotency, menu, coupons, events)
  seed/     deterministic catalog and seeder        time/   system or fixed clock
  faults/   one-shot fault arming and response holds
  control/  control API (loopback + bearer token), ledger reader
```

- **One transaction per order** (C4). In a single SQLite transaction the backend checks the key,
  prices the cart, checks stock and the expected total, then inserts the order, its lines, the
  payment and the idempotency record, and decrements stock. A replay returns the stored order.
- **Faults are armed, never random.** `DROP_RESPONSE_AFTER_COMMIT` commits and then closes the
  socket without answering. `HOLD_RESPONSE_AFTER_COMMIT` commits and parks the response until the
  test releases it. `SERVICE_UNAVAILABLE` answers 503 with `Retry-After: 5`. Each is armed for a
  given number of matching requests, so a scenario decides exactly which request fails.
- **Time is controllable.** With `--clock fixed` the clock starts at the seed epoch and moves only
  when the control API advances it. That is how a coupon expires "while backgrounded" without any
  waiting.
- **Every event is journaled** with its request id, attempt id and key: quote, order committed,
  idempotent replay, rejection, fault fired, response dropped or held. The report shows these
  backend events next to the app log.

## The release gate

`orderproof-verifier` is a JVM CLI. `scripts/verify.sh` builds everything, starts or reuses the
emulator, starts a fresh backend with a fixed clock and a per-run token, and calls the verifier with
the APKs and the source commit.

For each run and scenario, the verifier:

1. **Prepares the device** once per verification: uninstalls the other candidate, installs the
   candidate under test and the e2e APK, disables animations, and records the build identity
   (APK sha256, version from `dumpsys package`).
2. **Runs one scenario** with `am instrument`. The e2e rule resets the backend, force-stops the
   app, wipes its data and restores display settings before each case. The central scenario is
   screen-recorded.
3. **Collects evidence:** instrumentation output, logcat split into the e2e journal and the app log,
   screenshots, view-hierarchy dumps on failure, backend events and the ledger.
4. **Judges.** `LedgerOracle` checks invariants (C4, C5) and the scenario's expected ledger. The
   `Classifier` combines UI assertions, oracle violations, missing evidence and infrastructure
   signals into PASS, PRODUCT_FAILURE, INFRASTRUCTURE_FAILURE or INCONCLUSIVE.
5. **Reruns failures** as diagnostics (attempt 2+), to report flakiness.

The gate passes only when every first attempt in every run passed with complete evidence, there
were enough clean runs, and the build identity matches the source commit. See
[release-gate.md](release-gate.md) for the rules and [failure-and-recovery.md](failure-and-recovery.md)
for the walkthroughs.

## Design decisions

- **The ledger outranks the UI.** The faulty build's screens are indistinguishable from the
  corrected build's. Only the backend records show the double charge, so the gate trusts them
  first.
- **The oracle is independent.** It has its own SQL, and its expectations are written by hand. If it
  reused the backend's pricing or repositories, a pricing bug could mark itself as correct.
- **One e2e APK for both candidates.** The scenarios take the target package as an argument and
  contain no candidate-specific code (enforced by `check-defect-isolation.sh`). The same tests run
  unchanged against both builds.
- **No sleeps, no randomness.** Every wait is a condition with a deadline. Faults are armed
  explicitly, holds are released explicitly, and the clock is advanced explicitly. Timings appear
  in reports as diagnostics only.
