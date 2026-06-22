#!/usr/bin/env python3
"""Generate mock Codeforces-style rating data for the rating-band race video.

Pipeline mirrors the CS2 generator:
  per-contest ratings (piecewise-linear career mean + AR(1) noise)
  -> light rolling smoothing -> biweekly knots
  -> src/data/codeforces/top_coders_rolling.csv

The Java scene (CodeforcesRatingScene) PCHIP-interpolates the knots at render
time and paints the Codeforces rank-tier colour bands behind the lines.

IMPORTANT: every trajectory here is INVENTED for visual testing. The handles are
recognisable, but the arcs are not real ratings. A few lines deliberately start
low and sweep upward across many tiers so the colour bands are easy to see.
Deterministic via a fixed seed.

Run from repo root:  python3 tools/generate_codeforces_mock_data.py
"""

from __future__ import annotations

import os
import random
from datetime import date, timedelta

SEED = 20260616
NOISE_SIGMA = 26.0      # per-step rating jitter around the career mean
NOISE_DECAY = 0.55      # AR(1) memory so the jitter looks like form, not hash
KNOT_DAYS = 14          # biweekly knots
SMOOTH_WINDOW = 3       # rolling average over knots (the "rolling rating")

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "data", "codeforces",
                   "top_coders_rolling.csv")

END = "2026-06-01"

# (handle, css-color, country, [(date, mean-rating), ...control points])
# Mean trajectories are piecewise-linear between control points.
PLAYERS = [
    # --- established top of the leaderboard (live high in the red zone) ---
    ("tourist", "#ffd166", "BY", [
        ("2018-01-01", 3400), ("2019-06-01", 3650), ("2020-06-01", 3560),
        ("2021-06-01", 3760), ("2022-06-01", 3640), ("2023-06-01", 3805),
        ("2024-06-01", 3700), ("2025-06-01", 3860), (END, 3815),
    ]),
    ("jiangly", "#4cc9f0", "CN", [
        ("2018-01-01", 2560), ("2019-06-01", 2780), ("2020-06-01", 2980),
        ("2021-06-01", 3180), ("2022-06-01", 3360), ("2023-06-01", 3520),
        ("2024-06-01", 3650), ("2025-06-01", 3720), (END, 3705),
    ]),
    ("Benq", "#f72585", "US", [
        ("2018-01-01", 3040), ("2019-06-01", 3160), ("2020-06-01", 3250),
        ("2021-06-01", 3300), ("2022-06-01", 3340), ("2023-06-01", 3300),
        ("2024-06-01", 3250), ("2025-06-01", 3210), (END, 3190),
    ]),
    ("Radewoosh", "#fb8500", "PL", [
        ("2018-01-01", 2860), ("2019-06-01", 3060), ("2020-06-01", 3160),
        ("2021-06-01", 3260), ("2022-06-01", 3350), ("2023-06-01", 3300),
        ("2024-06-01", 3380), ("2025-06-01", 3300), (END, 3320),
    ]),
    ("ksun48", "#90e0ef", "CA", [
        ("2018-01-01", 2700), ("2019-06-01", 2860), ("2020-06-01", 2960),
        ("2021-06-01", 3060), ("2022-06-01", 3150), ("2023-06-01", 3100),
        ("2024-06-01", 3050), ("2025-06-01", 3010), (END, 3025),
    ]),
    # --- invented climbers: start low, sweep up across the tiers ---
    # Expert -> CM -> Master -> IM -> GM -> IGM -> LGM
    ("nimbus", "#c77dff", "IN", [
        ("2019-01-01", 1650), ("2019-12-01", 1960), ("2020-10-01", 2240),
        ("2021-08-01", 2480), ("2022-08-01", 2730), ("2023-08-01", 2940),
        ("2024-09-01", 3060), ("2025-09-01", 3120), (END, 3140),
    ]),
    # Candidate Master -> ... -> International Grandmaster
    ("sol_invictus", "#ff7b00", "BR", [
        ("2019-06-01", 1880), ("2020-06-01", 2080), ("2021-06-01", 2280),
        ("2022-06-01", 2420), ("2023-06-01", 2520), ("2024-06-01", 2590),
        ("2025-06-01", 2640), (END, 2660),
    ]),
    # Specialist -> Expert -> CM -> Master -> IM -> Grandmaster
    ("kestrel", "#52b788", "VN", [
        ("2019-03-01", 1500), ("2020-03-01", 1780), ("2021-03-01", 2060),
        ("2022-03-01", 2280), ("2023-03-01", 2430), ("2024-03-01", 2520),
        ("2025-03-01", 2580), (END, 2610),
    ]),
]


def parse(d: str) -> date:
    y, m, dd = (int(x) for x in d.split("-"))
    return date(y, m, dd)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def mean_at(points, day: date) -> float:
    """Piecewise-linear interpolation of the (date, rating) control points."""
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

    # Codeforces ratings are integers and never dip below 0.
    return [(day, max(0, round(v))) for day, v in zip(days, smoothed)]


def main() -> None:
    rng = random.Random(SEED)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)

    rows = []
    for handle, color, country, raw_points in PLAYERS:
        points = [(parse(d), r) for d, r in raw_points]
        for day, rating in build_knots(points, rng):
            rows.append((handle, country, color, day.isoformat(), rating))

    with open(os.path.abspath(OUT), "w", encoding="utf-8") as f:
        f.write("player,country,color,date,rating\n")
        for handle, country, color, day, rating in rows:
            f.write(f"{handle},{country},{color},{day},{rating}\n")

    los = {}
    his = {}
    for handle, _c, _col, _d, rating in rows:
        los[handle] = min(los.get(handle, rating), rating)
        his[handle] = max(his.get(handle, rating), rating)
    print(f"Wrote {len(rows)} rows for {len(PLAYERS)} players to "
          f"{os.path.relpath(os.path.abspath(OUT))}")
    for handle, _color, _country, _pts in PLAYERS:
        print(f"  {handle:14s} {los[handle]:>4d} .. {his[handle]:>4d}")


if __name__ == "__main__":
    main()
