# Phone handoff checklist

Use this for the first test on a physical Android phone. Every gate run so far was on emulators,
so this is the first time the app meets real hardware, a real Wi-Fi network and real
permission prompts.

**Install only the corrected build** unless you are deliberately demonstrating the defect. The
faulty build is a separate app ("Ember FAULTY demo", package `…emberkitchen.faulty`, version suffix
`-FAULTY-DO-NOT-SHIP`), and it double-charges on retry.

## Before you start

- [ ] Phone: Android 8.0 (API 26) or newer. API 34 and API 37 are emulator-verified.
- [ ] Laptop and phone are on the same Wi-Fi. Guest networks with client isolation will not work.
- [ ] Laptop firewall allows inbound TCP 8787, for example `sudo ufw allow 8787/tcp`. Port 8788
      (control) must stay closed; it binds to loopback anyway.
- [ ] `sqlite3` is installed on the laptop (`sudo apt install sqlite3`) to inspect orders.
- [ ] You know the build you are installing. Compare `dist/apk/SHA256SUMS` with
      `sha256sum dist/apk/EmberKitchen-corrected-*.apk`. The app's connect screen shows the version,
      candidate and commit; check that they match the APK name.

## 1. Start the backend on the laptop

Run the binary directly. Without `--control-token-file` there is no control server at all: no fault
injection, no ledger access, nothing for the phone to reach except the public API.

```bash
source scripts/env.sh
./gradlew -q :backend:installDist
backend/build/install/orderproof-backend/bin/orderproof-backend --db /tmp/ember-phone.db --reset
# → "Control API disabled (no --control-token-file)."
hostname -I        # pick the Wi-Fi address, e.g. 192.168.1.20
```

From the phone's browser, `http://192.168.1.20:8787/v1/health` should return
`{"status":"ok",…}`. If it does not, fix the network before you install anything.

## 2. Install the corrected APK

```bash
adb install -r dist/apk/EmberKitchen-corrected-1.0.0-<sha>.apk
```

Or copy the APK to the phone and open it (allow "Install unknown apps" for your file manager).

## 3. Connect

- [ ] Open **Ember Kitchen**. The **Connect to Ember Kitchen** screen appears.
- [ ] Enter `http://192.168.1.20:8787` (your address) and tap **Test connection**. Expect
      "Connected to orderproof-backend …".
- [ ] **Android 17 (API 37) and newer:** the system asks for local network access. Tap **Allow**. If
      you deny it, the app explains the problem and points to Settings › Apps › Ember Kitchen ›
      Permissions. Check that the explanation is readable.
- [ ] Tap **Save and continue**. The menu loads.
- [ ] Cleartext HTTP is expected: the backend is a local, fictional service. If the connection fails
      with a security error, the network security config was not packaged. Report it.

## 4. Journey checks (expected totals come from the behavior contract)

- [ ] **Normal checkout.** Add 2 × Ember Smash Burger (double patty, bacon, cheddar) and 1 × Charcoal
      Fries (large). Review shows subtotal **$48.85**, tax **$4.03**, total **$52.88**. Place the
      order and note the order number.
- [ ] **Recover after close.** Swipe the app away from recents and reopen it. The same order and
      its status appear; nothing new is placed.
- [ ] **Coupon.** Cart with 2 × burger (single): apply `EMBER10`. The total is **$25.23**.
- [ ] **Outage.** Stop the backend (Ctrl-C), then try to place an order. Expect "wasn't placed and
      you weren't charged" with a retry. Restart the backend *without* `--reset`, tap retry, and
      the order goes through once.
- [ ] **Lost connection mid-order.** Turn on airplane mode right after tapping **Place order**.
      Expect either "wasn't placed" (the request never left) or "Trying again is safe" (the outcome
      is unknown). Turn airplane mode off and retry. You get exactly one order. Check on the
      laptop: `sqlite3 /tmp/ember-phone.db "select order_number,total_minor from orders"`.
- [ ] **Large text.** Settings › Display › Font size at maximum (and Display size at maximum if
      available). The full journey still works, and every button is reachable by scrolling.
- [ ] **TalkBack.** Menu items, prices, option toggles and the total are announced meaningfully.
- [ ] **Rotation and dark mode.** Rotate on the review screen; the cart and total survive. The app
      uses its own light Ember theme; with the system dark theme on, it should look the same and stay readable.

## 5. Optional: reproduce the central scenario on the phone

This one needs the control server, which only listens on the laptop's loopback, so the phone
still cannot reach it.

```bash
backend/build/install/orderproof-backend/bin/orderproof-backend --db /tmp/ember-phone.db --reset \
  --control-token-file ~/.cache/ember-phone.token
# arm: the next order commits, then its response is dropped
curl -fsS -X POST http://127.0.0.1:8788/control/faults \
  -H "Authorization: Bearer $(cat ~/.cache/ember-phone.token)" -H 'Content-Type: application/json' \
  -d '{"kind":"DROP_RESPONSE_AFTER_COMMIT","times":1}'
```

On the phone, order 1 × burger (single, **$14.02**). You see "We couldn't confirm your order" →
**Try again** → confirmation. Then on the laptop:

```bash
sqlite3 /tmp/ember-phone.db "select order_number,total_minor,checkout_attempt_id from orders"
sqlite3 /tmp/ember-phone.db "select order_id,amount_minor,status from payments"
```

Corrected build: **one** order, **one** payment. Delete the token file when you are done.

## 6. What to report back

- [ ] Phone model, Android version, and whether the local-network prompt appeared.
- [ ] The build line from the connect screen (version · candidate · commit).
- [ ] Each checklist item: pass or fail, with a screenshot of any failure.
- [ ] For any failure: `adb logcat -d -s OrderProof:I > phone-app.log`, plus the backend output
      and the database file.
