#!/usr/bin/env bash
# Runs the Ember Kitchen backend for verification: fresh database, fixed clock, control API on
# loopback with a new random token (written 0600, never printed).
# Usage: scripts/backend.sh start [run-dir]   scripts/backend.sh stop [run-dir]
# For a phone demo without fault controls, run backend/build/install/orderproof-backend/bin/orderproof-backend directly.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
dir="${2:-runs/backend}"
pidfile="$dir/backend.pid"

stop() {
  if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
    kill "$(cat "$pidfile")"
    timeout 30 bash -c "while kill -0 $(cat "$pidfile") 2>/dev/null; do sleep 0.5; done"
  fi
  rm -f "$pidfile"
}

case "${1:-}" in
  start)
    stop
    mkdir -p "$dir"
    rm -f "$dir"/orderproof.db*
    bin=backend/build/install/orderproof-backend/bin/orderproof-backend
    [ -x "$bin" ] || ./gradlew -q :backend:installDist
    "$bin" --db "$dir/orderproof.db" --clock fixed --reset --control-token-file "$dir/control.token" > "$dir/backend.log" 2>&1 &
    echo $! > "$pidfile"
    # Ready when the token exists and the public API answers.
    timeout 60 bash -c "until [ -s '$dir/control.token' ] && curl -fsS -o /dev/null http://127.0.0.1:8787/v1/health; do sleep 0.5; done" \
      || { echo "backend did not start; see $dir/backend.log" >&2; exit 3; }
    echo "backend running (pid $(cat "$pidfile")); db=$dir/orderproof.db token-file=$dir/control.token"
    ;;
  stop) stop ;;
  *) echo "usage: $0 start|stop [run-dir]" >&2; exit 64 ;;
esac
