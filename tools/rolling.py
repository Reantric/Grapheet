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


def rolling_knots(matches: list[tuple[date, float]],
                  end: date | None = None) -> list[tuple[date, float]]:
    """matches: (match_date, rating) sorted ascending. Returns weekly knots.

    The final knot is always emitted exactly at `end` (default: the last
    match date), so every player whose career reaches the dataset end gets
    a line ending on the SAME day — the race scene draws all front lines to
    a common x instead of scattering ends across each player's weekly grid.
    """
    if not matches:
        return []
    matches = sorted(matches, key=lambda m: m[0])
    first, last = matches[0][0], matches[-1][0]
    end = end or last
    knots: list[tuple[date, float]] = []

    def window_average(day: date) -> float | None:
        window_start = day - timedelta(days=WINDOW_DAYS)
        in_window = [r for d, r in matches if window_start <= d <= day]
        if len(in_window) < MIN_MATCHES_IN_WINDOW:
            return None
        return sum(in_window) / len(in_window)

    day = first + timedelta(days=WINDOW_DAYS // 2)
    while day < end:
        value = window_average(day)
        if value is not None:
            knots.append((day, value))
        day += timedelta(days=KNOT_EVERY_DAYS)
    value = window_average(end)
    if value is not None and (not knots or knots[-1][0] != end):
        knots.append((end, value))
    return knots


def write_knots_csv(path: str, rows: list[tuple[str, str, str, date, float]]) -> None:
    with open(path, "w", newline="") as f:
        # LF line endings so regenerating never produces a whole-file diff
        # against the committed CSV.
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["player", "team", "color", "date", "rating"])
        for player, team, color, d, rating in rows:
            w.writerow([player, team, color, d.isoformat(), f"{rating:.4f}"])
