"""Prints the generated part of docs/results.md from executed runs.

Usage: python3 -I scripts/results-md.py FINAL_RUN_DIR... [--history EARLIER_RUN_DIR...]
"""
import json
import sys
from pathlib import Path

args = sys.argv[1:]
split = args.index("--history") if "--history" in args else len(args)
load = lambda ds: [(Path(d), json.loads((Path(d) / "report.json").read_text())) for d in ds]
reports, history = load(args[:split]), load(args[split + 1:])
out = []
p = out.append

p("# Executed results\n")
p("The gate tables are generated from each run's `report.json` and were not edited by hand. Every run")
p("executed on an Android emulator on one Linux host. `scripts/dist.sh --reports` copies the full bundles")
p("(HTML report, screenshots, recordings, logs, backend events, ledgers) of the final runs to")
p("`dist/reports/<run>/`; bundles are not committed. Results recorded outside the gate are in the last section.\n")

p("## Gate verdicts\n")
p("| Run | Config | Candidate | Device | Runs | Clean | Verdict | Failure codes | Expectation |")
p("|---|---|---|---|---:|---:|---|---|---|")
for d, r in reports:
    g, dev, b = r["gate"], r["device"], r["build"]
    exp = r["gate"]["expectation"]
    exp_s = "—" if exp is None else ("met" if exp["met"] else "**not met**") + f" (`{exp['expectedFailureCode']}`)"
    codes = ", ".join(f"`{c}`" for c in g["failureCodes"]) or "—"
    p(f"| `{d.name}` | {r['configLabel']} | {b['candidate']} | {dev['avdName']}, API {dev['apiLevel']}, {dev['screenSize']} @ {dev['density']} "
      f"| {g['runs']} | {g['cleanRuns']}/{g['requiredCleanRuns']} | **{g['verdict']}** | {codes} | {exp_s} |")

p("\n## Build identity\n")
p("| Run | Source commit | Dirty | Commit reported by app | App APK sha256 | Test APK sha256 |")
p("|---|---|---|---|---|---|")
for d, r in reports:
    b = r["build"]
    p(f"| `{d.name}` | `{b['sourceCommit'][:12]}` | {b['sourceDirty']} | {', '.join(b['reportedCommits'])} | `{b['appApkSha256'][:16]}…` | `{b['testApkSha256'][:16]}…` |")

p("\n## Scenario outcomes (first attempts; reruns in parentheses)\n")
scenarios = []
for _, r in reports:
    for e in r["executions"]:
        if e["scenario"] not in scenarios:
            scenarios.append(e["scenario"])
p("| Scenario | " + " | ".join(f"`{d.name}`" for d, _ in reports) + " |")
p("|---|" + "---|" * len(reports))
for s in scenarios:
    cells = []
    for _, r in reports:
        ex = [e for e in r["executions"] if e["scenario"] == s]
        firsts = [e["outcome"] for e in sorted(ex, key=lambda e: e["run"]) if e["attempt"] == 1]
        reruns = [e["outcome"] for e in ex if e["attempt"] > 1]
        short = {"PASS": "pass", "PRODUCT_FAILURE": "PRODUCT", "INFRASTRUCTURE_FAILURE": "INFRA", "INCONCLUSIVE": "INCONCL."}
        cell = " ".join(short[o] for o in firsts) or "—"
        if reruns:
            cell += " (" + " ".join(short[o] for o in reruns) + ")"
        cells.append(cell)
    p(f"| {s} | " + " | ".join(cells) + " |")

p("\n## Why the faulty candidate failed\n")
for d, r in reports:
    if r["build"]["candidate"] != "faulty":
        continue
    p(f"`{d.name}`:\n")
    for e in r["executions"]:
        if e["attempt"] == 1 and e["outcome"] != "PASS":
            for reason in e["reasons"]:
                p(f"- **{e['scenario']}**: {reason}")
    p("")

p("## Flakiness\n")
flaky = [(d.name, f) for d, r in reports for f in r["gate"]["flaky"]]
if flaky:
    for name, f in flaky:
        p(f"- `{name}`: {f}")
else:
    p("No scenario was flaky in these runs: every first attempt matched its rerun, or there was no rerun.")

p("\n## Timings (diagnostics only; no verdict depends on them)\n")
p("| Run | Total | Slowest scenario |")
p("|---|---:|---|")
for d, r in reports:
    t = r["timings"]
    slow = max(t["perScenarioMillis"].items(), key=lambda kv: max(kv[1]))
    p(f"| `{d.name}` | {t['totalMillis'] / 60000:.1f} min | {slow[0]} ({max(slow[1]) / 1000:.0f} s) |")

if history:
    p("\n## Earlier runs (superseded by the runs above)\n")
    p("Kept for the record, including the one that did not meet its expectation; see")
    p("[failure-and-recovery.md](failure-and-recovery.md#4-harness-and-infrastructure-problems-found-on-the-way).\n")
    p("Runs on a dirty tree were development runs; some ran only the scenarios being worked on.\n")
    p("| Run | Commit | Candidate | Device | Scenarios | Clean | Verdict | Failure codes | Expectation | Flaky |")
    p("|---|---|---|---|---:|---:|---|---|---|---|")
    for d, r in history:
        g, b, dev = r["gate"], r["build"], r["device"]
        exp = g["expectation"]
        exp_s = "—" if exp is None else ("met" if exp["met"] else "**not met**")
        p(f"| `{d.name}` | `{b['sourceCommit'][:7]}`{' (dirty)' if b['sourceDirty'] else ''} | {b['candidate']} | {dev['avdName']} | "
          f"{len({e['scenario'] for e in r['executions']})} | {g['cleanRuns']}/{g['requiredCleanRuns']} | {g['verdict']} | {', '.join(g['failureCodes']) or '—'} | {exp_s} | {len(g['flaky'])} |")

print("\n".join(out))
