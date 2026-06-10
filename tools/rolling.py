"""Shared helpers: turn per-match HLTV ratings into rolling-average knots.

A "knot" row is (player, team, color, ISO date, rolling rating). The Java scene
PCHIP-interpolates between knots, so weekly knots are plenty.
"""

from __future__ import annotations

import csv
from datetime import date, timedelta

WINDOW_DAYS = 90          # 3-month rolling window
KNOT_EVERY_DAYS = 7       # emit one knot per week
MIN_MATCHES_IN_WINDOW = 8 # below this the rolling average is too noisy to trust


def rolling_knots(matches: list[tuple[date, float]]) -> list[tuple[date, float]]:
    """matches: (match_date, rating) sorted ascending. Returns weekly knots."""
    if not matches:
        return []
    matches = sorted(matches, key=lambda m: m[0])
    first, last = matches[0][0], matches[-1][0]
    knots: list[tuple[date, float]] = []
    day = first + timedelta(days=WINDOW_DAYS // 2)
    lo = 0
    while day <= last:
        window_start = day - timedelta(days=WINDOW_DAYS)
        while lo < len(matches) and matches[lo][0] < window_start:
            lo += 1
        in_window = [r for d, r in matches[lo:] if d <= day]
        if len(in_window) >= MIN_MATCHES_IN_WINDOW:
            knots.append((day, sum(in_window) / len(in_window)))
        day += timedelta(days=KNOT_EVERY_DAYS)
    return knots


def write_knots_csv(path: str, rows: list[tuple[str, str, str, date, float]]) -> None:
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["player", "team", "color", "date", "rating"])
        for player, team, color, d, rating in rows:
            w.writerow([player, team, color, d.isoformat(), f"{rating:.4f}"])
