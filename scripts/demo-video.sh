#!/usr/bin/env bash
# Builds a side-by-side demo of the central scenario from two executed verifier runs.
# Left: the faulty build; right: the corrected build. Captions come from each run's report.json,
# so the video states exactly what the gate concluded; nothing is edited or re-timed.
#
#   scripts/demo-video.sh runs/<faulty-run> runs/<corrected-run> [out.mp4]
set -euo pipefail
faulty="${1:?faulty run dir}" corrected="${2:?corrected run dir}" out="${3:-dist/orderproof-demo.mp4}"
ffmpeg="$(command -v ffmpeg || echo "$HOME/.local/bin/ffmpeg")"
scenario=lostResponseAfterCommit
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

# Line 1 is the headline, line 2 the gate verdict, line 3 what the ledger held for the scenario.
caption() {
  python3 -I - "$1/report.json" "$scenario" <<'PY'
import json, sys
r = json.load(open(sys.argv[1])); g = r["gate"]
e = next(x for x in r["executions"] if x["scenario"] == sys.argv[2] and x["attempt"] == 1)
codes = ", ".join(g["failureCodes"]) or "no violations"
ledger = "; ".join(v["message"] for v in (e.get("oracle") or {}).get("violations", [])) or "ledger: exactly one order and one payment"
print(f'{r["build"]["candidate"].upper()} build · {r["device"]["avdName"]} · API {r["device"]["apiLevel"]}')
print(f'Gate {g["verdict"]} ({codes}) · commit {r["build"]["sourceCommit"][:7]}')
print(ledger)
PY
}
mapfile -t left < <(caption "$faulty")
mapfile -t right < <(caption "$corrected")

esc() { printf '%s' "$1" | sed 's/[{}]//g'; }
cat > "$work/labels.ass" <<ASS
[Script Info]
ScriptType: v4.00+
PlayResX: 1100
PlayResY: 1480
WrapStyle: 0

[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Head,DejaVu Sans,26,&H00EAF4FB,&H00EAF4FB,&H001D2024,&H001D2024,1,0,0,0,100,100,0,0,1,0,0,7,20,20,16,1
Style: Body,DejaVu Sans,17,&H00EAF4FB,&H00EAF4FB,&H001D2024,&H001D2024,0,0,0,0,100,100,0,0,1,0,0,7,20,20,16,1

[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:00.00,9:59:59.00,Head,,20,580,14,,$(esc "${left[0]}")
Dialogue: 0,0:00:00.00,9:59:59.00,Body,,20,580,52,,$(esc "${left[1]}")
Dialogue: 0,0:00:00.00,9:59:59.00,Body,,20,580,78,,$(esc "${left[2]}")
Dialogue: 0,0:00:00.00,9:59:59.00,Head,,570,20,14,,$(esc "${right[0]}")
Dialogue: 0,0:00:00.00,9:59:59.00,Body,,570,20,52,,$(esc "${right[1]}")
Dialogue: 0,0:00:00.00,9:59:59.00,Body,,570,20,78,,$(esc "${right[2]}")
ASS

clip() { echo "$1/runs/run-01/$scenario/attempt-1/recording.mp4"; }
mkdir -p "$(dirname "$out")"
"$ffmpeg" -hide_banner -loglevel error -y -i "$(clip "$faulty")" -i "$(clip "$corrected")" -filter_complex \
  "[0:v]scale=540:1200,setsar=1,pad=550:1480:0:280:color=0x24201D[l];\
   [1:v]scale=540:1200,setsar=1,pad=550:1480:10:280:color=0x24201D[r];\
   [l][r]hstack=inputs=2,subtitles=$work/labels.ass,format=yuv420p" \
  -r 30 -c:v libx264 -preset medium -crf 23 -movflags +faststart "$out"
echo "$out"
