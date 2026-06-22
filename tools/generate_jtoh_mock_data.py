#!/usr/bin/env python3
"""Generate mock JToH-style difficulty-climb data for the difficulty-band race.

Pipeline mirrors the Codeforces generator:
  per-session difficulty (piecewise-linear career mean + AR(1) noise)
  -> light rolling smoothing -> biweekly knots
  -> src/data/jtoh/top_climbers_rolling.csv

The Java scene (JtohDifficultyScene) PCHIP-interpolates the knots at render
time and paints the JToH difficulty-tier colour bands (Easy .. Extreme, ratings
1..9) behind the lines. Each climber's y-value is the hardest tower difficulty
they have cleared, on the JToH 1..10 numeric scale (a tower rated 7.43 sits
mid-Remorseless).

IMPORTANT: every trajectory here is INVENTED for visual testing. The handles
look like climber names, but the arcs are not real records. Several lines
deliberately start low and sweep upward across many tiers so the colour bands
are easy to see. Deterministic via a fixed seed.

Run from repo root:  python3 tools/generate_jtoh_mock_data.py
"""

from __future__ import annotations

import os
import random
from datetime import date, timedelta

SEED = 20260618
NOISE_SIGMA = 0.085     # per-step difficulty jitter around the career mean
NOISE_DECAY = 0.55      # AR(1) memory so the jitter looks like form, not hash
KNOT_DAYS = 14          # biweekly knots
SMOOTH_WINDOW = 3       # rolling average over knots (the "rolling difficulty")

DIFF_MIN = 1.0          # Easy floor — nobody is rated below Easy here
DIFF_MAX = 9.95         # just shy of Terrifying (rating 10)

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "data", "jtoh",
                   "top_climbers_rolling.csv")

END = "2026-06-01"

# (handle, css-color, region, [(date, mean-difficulty), ...control points])
# Mean trajectories are piecewise-linear between control points. The css-color
# is the LINE colour (distinct from the tier bands behind it), region is a free
# tag carried through unchanged.
CLIMBERS = [
    # --- veterans living at the top of the chart (Insane .. Extreme) ---
    ("Trihard", "#ffd166", "NA", [
        ("2019-01-01", 7.6), ("2020-06-01", 8.2), ("2021-06-01", 8.6),
        ("2022-06-01", 8.9), ("2023-06-01", 9.1), ("2024-06-01", 9.0),
        ("2025-06-01", 9.2), (END, 9.15),
    ]),
    ("Zephyrine", "#4cc9f0", "EU", [
        ("2019-01-01", 7.0), ("2020-06-01", 7.5), ("2021-06-01", 8.0),
        ("2022-06-01", 8.3), ("2023-06-01", 8.6), ("2024-06-01", 8.7),
        ("2025-06-01", 8.85), (END, 8.9),
    ]),
    ("Vortexity", "#f72585", "AS", [
        ("2019-06-01", 6.4), ("2020-06-01", 6.9), ("2021-06-01", 7.4),
        ("2022-06-01", 7.8), ("2023-06-01", 8.1), ("2024-06-01", 8.25),
        ("2025-06-01", 8.2), (END, 8.3),
    ]),
    # --- the marquee climb: Easy all the way up to Extreme ---
    ("AscendKid", "#c77dff", "NA", [
        ("2019-03-01", 1.2), ("2020-03-01", 2.6), ("2021-03-01", 4.0),
        ("2022-03-01", 5.4), ("2023-03-01", 6.7), ("2024-03-01", 7.8),
        ("2025-03-01", 8.7), (END, 9.05),
    ]),
    # Hard -> Insane
    ("PylonPusher", "#90e0ef", "OCE", [
        ("2019-05-01", 3.0), ("2020-05-01", 4.1), ("2021-05-01", 5.2),
        ("2022-05-01", 6.2), ("2023-05-01", 7.0), ("2024-05-01", 7.6),
        ("2025-05-01", 7.9), (END, 8.05),
    ]),
    # Medium -> Remorseless
    ("WrapStrat", "#fb8500", "EU", [
        ("2019-04-01", 2.1), ("2020-04-01", 3.2), ("2021-04-01", 4.3),
        ("2022-04-01", 5.3), ("2023-04-01", 6.2), ("2024-04-01", 6.8),
        ("2025-04-01", 7.1), (END, 7.25),
    ]),
    # Difficult -> Intense plateau then a late push
    ("CoilJumper", "#52b788", "SA", [
        ("2019-07-01", 4.0), ("2020-07-01", 4.6), ("2021-07-01", 5.1),
        ("2022-07-01", 5.5), ("2023-07-01", 5.7), ("2024-07-01", 6.1),
        ("2025-07-01", 6.5), (END, 6.7),
    ]),
    # Easy -> Challenging, the slow-but-steady grinder
    ("TrussGremlin", "#ff7b00", "AF", [
        ("2019-02-01", 1.0), ("2020-02-01", 2.0), ("2021-02-01", 3.0),
        ("2022-02-01", 3.9), ("2023-02-01", 4.6), ("2024-02-01", 5.1),
        ("2025-02-01", 5.4), (END, 5.55),
    ]),
]


def parse(d: str) -> date:
    y, m, dd = (int(x) for x in d.split("-"))
    return date(y, m, dd)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def mean_at(points, day: date) -> float:
    """Piecewise-linear interpolation of the (date, difficulty) control points."""
    if day <= points[0][0]:
        return float(points[0][1])
    if day >= points[-1][0]:
        return float(points[-1][1])
    for (d0, r0), (d1, r1) in zip(points, points[1:]):
        if d0 <= day <= d1:
            span = (d1 - d0).days or 1
            return lerp(r0, r1, (day - d0).days / span)
    return float(points[-1][1])


def build_knots(points, rng):
    start, stop = points[0][0], points[-1][0]
    days = []
    d = start
    while d <= stop:
        days.append(d)
        d += timedelta(days=KNOT_DAYS)
    if days[-1] != stop:
        days.append(stop)

    # AR(1) noise around the career mean, then a short rolling average.
    raw = []
    resid = 0.0
    for day in days:
        resid = NOISE_DECAY * resid + rng.gauss(0, NOISE_SIGMA)
        raw.append(mean_at(points, day) + resid)

    smoothed = []
    for i in range(len(raw)):
        lo = max(0, i - SMOOTH_WINDOW + 1)
        window = raw[lo:i + 1]
        smoothed.append(sum(window) / len(window))

    # Difficulty is a 1..10 decimal; clamp to the chart and keep two places.
    return [(day, round(min(DIFF_MAX, max(DIFF_MIN, v)), 2))
            for day, v in zip(days, smoothed)]


def main() -> None:
    rng = random.Random(SEED)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    rows = []
    for handle, color, region, raw_points in CLIMBERS:
        points = [(parse(d), r) for d, r in raw_points]
        for day, diff in build_knots(points, rng):
            rows.append((handle, region, color, day.isoformat(), diff))

    with open(os.path.abspath(OUT), "w", encoding="utf-8") as f:
        f.write("player,country,color,date,rating\n")
        for handle, region, color, day, diff in rows:
            f.write(f"{handle},{region},{color},{day},{diff}\n")

    los = {}
    his = {}
    for handle, _r, _col, _d, diff in rows:
        los[handle] = min(los.get(handle, diff), diff)
        his[handle] = max(his.get(handle, diff), diff)
    print(f"Wrote {len(rows)} rows for {len(CLIMBERS)} climbers to "
          f"{os.path.relpath(os.path.abspath(OUT))}")
    for handle, _color, _region, _pts in CLIMBERS:
        print(f"  {handle:14s} {los[handle]:>5.2f} .. {his[handle]:>5.2f}")


if __name__ == "__main__":
    main()
