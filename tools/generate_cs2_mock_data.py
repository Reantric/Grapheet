#!/usr/bin/env python3
"""Generate realistic mock HLTV-rating data for the CS2 top-players race video.

Pipeline mirrors the intended real one:
  per-match ratings (mean trajectory + noise) -> 90-day rolling average
  -> weekly knots -> src/data/cs2/top_players_rolling.csv

The Java scene (Cs2TopPlayersScene) PCHIP-interpolates the knots at render time.

Career arcs are hand-tuned to roughly match reality through mid-2025
(donk's late-2023 breakout, s1mple going inactive, m0NESY's rise, ...);
everything after that is invented. Deterministic via fixed seed.

Run from repo root:  python3 tools/generate_cs2_mock_data.py
Swap in real data later with tools/scrape_hltv.py
"""

from __future__ import annotations

import os
import random
from datetime import date, timedelta

from rolling import rolling_knots, write_knots_csv

SEED = 20260610
MATCH_NOISE_SIGMA = 0.16   # per-match rating spread around the career mean
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "data", "cs2",
                   "top_players_rolling.csv")

# (player, team-label, css-color, [(date, mean-rating), ...])
# Mean trajectories are piecewise-linear between control points.
PLAYERS = [
    ("ZywOo", "Vitality", "#ffd166", [
        ("2023-01-01", 1.27), ("2023-06-01", 1.32), ("2023-11-01", 1.26),
        ("2024-04-01", 1.24), ("2024-10-01", 1.29), ("2025-04-01", 1.26),
        ("2025-10-01", 1.28), ("2026-06-01", 1.27),
    ]),
    ("donk", "Spirit", "#ef476f", [
        ("2023-09-01", 1.08), ("2023-12-01", 1.18), ("2024-02-15", 1.34),
        ("2024-07-01", 1.30), ("2024-12-01", 1.38), ("2025-05-01", 1.31),
        ("2025-11-01", 1.34), ("2026-06-01", 1.31),
    ]),
    ("m0NESY", "G2 / Falcons", "#06d6a0", [
        ("2023-01-01", 1.16), ("2023-08-01", 1.21), ("2024-03-01", 1.18),
        ("2024-09-01", 1.23), ("2025-03-01", 1.21), ("2025-09-01", 1.26),
        ("2026-06-01", 1.28),
    ]),
    ("sh1ro", "C9 / Spirit", "#4cc9f0", [
        ("2023-01-01", 1.24), ("2023-07-01", 1.19), ("2024-02-01", 1.16),
        ("2024-09-01", 1.21), ("2025-03-01", 1.25), ("2025-10-01", 1.21),
        ("2026-06-01", 1.23),
    ]),
    ("NiKo", "G2 / Falcons", "#b388eb", [
        ("2023-01-01", 1.18), ("2023-09-01", 1.14), ("2024-03-01", 1.21),
        ("2024-10-01", 1.16), ("2025-04-01", 1.12), ("2025-11-01", 1.17),
        ("2026-06-01", 1.15),
    ]),
    ("b1t", "NAVI", "#f8961e", [
        ("2023-01-01", 1.09), ("2023-08-01", 1.13), ("2024-04-01", 1.18),
        ("2024-11-01", 1.15), ("2025-05-01", 1.21), ("2025-12-01", 1.18),
        ("2026-06-01", 1.20),
    ]),
    # s1mple goes inactive: his series simply ends (tests line/label retirement).
    ("s1mple", "NAVI", "#f1f1f1", [
        ("2023-01-01", 1.26), ("2023-05-01", 1.22), ("2023-10-15", 1.17),
    ]),
]


def _d(s: str) -> date:
    return date.fromisoformat(s)


def mean_rating(points: list[tuple[date, float]], day: date) -> float:
    if day <= points[0][0]:
        return points[0][1]
    for (d0, r0), (d1, r1) in zip(points, points[1:]):
        if d0 <= day <= d1:
            t = (day - d0).days / max(1, (d1 - d0).days)
            return r0 + (r1 - r0) * t
    return points[-1][1]


def simulate_matches(rng: random.Random,
                     points: list[tuple[date, float]]) -> list[tuple[date, float]]:
    """Matches every ~2-4 days, with a slow player-break around late December."""
    matches = []
    day = points[0][0]
    end = points[-1][0]
    while day <= end:
        if not (day.month == 12 and day.day > 18) and not (day.month == 1 and day.day < 6):
            n_maps = rng.choice((1, 2, 2, 3))
            for _ in range(n_maps):
                r = rng.gauss(mean_rating(points, day), MATCH_NOISE_SIGMA)
                matches.append((day, max(0.35, min(2.15, r))))
        day += timedelta(days=rng.choice((2, 2, 3, 3, 4)))
    return matches


def main() -> None:
    rng = random.Random(SEED)
    rows = []
    for player, team, color, raw_points in PLAYERS:
        points = [(_d(d), r) for d, r in raw_points]
        matches = simulate_matches(rng, points)
        for d, rating in rolling_knots(matches):
            rows.append((player, team, color, d, rating))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_knots_csv(OUT, rows)
    by_player = {}
    for r in rows:
        by_player.setdefault(r[0], []).append(r)
    print(f"wrote {len(rows)} knots -> {os.path.normpath(OUT)}")
    for p, rs in by_player.items():
        print(f"  {p:8s} {rs[0][3]} .. {rs[-1][3]}  ({len(rs)} knots, "
              f"min {min(x[4] for x in rs):.3f}, max {max(x[4] for x in rs):.3f}")


if __name__ == "__main__":
    main()
