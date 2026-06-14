#!/usr/bin/env python3
"""Best-effort HLTV scraper: per-match ratings -> rolling knots CSV.

NOT runnable from a datacenter IP (HLTV sits behind Cloudflare); run it from a
residential connection. Page layout selectors may need a touch-up — this was
written against the stats/players/matches table layout and could not be
verified from the sandbox that authored it. The output schema is identical to
tools/generate_cs2_mock_data.py, so the Java scene doesn't care which one fed it.

Usage:
  pip install requests beautifulsoup4
  python3 tools/scrape_hltv.py

Edit PLAYERS below: (hltv_player_id, url_slug, display_name, team, color).
"""

from __future__ import annotations

import os
import sys
import time
from datetime import date, datetime

from rolling import rolling_knots, write_knots_csv

try:
    import requests
    from bs4 import BeautifulSoup
except ImportError:
    sys.exit("pip install requests beautifulsoup4")

START = "2023-01-01"
END = date.today().isoformat()
PAGE_SIZE = 50           # HLTV match-history page size
SLEEP_SECONDS = 4.0      # be polite; HLTV rate-limits hard
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "data", "cs2",
                   "top_players_rolling.csv")

PLAYERS = [
    (11893, "zywoo",  "ZywOo",  "Vitality", "#ffd166"),
    (19230, "donk",   "donk",   "Spirit",   "#ef476f"),
    (18987, "m0nesy", "m0NESY", "Falcons",  "#06d6a0"),
    (16920, "sh1ro",  "sh1ro",  "Spirit",   "#4cc9f0"),
    (3741,  "niko",   "NiKo",   "Falcons",  "#b388eb"),
    (18927, "b1t",    "b1t",    "NAVI",     "#f8961e"),
]

HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) "
                   "Chrome/131.0.0.0 Safari/537.36"),
    "Accept-Language": "en-US,en;q=0.9",
}


def fetch_matches(session: requests.Session, player_id: int, slug: str):
    """Paginate the player's match history, yielding (date, rating)."""
    offset = 0
    while True:
        url = (f"https://www.hltv.org/stats/players/matches/{player_id}/{slug}"
               f"?startDate={START}&endDate={END}&offset={offset}")
        resp = session.get(url, headers=HEADERS, timeout=20)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "html.parser")
        rows = soup.select("table.stats-table tbody tr")
        if not rows:
            return
        for row in rows:
            cells = row.find_all("td")
            if len(cells) < 2:
                continue
            day_link = cells[0].find("a")
            if day_link is None:
                continue
            day = datetime.strptime(day_link.get_text(strip=True), "%d/%m/%y").date()
            rating_text = cells[-1].get_text(strip=True).split()[0]
            try:
                yield day, float(rating_text)
            except ValueError:
                continue
        offset += PAGE_SIZE
        time.sleep(SLEEP_SECONDS)


def main() -> None:
    session = requests.Session()
    rows = []
    for player_id, slug, name, team, color in PLAYERS:
        print(f"scraping {name} (id {player_id})...", flush=True)
        matches = list(fetch_matches(session, player_id, slug))
        print(f"  {len(matches)} maps")
        for d, rating in rolling_knots(matches):
            rows.append((name, team, color, d, rating))
        time.sleep(SLEEP_SECONDS)
    write_knots_csv(OUT, rows)
    print(f"wrote {len(rows)} knots -> {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
