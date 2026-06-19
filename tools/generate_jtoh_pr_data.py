#!/usr/bin/env python3
"""Source the three real JToH "completed towers" workbooks from ~/Downloads and
flatten them into one completions CSV the JtohDifficultyScene reads.

One row per tower completion:
    player,color,datetime,code,difficulty

  player     - Amog / fairylog / Eggnote (assigned per source file)
  color      - that player's LINE colour (the ledger text colour is derived
               from the tower difficulty instead, in the Java scene)
  datetime   - ISO local date-time of the completion (drives the x axis and the
               ledger ordering; sub-day precision matters when a player clears
               several towers in one day)
  code       - short tower code (ToAST, SoBJ, ...) shown in the ledger
  difficulty - JToH numeric difficulty value (the y axis)

The scene builds each player's smooth PCHIP line from their PR knots (the
running max of difficulty over time) and uses the full row list for the ledger
feed. So this tool keeps EVERY completion; the PR/ledger split happens in Java.

Run from repo root:  python3 tools/generate_jtoh_pr_data.py
"""

from __future__ import annotations

import os
from datetime import datetime, timedelta

import openpyxl

DOWNLOADS = os.path.expanduser("~/Downloads")

# (player, line-colour, source workbook). Order is cosmetic.
SOURCES = [
    ("Amog", "#ffd166", "completed_towers_full_readable_table.xlsx"),
    ("fairylog", "#4cc9f0", "completed_towers_readable_table.xlsx"),
    ("Eggnote", "#ff5ca8", "completed_towers_updated_readable_table.xlsx"),
]

# Inline single-completion player (not from a workbook): flat PR at 1.25.
SNOW = ("snow", "#cfe8ff", [
    ("2025-07-17T16:24:00", "ToAST", 1.25),
])

# Gag "line" player: NOT a PR progression. mode=line tells the scene to plot
# these rows directly as PCHIP knots (no running-max), and they raise no ledger
# events. Lintahlo drops from 1 to -1 over ~3 months, then retires so the
# scene's CS2-style fade removes the label while the dimmed line stays drawn.
# Knots are (days-after-first-completion, value).
LINTAHLO = ("Lintahlo", "#9b5de5")
LINTAHLO_KNOTS = [(0, 1.0), (90, -1.0)]

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "data", "jtoh",
                   "completions.csv")


def col(header, *names):
    """First matching column index for any of the candidate header names."""
    lookup = {str(h): i for i, h in enumerate(header)}
    for name in names:
        if name in lookup:
            return lookup[name]
    raise KeyError(f"none of {names} in {list(header)}")


def read_completions(path):
    wb = openpyxl.load_workbook(path, data_only=True, read_only=True)
    ws = wb["Completed Towers"] if "Completed Towers" in wb.sheetnames else wb.worksheets[0]
    rows = list(ws.iter_rows(values_only=True))
    header = rows[0]
    ci = col(header, "Code")
    di = col(header, "Difficulty Value")
    dci = col(header, "Date Completed")
    out = []
    for r in rows[1:]:
        if not r or r[0] in (None, ""):
            continue
        code, value, when = r[ci], r[di], r[dci]
        if not code or not isinstance(value, (int, float)) or when is None:
            continue
        out.append((when, str(code).strip(), float(value)))
    return out


def main() -> None:
    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    rows = []  # (when, player, color, mode, code, value)
    summary = []
    for player, color, fname in SOURCES:
        comps = read_completions(os.path.join(DOWNLOADS, fname))
        comps.sort(key=lambda c: c[0])
        for when, code, value in comps:
            rows.append((when, player, color, "pr", code, value))
        # PR knots = running max, for the summary only (Java recomputes them).
        prs, mx = [], -1.0
        for when, code, value in comps:
            if value > mx + 1e-9:
                prs.append((code, value))
                mx = value
        summary.append((player, len(comps), prs,
                        comps[0][0] if comps else None,
                        comps[-1][0] if comps else None))

    # snow: one inline completion, plotted as a flat PR.
    sp, scolor, scomps = SNOW
    for ds, code, value in scomps:
        rows.append((datetime.fromisoformat(ds), sp, scolor, "pr", code, value))
    summary.append((sp, len(scomps), [(c, v) for _, c, v in scomps], None, None))

    # Lintahlo: an explicit declining line that retires after ~3 months.
    real_dates = [r[0] for r in rows]
    dmin = min(real_dates)
    lp, lcolor = LINTAHLO
    for days_after_start, value in LINTAHLO_KNOTS:
        rows.append((dmin + timedelta(days=days_after_start), lp, lcolor, "line", "-", value))

    rows.sort(key=lambda r: r[0])
    with open(os.path.abspath(OUT), "w", encoding="utf-8") as f:
        f.write("player,color,mode,datetime,code,difficulty\n")
        for when, player, color, mode, code, value in rows:
            f.write(f"{player},{color},{mode},{when.isoformat()},{code},{value}\n")

    print(f"Wrote {len(rows)} rows to {os.path.relpath(os.path.abspath(OUT))}")
    for player, n, prs, first, last in summary:
        span_s = f"{first.date()} .. {last.date()}" if first else "(inline)"
        print(f"  {player:9s} {n:4d} completions  {span_s}  "
              f"{len(prs)} PR knots, top {prs[-1][1] if prs else '?'}")
    print(f"  Lintahlo  line {LINTAHLO_KNOTS[0][1]:g} -> {LINTAHLO_KNOTS[-1][1]:g}  "
          f"{dmin.date()} .. {(dmin + timedelta(days=LINTAHLO_KNOTS[-1][0])).date()}")


if __name__ == "__main__":
    main()
