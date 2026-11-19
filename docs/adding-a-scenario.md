# Adding a scenario

A scenario has three parts. Each lives in a different place, so no part can grade its own work:

| Part | Where | Owns |
|---|---|---|
| Contract | `docs/behavior-contract.md` | What should happen, in words and in cents |
| UI journey | `e2e/src/main/kotlin/dev/orderproof/e2e/scenarios/*.kt` | Drives the app; checks only what a customer can see |
| Expected ledger | `verifier/src/main/kotlin/dev/orderproof/verifier/scenario/ScenarioCatalog.kt` | What the backend must have persisted afterwards |

The example below adds `welcomeCouponApplies`: one burger and one lemonade with `WELCOME5`.

## 1. Write the contract first

Add a row to the scenario table in `docs/behavior-contract.md` and work the numbers out by hand:

```
1 × burger (single) 12.95 + 1 × lemonade (regular) 4.50 = 17.45   (≥ 15.00, so WELCOME5 applies)
discount 5.00 → taxable 12.45
tax 12.45 × 8.25% = 1.027125 → 1.03
total 12.45 + 1.03 = 13.48
```

If the behavior is new, add a numbered clause instead of changing an existing one. Clause numbers
are referenced from code.

## 2. Write the UI journey

Put it in the scenario class that matches its theme, or start a new class with the same header.
Use the rule-provided driver, journey, control and evidence objects:

```kotlin
/** 1 × burger + 1 × lemonade with WELCOME5: $17.45 − $5.00 + $1.03 = $13.48. */
@Test
fun welcomeCouponApplies() {
    journey.openAndConnect()
    journey.awaitMenu()
    journey.addItem("ember-smash-burger", listOf("patty-single"))
    journey.addItem("smoked-lemonade", listOf("lemonade-regular"))
    journey.openCart()
    journey.applyCoupon("WELCOME5")
    journey.review()
    evidence.check("discounted total shown", "$13.48" in journey.shownTotal(), journey.shownTotal())
    evidence.screenshot("review")

    journey.placeOrder()
    val number = journey.awaitConfirmation()
    evidence.check("confirmation shows total", "$13.48" in app.text("order_total"), app.text("order_total"))
    evidence.screenshot("confirmation-$number")
}
```

Rules for the journey:

- **Do not reset anything yourself.** `ScenarioRule` resets the backend to the seed, force-stops
  the app, wipes its data and restores font scale and display size before every scenario.
- **No sleeps and no randomness.** Use `app.await`, `app.awaitAny` and `app.waitForText`, which are
  conditions with deadlines. For "time passes", call `rule.control.advanceClock(ms)`. For "the
  network fails", arm a fault with `rule.control.arm(FaultKind.…)`. For "the server is slow", arm
  `HOLD_RESPONSE_AFTER_COMMIT` and then call `awaitHold` / `releaseHolds`.
- **Check only what a customer can see,** through `evidence.check(name, passed, detail)`. Do not
  read the ledger from the test: the verifier judges it on the host.
- **Do not branch on the candidate.** The same test must run unchanged against both builds.
  `scripts/check-defect-isolation.sh` fails CI if test code mentions `CandidateBuild` or the faulty
  package.
- **Take screenshots at the moments a reviewer would want to see.** Each screenshot is required
  evidence once it has been recorded.
- **Find elements by test tag.** New UI elements need a tag in `app/.../ui/TestTags.kt`. A
  scrollable container needs its own tag so `app.click(tag, scrollIn = container)` can reach
  content below the fold on small screens and at large font sizes.

## 3. Add the expected ledger

In `ScenarioCatalog.all`, write the expectation by hand, with the arithmetic in a comment. Never
call production pricing code:

```kotlin
// 1295 + 450 = 1745; WELCOME5 −500 → 1245; 1245 × 8.25% = 102.71 → 103.
Scenario(
    "welcomeCouponApplies", "CheckoutJourneyScenarios", "Fixed coupon applies at the minimum",
    Expectation(listOf(ExpectedOrder(
        listOf(line("ember-smash-burger", "patty-single"), line("smoked-lemonade", "lemonade-regular")),
        subtotalMinor = 1745, discountMinor = 500, taxMinor = 103, totalMinor = 1348, couponCode = "WELCOME5",
    ))),
),
```

The oracle also applies its global invariants to every scenario: one captured payment per order,
consistent arithmetic, an idempotency record per order, stock moved by exactly what was ordered,
and no duplicate orders for one attempt. If the scenario changes stock on purpose (for example,
through `setStock`), declare it with `stockBaselineOverrides`.

## 4. If you need a new fault

1. Add the kind to `FaultKind` in `control-contract/.../Faults.kt`.
2. Implement it in `backend/.../faults/FaultInjector.kt`. It must be armed explicitly, fire for a
   counted number of matching requests, and journal a `FAULT_*` event.
3. Cover it in `backend/src/test/.../HttpApiTest.kt` before using it in a scenario.

## 5. Run it against both candidates

```bash
scripts/verify.sh --candidate corrected --scenarios welcomeCouponApplies --runs 1
scripts/verify.sh --candidate faulty    --scenarios welcomeCouponApplies --runs 1
scripts/verify.sh --candidate corrected --scenarios welcomeCouponApplies --avd op-small-api34 --config secondary --runs 1
```

Decide which result the faulty build should get, and write that down in the contract. This
example never resends an attempt, so the faulty build should pass it too. If the faulty build
fails a scenario that is not about C3, the scenario or the defect isolation is wrong.

During development, `scripts/smoke-one.sh ChangedWorldScenarios#welcomeCouponApplies` reruns one
journey quickly against an already running backend. Before you call the scenario done, run the
full gate: three clean primary runs and one secondary run.
