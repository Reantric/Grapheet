#!/usr/bin/env python3
"""Add one "tower beaten" blip per ledger event to a rendered JtohDifficultyScene.

Timing is read straight from ``output/JtohDifficultyScene-beats.csv``, which the
scene emits during an offline export (one row per completion, stamped with the
exact video-time the row slides into the ledger). Because the render is the
single source of truth, the audio can never drift when the tempo schedule is
retuned -- no Python re-simulation of the tempo is involved.

Each blip is a short pentatonic "pluck": pitch rises with the tower's difficulty
(an Easy clear pings low, an Insane PR rings high), loudness scales gently with
difficulty, and each player sits at a fixed stereo position so you can hear whose
tower it was. Notes are locked to a major-pentatonic ladder so the dense Amog
grind reads as an arpeggio run rather than mud.

numpy lives in the system framework python3, not Homebrew's. Run with that
interpreter and point --ffmpeg at a real ffmpeg, e.g.:

    /usr/bin/env python3 tools/add_tower_sfx.py --ffmpeg /opt/homebrew/bin/ffmpeg
"""
from __future__ import annotations

import argparse
import csv
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 48_000  # audio sample rate

# Major-pentatonic semitone offsets within an octave.
_PENT = (0, 2, 4, 7, 9)
# Difficulty range the ladder is stretched across (the dataset's tier ratings).
_DIFF_LO, _DIFF_HI = 1.0, 10.0

# Stereo pan per player, equal-power (-1 = hard left .. +1 = hard right). Players
# not listed fall back to centre. Keeps each climber audibly separable.
_PAN = {
    "Amog": 0.0,
    "Eggnote": 0.55,
    "fairylog": -0.55,
    "snow": 0.8,
    "redstone": -0.8,
}


def _pentatonic_ladder(base_midi: int = 57, octaves: int = 4) -> np.ndarray:
    """A sorted run of pentatonic MIDI notes from base across `octaves` octaves."""
    notes = [base_midi + 12 * o + s for o in range(octaves) for s in _PENT]
    return np.array(sorted(notes), dtype=float)


_LADDER = _pentatonic_ladder()


def _note_for(difficulty: float) -> float:
    """Map a tower difficulty onto the pentatonic ladder -> frequency (Hz)."""
    frac = (difficulty - _DIFF_LO) / (_DIFF_HI - _DIFF_LO)
    frac = min(1.0, max(0.0, frac))
    idx = int(round(frac * (len(_LADDER) - 1)))
    midi = _LADDER[idx]
    return 440.0 * 2.0 ** ((midi - 69) / 12.0)


def _blip(freq: float, difficulty: float) -> np.ndarray:
    """A single mono pluck: fast attack, exponential decay, a few harmonics."""
    dur = 0.30
    t = np.arange(int(dur * SR)) / SR
    # Fast attack (~3ms) * exponential decay (~90ms) -> a marimba/glock pluck.
    env = np.exp(-t / 0.09) * (1.0 - np.exp(-t / 0.003))
    tone = (
        np.sin(2 * np.pi * freq * t)
        + 0.50 * np.sin(2 * np.pi * 2 * freq * t)
        + 0.22 * np.sin(2 * np.pi * 3 * freq * t)
    )
    frac = (difficulty - _DIFF_LO) / (_DIFF_HI - _DIFF_LO)
    frac = min(1.0, max(0.0, frac))
    amp = 0.32 * (0.62 + 0.38 * frac)  # harder towers a touch louder
    return (tone * env * amp).astype(np.float32)


def _ffprobe_duration(ffmpeg: str, video: str) -> float:
    ffprobe = ffmpeg[:-6] + "ffprobe" if ffmpeg.endswith("ffmpeg") else "ffprobe"
    try:
        out = subprocess.run(
            [ffprobe, "-v", "error", "-show_entries", "format=duration",
             "-of", "default=nw=1:nk=1", video],
            capture_output=True, text=True, check=True,
        )
        return float(out.stdout.strip())
    except (subprocess.CalledProcessError, ValueError, FileNotFoundError):
        return 0.0


def build_track(beats: list[dict], total_seconds: float) -> np.ndarray:
    """Mix every beat into a stereo float buffer, soft-limited and normalized."""
    tail = 0.4
    n = int((total_seconds + tail) * SR)
    buf = np.zeros((n, 2), dtype=np.float32)

    for b in beats:
        secs = float(b["seconds"])
        diff = float(b["difficulty"])
        mono = _blip(_note_for(diff), diff)
        pan = _PAN.get(b["player"], 0.0)
        # Equal-power pan.
        gl = np.sqrt((1.0 - pan) / 2.0)
        gr = np.sqrt((1.0 + pan) / 2.0)
        start = int(round(secs * SR))
        end = min(start + len(mono), n)
        if start >= n:
            continue
        seg = mono[: end - start]
        buf[start:end, 0] += seg * gl
        buf[start:end, 1] += seg * gr

    # Soft-clip the dense grind clusters (many near-simultaneous beats), then
    # leave generous headroom: lossy AAC overshoots transient peaks by a few dB,
    # so a 0.89 target re-decodes above full scale and clips. 0.63 lands the
    # re-decoded peak near -1 dBFS.
    buf = np.tanh(buf * 1.5)
    peak = float(np.max(np.abs(buf))) or 1.0
    buf *= 0.63 / peak
    return buf


def write_wav(path: str, buf: np.ndarray) -> None:
    pcm = (np.clip(buf, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def mux(ffmpeg: str, video: str, wav: str, out: str, audio_bitrate: str) -> None:
    subprocess.run(
        [ffmpeg, "-y", "-hide_banner", "-loglevel", "error",
         "-i", video, "-i", wav,
         "-map", "0:v:0", "-map", "1:a:0",
         "-c:v", "copy", "-c:a", "aac", "-b:a", audio_bitrate, "-shortest", out],
        check=True,
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--beats", default="output/JtohDifficultyScene-beats.csv")
    ap.add_argument("--video", default="output/JtohDifficultyScene.mp4")
    ap.add_argument("--out", default="output/JtohDifficultyScene-sfx.mp4")
    ap.add_argument("--ffmpeg", default="ffmpeg")
    ap.add_argument("--audio-bitrate", default="192k",
                    help="AAC bitrate, e.g. 256k/320k for a high-quality master")
    args = ap.parse_args()

    if not os.path.exists(args.beats):
        sys.exit(f"beats file not found: {args.beats} (render the scene first)")
    if not os.path.exists(args.video):
        sys.exit(f"video not found: {args.video}")

    with open(args.beats, newline="") as f:
        beats = list(csv.DictReader(f))
    if not beats:
        sys.exit("beats file has no rows")

    total = _ffprobe_duration(args.ffmpeg, args.video)
    if total <= 0:
        total = max(float(b["seconds"]) for b in beats) + 1.0

    print(f"{len(beats)} beats over {total:.1f}s -> synthesizing...")
    buf = build_track(beats, total)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        write_wav(wav_path, buf)
        mux(args.ffmpeg, args.video, wav_path, args.out, args.audio_bitrate)
    finally:
        os.unlink(wav_path)
    print(f"wrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
