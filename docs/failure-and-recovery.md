# Failure and recovery

This document walks through what happens when checkout goes wrong. It covers the failures Ember
Kitchen is built to survive, the one the faulty candidate does not survive, and the harness and
infrastructure problems found while building the gate. Log excerpts come from executed runs; ids
are shortened. The final runs and their evidence bundles are listed in [results.md](results.md).

## 1. Lost response after commit (the central scenario)

**Setup.** One Ember Smash Burger, total $14.02. The scenario arms `DROP_RESPONSE_AFTER_COMMIT`
for one request. The backend commits the order and payment, then closes the socket without
answering. The customer sees "We couldn't confirm your order" and taps **Try again**.

**Corrected candidate.** Both sends carry the key persisted when the attempt was created:

```
attempt.created attempt=162c… persistedKey=9cb7…  total=1402
submit.send     send=1 key=9cb7…                          → backend: EK-1001 committed, payment 1402, DROPPED
submit.open     reason=OUTCOME_UNKNOWN (IOException: unexpected end of stream)
submit.send     send=2 key=9cb7…                          → backend: IDEMPOTENT_REPLAY "returned existing order; no new order or payment"
submit.confirmed number=EK-1001 total=1402
```

Oracle: `violations=[]`, 1 order, 1 payment. Gate: PASS.

**Faulty candidate.** The screens are the same: an error, a retry, then the confirmation
"EK-1002, $14.02". The UI assertions all pass. The log shows the defect:

```
attempt.created attempt=6e00… persistedKey=78d7…  total=1402
submit.send     send=1 key=858a… persistedKey=78d7…       → backend: EK-1001 committed, payment 1402, DROPPED
submit.open     reason=OUTCOME_UNKNOWN
submit.send     send=2 key=67bc… persistedKey=78d7…       → backend: new key, so EK-1002 committed, payment 1402
submit.confirmed number=EK-1002 total=1402
```

Oracle:

> DUPLICATE_ORDER: Checkout attempt 6e00… produced 2 orders (EK-1001, EK-1002) and 2 captured
> payments totalling 2804 for one customer intent.

The classifier marks it PRODUCT_FAILURE even though the UI passed: the ledger outranks the UI. The
gate fails with `[DUPLICATE_ORDER]` and links the first failure to its evidence folder (recording,
screenshots, app log, backend events, ledger). The diagnostic rerun fails the same way, so the
report does not list it as flaky.

## 2. Process death during submission

**Setup.** One Charcoal Fries, total $6.44. The scenario arms `HOLD_RESPONSE_AFTER_COMMIT`, so the
backend commits and then parks the response. The test waits until the hold is reported, kills the
app (`am force-stop`, no callbacks), releases the stale response into a dead socket, and relaunches
the app the way a customer would.

**Corrected candidate.** On startup the coordinator finds the persisted attempt in phase `SENT` and
asks `GET /v1/checkouts/{persistedKey}`. The backend has an order for that key, so the app shows
EK-1001 as confirmed. One order, one payment.

**Faulty candidate.** The app sent a key it never persisted, so reconciliation asks about a key the
backend has never seen:

```
submit.send      send=1 key=c5bf… persistedKey=72df…       → backend: EK-1001 committed (response held)
-- process killed, app relaunched --
reconcile.start  phase=SENT persistedKey=72df…
GET /v1/checkouts/72df… → 404
reconcile.not_found
submit.send      send=2 key=78b0… persistedKey=72df…       → backend: EK-1002 committed
```

On relaunch the customer reads *"The app closed before your order finished. It wasn't placed, and
you weren't charged."* That statement is false, because EK-1001 was already charged. Tapping
**Try again** charges again. Oracle: `DUPLICATE_ORDER`, 2 payments totalling 1288.

The screenshots `02-relaunch-offers-retry.png` and `03-after-relaunch-EK-1002.png` in the faulty
run show this sequence.

## 3. The other recovery paths (both candidates pass)

| Scenario | What breaks | How the app recovers | What the ledger proves |
|---|---|---|---|
| `repeatedTaps` | Five taps on **Place order** while the first send is in flight (its response is held) | Submission is exclusive; extra taps do nothing while an attempt is open | 1 order, 1 payment |
| `itemUnavailableBeforeSubmit` | Brisket stock set to 0 after review | `409 ITEM_UNAVAILABLE`: "…just sold out. You weren't charged."; cart shows *Sold out*; review is blocked | 0 orders, 0 payments, stock unchanged |
| `couponExpiresWhileBackgrounded` | App backgrounded; the backend clock jumps 2 h 01 m, so `EMBER10` expires | On resume the app re-quotes and shows "$25.23 → $28.04"; **Place order** stays disabled until the customer confirms | 1 order at 28.04, no coupon |
| `backendOutageRecovers` | `503` on the menu, then on checkout | "Temporarily unavailable" with retry; at checkout, "wasn't placed and you weren't charged" | 1 order at 11.91 after recovery |
| `normalCheckout` | Nothing; the app is closed after confirming | On reopen the last order and its status are shown again | 1 order at 52.88 |
| `largeTextSmallScreen` | 200% font, screen forced to 720×1280 @ 320 dpi | The journey completes by scrolling; *View cart*, *Review order*, *Place order* and the order number are each fully on screen and at least 48 dp tall when used | 1 order at 8.61 |

Why the faulty build passes these: none of them resends an attempt that may already have
committed. The defect only shows when the same intent is sent twice, which is exactly what the two
lost-answer scenarios force.

## 4. Harness and infrastructure problems found on the way

Every inconclusive or infrastructure result was investigated before it was rerun, and each fix
went into the harness or into test hooks, never into weaker checks.

| Symptom | Root cause | Fix |
|---|---|---|
| A faulty run reported `candidate=corrected` lines in its app log | A corrected build left on the device from an earlier session was still running | The verifier uninstalls the other candidate before installing. Any foreign candidate in the log now makes the scenario INFRASTRUCTURE_FAILURE, never a pass (device preparation and `Classifier`) |
| On API 34 at 720×1280, *Add to cart* and quantity controls were never found | The faulty build's warning banner pushes the item options below the fold; UiAutomator searched only the visible area | The item screen exposes an `item_details` scroll container, and the driver scrolls inside it until the control appears (`test(app): tag the item details scroller`, `fix(e2e): scroll to item options and quantity on small screens`) |
| At 200% font, `scrollUntil` stopped before the target in the Compose menu | Compose lazy lists do not report scroll ends the way UiAutomator expects | The driver drags in small steps until the content text stops changing (`fix(e2e): scroll by swiping until the list stops changing`) |
| `StaleObjectException` while reading a total | Recomposition replaced the node between the find and the read | Reads retry on a stale node, up to 5 times (`fix(e2e): re-read a node that goes stale while its text is collected`) |
| `couponExpiresWhileBackgrounded` INCONCLUSIVE once on API 34: "did not come to the foreground" | After the reset's `force-stop` and `pm clear`, the system's deferred task cleanup killed the newly launched process 200 ms after it started (`am_kill … remove task`, adj −10000), leaving an empty task and a cream screen | `launch()` checks for both a window and a process. If both are gone, it taps the icon again, at most twice, and logs a step in the evidence (`fix(e2e): relaunch when the system kills the app before its first window`) |

In the last case the gate behaved as intended: the scenario was INCONCLUSIVE (`app logs` missing,
since the app never ran), the rerun passed, the report marked it flaky, and the faulty run's
`--expect-failure DUPLICATE_ORDER` was **not met**, because the codes were
`[DUPLICATE_ORDER, INCONCLUSIVE, MISSING_EVIDENCE]`. That run (`runs/20261119T125645Z-faulty-op-small-api34`)
is kept as an example of the gate refusing to call a noisy run a clean demonstration. In the four
final runs at `b804819` the relaunch path never fired (no "starting it again" step in any evidence),
and every scenario that was expected to pass did so on its first attempt.
