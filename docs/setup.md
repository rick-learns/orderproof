# Setup

Everything runs on one Linux x86_64 machine with KVM. Nothing needs Android Studio.

## Pinned toolchain

| Tool | Version | Where it is pinned |
|---|---|---|
| JDK | Temurin/OpenJDK 21 (bytecode target 17) | `scripts/env.sh` (`JAVA_HOME`), CI `setup-java` |
| Gradle | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.1 | `gradle/libs.versions.toml` |
| Kotlin | 2.4.21 | `gradle/libs.versions.toml` |
| Compose BOM | 2026.09.00 | `gradle/libs.versions.toml` |
| OkHttp | 5.5.1 | `gradle/libs.versions.toml` |
| kotlinx-serialization / coroutines | 1.13.0 / 1.11.0 | `gradle/libs.versions.toml` |
| sqlite-jdbc | 3.54.1.0 | `gradle/libs.versions.toml` |
| AndroidX Test / UiAutomator / Espresso | 1.7.0 / 2.4.1 / 3.7.0 | `gradle/libs.versions.toml` |
| Android SDK platform | `android-37.0` (compileSdk/targetSdk 37, minSdk 26) | `app/build.gradle.kts` |
| Build tools | 36.0.0 and 37.0.0 | installed below |
| Emulator / platform-tools / cmdline-tools | 37.2.12 / 37.0.1 / 23.0 | tested versions |
| System images | `android-37.0;google_apis;x86_64`, `android-34;google_apis;x86_64` | AVDs below |
| ffmpeg (optional, demo video only) | any build with libass | `scripts/demo-video.sh` |

## One-time install

```bash
# 1. JDK 21 in ~/.local/jdk-21 (or export JAVA_HOME to an existing one before sourcing env.sh)
# 2. Android command-line tools in ~/.local/android-sdk/cmdline-tools/latest
#    (or export ANDROID_HOME to an existing SDK)
source scripts/env.sh
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "emulator" "platforms;android-37.0" \
  "build-tools;36.0.0" "build-tools;37.0.0" \
  "system-images;android-37.0;google_apis;x86_64" "system-images;android-34;google_apis;x86_64"

# 3. The two emulator configurations used by the gate
avdmanager create avd -n prs-pixel      -k "system-images;android-37.0;google_apis;x86_64" -d pixel_7
avdmanager create avd -n op-small-api34 -k "system-images;android-34;google_apis;x86_64"   -d small_phone
```

| AVD | Role | Device | Screen | API |
|---|---|---|---|---|
| `prs-pixel` | primary | Pixel 7 | 1080×2400 @ 420 dpi | 37 |
| `op-small-api34` | secondary | small phone | 720×1280 @ 320 dpi | 34 |

KVM: your user needs write access to `/dev/kvm` (`sudo usermod -aG kvm $USER`, then log in again).
Until then, `scripts/emulator.sh` runs the emulator through `sg kvm` automatically.

## Everyday commands

```bash
source scripts/env.sh

scripts/ci.sh                                             # everything without an emulator (~2–3 min warm)
scripts/verify.sh --candidate corrected --runs 3          # primary gate
scripts/verify.sh --candidate corrected --avd op-small-api34 --config secondary
scripts/verify.sh --candidate faulty --expect-failure DUPLICATE_ORDER
scripts/dist.sh --reports runs/<faulty-run> runs/<corrected-run>   # labeled APKs + copied reports
scripts/demo-video.sh runs/<faulty-run> runs/<corrected-run>       # side-by-side MP4 in dist/

./gradlew :app:connectedCorrectedDebugAndroidTest         # Compose UI tests on a running emulator
./gradlew :app:connectedFaultyDebugAndroidTest
scripts/emulator.sh stop emulator-5554
```

Run the backend by hand (public API only; add `--control-token-file FILE` to enable the loopback
control API):

```bash
./gradlew -q :backend:installDist
backend/build/install/orderproof-backend/bin/orderproof-backend --db /tmp/ember.db --reset [--clock fixed]
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `verify.sh` stops at `== emulator` | Read `runs/logs/emulator-<avd>.log`; check `/dev/kvm` access and free disk space |
| Port 8787 or 8788 in use | A backend from an interrupted run: `scripts/backend.sh stop runs/<that run>/backend` |
| A scenario is INCONCLUSIVE | Open its folder in the report: `failure-hierarchy.xml` and `01-failure.png` show the screen at the moment of failure. Reproduce with the `smoke-one.sh` command in the report's *Reproduce* section |
| Lint fails on a new warning | Warnings are errors (`app/build.gradle.kts` › `lint`). Fix the warning; suppress it only with a comment explaining why |
