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

# Every career that reaches the dataset end gets its final knot exactly here,
# so all live lines end on the same x in the race scene.
END = "2026-06-01"

# (player, css-color, [(date, team), ...transfers], [(date, mean-rating), ...])
# Mean trajectories are piecewise-linear between control points. Teams are
# emitted per knot (the scene shows the era-correct logo through transfers);
# team names must match src/data/cs2/team_logos/<team>.png.
PLAYERS = [
    # Yearly anchors from HLTV top-20 articles: 2019 1.30 (#1 as a rookie),
    # 2020 1.30 (#1), 2021 1.29 (#2), 2022 1.27 (#2), 2023 1.31 (#1).
    ("ZywOo", "#ffd166",
     [("2018-10-01", "Vitality")], [
        ("2018-10-01", 1.16), ("2019-04-01", 1.30), ("2019-10-01", 1.31),
        ("2020-04-01", 1.30), ("2020-11-01", 1.29), ("2021-05-01", 1.28),
        ("2021-12-01", 1.30), ("2022-07-01", 1.27),
        ("2023-01-01", 1.28), ("2023-06-01", 1.32), ("2023-11-01", 1.26),
        ("2024-04-01", 1.24), ("2024-10-01", 1.29), ("2025-04-01", 1.26),
        ("2025-10-01", 1.28), (END, 1.27),
    ]),
    # Spirit main roster Jul 2023; tier-1 LAN breakout Dec 2023 (BetBoom
    # Dacha MVP at 16), #1 of 2024.
    ("donk", "#ef476f",
     [("2023-07-05", "Spirit")], [
        ("2023-09-01", 1.08), ("2023-12-01", 1.18), ("2024-02-15", 1.34),
        ("2024-07-01", 1.30), ("2024-12-01", 1.38), ("2025-05-01", 1.31),
        ("2025-11-01", 1.34), (END, 1.31),
    ]),
    # G2 debut Jan 2022 at 16 (#7 that year, 1.17), #4 of 2023 (~1.20);
    # to Falcons Apr 14, 2025.
    ("m0NESY", "#06d6a0",
     [("2022-01-03", "G2"), ("2025-04-14", "Falcons")], [
        ("2022-01-03", 1.10), ("2022-07-01", 1.18), ("2022-12-01", 1.15),
        ("2023-01-01", 1.17), ("2023-08-01", 1.21), ("2024-03-01", 1.18),
        ("2024-09-01", 1.23), ("2025-03-01", 1.21), ("2025-09-01", 1.26),
        (END, 1.28),
    ]),
    # Gambit main Oct 2020, #4 of 2021 (1.24), statistical peak 2022 (1.28,
    # the year's highest raw rating), 2023 slump (~1.16, benched from C9
    # late Oct), Spirit Dec 17, 2023.
    ("sh1ro", "#4cc9f0",
     [("2020-10-05", "Gambit"), ("2022-04-24", "Cloud9"),
      ("2023-12-17", "Spirit")], [
        ("2020-10-05", 1.14), ("2021-03-01", 1.25), ("2021-10-01", 1.22),
        ("2022-06-01", 1.28), ("2023-01-01", 1.23), ("2023-07-01", 1.18),
        ("2024-02-01", 1.16), ("2024-09-01", 1.21), ("2025-03-01", 1.25),
        ("2025-10-01", 1.21), (END, 1.23),
    ]),
    # 2017 ~1.22 (#2), 2018 ~1.19 (#3), 2019 IGL dip (~1.15, #11), left FaZe
    # for G2 Oct 2020, 2021 1.21 (#3), 2023 resurgence (~1.21, #2), Falcons
    # Jan 3, 2025.
    ("NiKo", "#b388eb",
     [("2016-11-15", "mousesports"), ("2017-02-19", "FaZe"),
      ("2020-10-28", "G2"), ("2025-01-03", "Falcons")], [
        ("2016-11-15", 1.25), ("2017-05-01", 1.20), ("2018-01-01", 1.22),
        ("2018-09-01", 1.17), ("2019-05-01", 1.13), ("2019-12-01", 1.16),
        ("2020-07-01", 1.20), ("2021-03-01", 1.23), ("2021-10-01", 1.21),
        ("2022-05-01", 1.17), ("2023-01-01", 1.18), ("2023-07-01", 1.22),
        ("2024-01-01", 1.18), ("2024-10-01", 1.16), ("2025-04-01", 1.12),
        ("2025-11-01", 1.17), (END, 1.15),
    ]),
    # NAVI main Dec 2020, #9 of 2021 (~1.16), #16 of 2022 (~1.10), 2023 low
    # (~1.05), 2024 rebound (won PGL Copenhagen).
    ("b1t", "#f8961e",
     [("2020-12-20", "NAVI")], [
        ("2020-12-20", 1.05), ("2021-06-01", 1.16), ("2021-11-01", 1.19),
        ("2022-06-01", 1.09), ("2023-02-01", 1.06), ("2023-09-01", 1.11),
        ("2024-04-01", 1.18), ("2024-11-01", 1.15), ("2025-05-01", 1.21),
        ("2025-12-01", 1.18), (END, 1.20),
    ]),
    # 2017 1.19 (#8), 2018 1.33 (#1), 2019 1.29 (#2), 2020 1.29 (#2),
    # 2021 ~1.34 (#1, career best), 2022 1.25 (#1), 2023 1.18 then inactive:
    # his series simply ends (tests line/label retirement).
    ("s1mple", "#f1f1f1",
     [("2016-11-15", "NAVI")], [
        ("2016-11-15", 1.22), ("2017-06-01", 1.18), ("2018-01-01", 1.29),
        ("2018-08-01", 1.35), ("2019-04-01", 1.29), ("2019-11-01", 1.27),
        ("2020-06-01", 1.29), ("2021-02-01", 1.31), ("2021-09-01", 1.36),
        ("2022-04-01", 1.26), ("2022-11-01", 1.25), ("2023-05-01", 1.21),
        ("2023-10-15", 1.17),
    ]),
    # Era players: give 2017-2022 a real race (and exercise mid-video
    # retirements). coldzera fades out of tier 1 in 2021.
    ("coldzera", "#d62828",
     [("2016-11-15", "SK"), ("2018-07-01", "MIBR"), ("2019-09-25", "FaZe")], [
        ("2016-11-15", 1.29), ("2017-07-01", 1.25), ("2018-02-01", 1.21),
        ("2018-10-01", 1.15), ("2019-05-01", 1.18), ("2019-12-01", 1.13),
        ("2020-08-01", 1.10), ("2021-06-01", 1.05),
    ]),
    ("dev1ce", "#3a86ff",
     [("2016-11-15", "Astralis"), ("2021-04-26", "NIP"),
      ("2022-12-20", "Astralis")], [
        ("2016-11-15", 1.17), ("2017-07-01", 1.14), ("2018-03-01", 1.24),
        ("2018-11-01", 1.27), ("2019-07-01", 1.23), ("2020-03-01", 1.25),
        ("2020-12-01", 1.20), ("2021-08-01", 1.16), ("2022-05-01", 1.06),
        ("2023-01-01", 1.17), ("2023-09-01", 1.21), ("2024-05-01", 1.17),
        ("2025-01-01", 1.20), ("2025-09-01", 1.16), (END, 1.18),
    ]),
    ("electronic", "#8d99ae",
     [("2017-09-01", "NAVI"), ("2024-01-22", "VP")], [
        ("2017-09-01", 1.12), ("2018-04-01", 1.22), ("2018-12-01", 1.19),
        ("2019-08-01", 1.21), ("2020-04-01", 1.16), ("2021-01-01", 1.19),
        ("2021-09-01", 1.16), ("2022-06-01", 1.12), ("2023-02-01", 1.11),
        ("2023-10-01", 1.08), ("2024-06-01", 1.12), ("2025-02-01", 1.08),
        ("2025-10-01", 1.06), (END, 1.07),
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


def team_at(transfers: list[tuple[date, str]], day: date) -> str:
    team = transfers[0][1]
    for d, t in transfers:
        if d <= day:
            team = t
    return team


def main() -> None:
    rng = random.Random(SEED)
    rows = []
    for player, color, raw_transfers, raw_points in PLAYERS:
        points = [(_d(d), r) for d, r in raw_points]
        transfers = [(_d(d), t) for d, t in raw_transfers]
        matches = simulate_matches(rng, points)
        # Careers that reach the dataset end get their final knot exactly ON
        # the end date, so all live lines end at the same x in the scene.
        align_end = points[-1][0] if raw_points[-1][0] == END else None
        for d, rating in rolling_knots(matches, end=align_end):
            rows.append((player, team_at(transfers, d), color, d, rating))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    write_knots_csv(OUT, rows)
    by_player = {}
    for r in rows:
        by_player.setdefault(r[0], []).append(r)
    print(f"wrote {len(rows)} knots -> {os.path.normpath(OUT)}")
    for p, rs in by_player.items():
        print(f"  {p:8s} {rs[0][3]} .. {rs[-1][3]}  ({len(rs)} knots, "
              f"min {min(x[4] for x in rs):.3f}, max {max(x[4] for x in rs):.3f})")


if __name__ == "__main__":
    main()
