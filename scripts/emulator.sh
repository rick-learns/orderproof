#!/usr/bin/env bash
# Starts a headless emulator for an AVD (or reuses a running one) and prints its adb serial.
# Usage: scripts/emulator.sh start <avd>     scripts/emulator.sh stop <serial>
set -euo pipefail
source "$(dirname "$0")/env.sh"

serial_for_avd() {
  local avd="$1" s
  for s in $(adb devices | awk '/^emulator-[0-9]+\tdevice$/ {print $1}'); do
    [ "$(adb -s "$s" shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')" = "$avd" ] && { echo "$s"; return 0; }
  done
  return 1
}

free_port() {
  local port=5554
  while adb devices | grep -q "^emulator-$port"; do port=$((port + 2)); done
  echo "$port"
}

# The emulator needs /dev/kvm. A user just added to the kvm group can use `sg` before re-login.
run_with_kvm() {
  if [ -w /dev/kvm ]; then "$@"; else sg kvm -c "$(printf '%q ' "$@")"; fi
}

case "${1:-}" in
  start)
    avd="${2:?avd name required}"
    if s=$(serial_for_avd "$avd"); then echo "$s"; exit 0; fi
    port=$(free_port)
    mkdir -p runs/logs
    run_with_kvm emulator -avd "$avd" -port "$port" -no-window -no-audio -no-boot-anim -no-snapshot-save \
      -gpu swiftshader_indirect > "runs/logs/emulator-$avd.log" 2>&1 &
    serial="emulator-$port"
    timeout 600 adb -s "$serial" wait-for-device
    timeout 600 bash -c "until [ \"\$(adb -s $serial shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')\" = 1 ]; do sleep 2; done"
    echo "$serial"
    ;;
  stop)
    adb -s "${2:?serial required}" emu kill
    ;;
  *)
    echo "usage: $0 start <avd> | stop <serial>" >&2; exit 64 ;;
esac
