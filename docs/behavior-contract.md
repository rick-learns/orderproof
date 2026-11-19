# Ember Kitchen behavior contract

This is the specification both candidates are judged against. The release gate checks
customer-visible behavior with UI assertions and checks money with an independent ledger
oracle. Expected ledgers in `verifier/src/main/kotlin/dev/orderproof/verifier/scenario/ScenarioCatalog.kt`
are written by hand from this document; nothing in the oracle or the catalog calls production
pricing code.

Clause numbers are stable and referenced from code (for example `CheckoutKeyPolicy` cites C3).

## Money

**C1. Integer minor units, server-authoritative.** Every amount is a `Long` count of cents. The
backend prices every cart (`POST /v1/quote`) and every order. The app displays what the backend
returned and never computes a subtotal, discount, tax or total itself.

**C2. Pricing rules.**

- Line total = (base price + selected option prices) × quantity.
- Subtotal = sum of line totals.
- Discount is applied to the subtotal: `EMBER10` takes 10% (rounded half-up to the cent) when the
  subtotal is at least $20.00 and the coupon has not expired; `WELCOME5` takes $5.00 when the
  subtotal is at least $15.00. A discount never exceeds the subtotal.
- Tax = 8.25% of (subtotal − discount), rounded half-up to the cent.
- Total = subtotal − discount + tax.

**C3. One customer intent, one key.** Before the first send, the app persists a checkout attempt:
the exact cart, coupon, the total the customer confirmed, a simulated payment token, an attempt id
and an idempotency key. Every send of that attempt (a retry after a lost response, a resend after
process death, a repeated tap) carries the same `Idempotency-Key`. A new key is minted only for a
new intent: a different cart, coupon or confirmed total.

> The faulty candidate breaks exactly this clause: it mints a fresh key on every send.

**C4. Server idempotency and atomicity.** `POST /v1/orders` commits the order, its single captured
simulated payment, the stock decrement and the idempotency record in one transaction. A repeated
key with the same request body returns the original order with `Idempotent-Replay: true` and
creates nothing. Once a key has committed an order, a request with that key and a different body is
refused (`422 IDEMPOTENCY_KEY_REUSED`). A rejection (sold out, total changed) commits nothing and
records no key, so nothing is bound to that key yet.

**C5. Exactly one payment per order.** Every order has exactly one `CAPTURED` `SIMULATED_CARD`
payment equal to its total. There are no payments without an order, and stock moves by exactly what
was ordered.

## Checkout outcomes

**C6. Persist before send.** The attempt is written with phase `SENT` before any network I/O, so
a process killed mid-request still knows what it may have committed.

**C7. Classify what the app knows.**

| Outcome | When | What the customer is told |
|---|---|---|
| Success | 2xx with a readable order | Confirmation with order number and total |
| Rejected | 409/422 with an `ApiError` | The specific reason; the attempt is closed; "you weren't charged" |
| Unavailable | 503, or the connection could not be opened | "Your order wasn't placed and you weren't charged. Try again in a moment." |
| Unknown | Request sent but no definitive answer: other 5xx, I/O error after send, unreadable 2xx | "Trying again is safe: you won't be charged twice." |

Only Unknown means "may have committed". The app never claims "not charged" for Unknown.

**C8. Retry is safe.** Retrying an open attempt resends it with its persisted key (C3). If the
first send committed, the backend replays the original order (C4) and the customer sees one
confirmation for one charge.

**C9. Reconcile on relaunch.** If the app starts with a persisted attempt, it asks
`GET /v1/checkouts/{key}` before offering anything else:

- 200: show that order as confirmed.
- 404: tell the customer it was not placed and they were not charged, and offer to place it.
- anything else: say it could not be checked and offer a retry that cannot double-charge (C8).

**C10. One submission at a time.** While an attempt is in flight, further taps on *Place order* do
nothing. Five fast taps produce one order.

## A changing world

**C11. Item sells out.** If an item is out of stock at submit time, the backend refuses the order
(`409 ITEM_UNAVAILABLE`) without charging. The app says which item sold out and that the customer
was not charged, marks the item *Sold out* in the cart, and blocks review until it is removed.

**C12. Total changes while away.** When checkout resumes, the app re-quotes. If the total differs
from the one shown (for example, `EMBER10` expired while the app was in the background), it shows
both totals, explains why, and disables *Place order* until the customer explicitly confirms the
new total. The backend independently refuses any order whose `expectedTotalMinor` differs from its
own total (`409 TOTAL_CHANGED`), so a stale total can never be charged.

**C13. Outage is recoverable.** A `503` while loading the menu or placing an order gives a clear
message, keeps the cart, and offers a retry that succeeds once the backend recovers.

## Order tracking and access

**C14. Status survives restarts.** After a confirmed order, closing and reopening the app shows that
order's status again without placing anything new.

**C15. Accessible at 200% text on a small screen.** At font scale 2.0 on a 720×1280 display, every
step of the journey is reachable by scrolling. Controls are at least 48 dp tall, and nothing
essential is clipped or hidden behind banners.

## Test seams

**C16. Control is never in the app.** Fault injection, holds, stock and clock control, and ledger
reads live on a separate control server bound to `127.0.0.1` and protected by a per-run token. The
app has no control code and no credentials. A phone on the LAN can reach only the public API.

## Seed data

`--reset` restores this state. The fixed clock starts at `1780333200000` ms (2026-06-01T17:00:00Z).

| Item | Id | Base | Options | Stock |
|---|---|---:|---|---:|
| Ember Smash Burger | `ember-smash-burger` | 12.95 | Patty: single / double +3.50; add-ons: bacon +2.25, cheddar +1.50, jalapeños +0.95 | 20 |
| Brisket Sandwich | `brisket-sandwich` | 14.50 | Bread: brioche / sourdough; extras: pickled onion +0.75, extra brisket +4.00 | 5 |
| Fire-Roasted Cauliflower | `fire-roasted-cauliflower` | 11.00 | Heat: mild / medium / hot | 20 |
| Charcoal Fries | `charcoal-fries` | 5.95 | Size: regular / large +2.50 | 40 |
| Smoked Lemonade | `smoked-lemonade` | 4.50 | Size: regular / large +1.50 | 30 |
| Burnt Honey Cake | `burnt-honey-cake` | 7.95 | — | 8 |

| Coupon | Rule | Minimum subtotal | Expires |
|---|---|---:|---|
| `EMBER10` | 10% off | 20.00 | reset + 2 h |
| `WELCOME5` | 5.00 off | 15.00 | never |

## Scenarios and expected ledgers

| Scenario | Clauses | Cart | Subtotal | Discount | Tax | Total | Orders / payments |
|---|---|---|---:|---:|---:|---:|---|
| `normalCheckout` | C1, C2, C14 | 2 × burger (double, bacon, cheddar); 1 × fries (large) | 48.85 | 0 | 4.03 | 52.88 | 1 / 1 |
| `lostResponseAfterCommit` ★ | C3, C4, C7, C8 | 1 × burger (single) | 12.95 | 0 | 1.07 | 14.02 | 1 / 1 |
| `processDeathDuringSubmission` | C3, C6, C9 | 1 × fries (regular) | 5.95 | 0 | 0.49 | 6.44 | 1 / 1 |
| `repeatedTaps` | C10 | 1 × lemonade (regular) | 4.50 | 0 | 0.37 | 4.87 | 1 / 1 |
| `itemUnavailableBeforeSubmit` | C11 | brisket, then it sells out | — | — | — | — | 0 / 0 |
| `couponExpiresWhileBackgrounded` | C12 | 2 × burger (single) + `EMBER10` (was 25.23) | 25.90 | 0 | 2.14 | 28.04 | 1 / 1 |
| `backendOutageRecovers` | C13 | 1 × cauliflower (mild) | 11.00 | 0 | 0.91 | 11.91 | 1 / 1 |
| `largeTextSmallScreen` | C15 | 1 × honey cake | 7.95 | 0 | 0.66 | 8.61 | 1 / 1 |

★ The central scenario: screen-recorded on every run, and its recording is required evidence.

Rounding examples: 48.85 × 8.25% = 4.030 → 4.03; 12.95 × 8.25% = 1.068 → 1.07;
4.50 × 8.25% = 0.371 → 0.37; 11.00 × 8.25% = 0.9075 → 0.91.

## What the faulty candidate does

The faulty candidate differs in one file, `app/src/faulty/.../candidate/CandidateBuild.kt`, which
supplies a `CheckoutKeyPolicy` that ignores the persisted key (a C3 violation). Its screens are
identical. After a lost response or a process death, its retry looks like a normal successful
checkout, but the backend sees a new key, so it commits a second order and a second payment. The
oracle reports `DUPLICATE_ORDER`, and the gate fails for that reason only.
`scripts/check-defect-isolation.sh` enforces the one-file difference in CI.
