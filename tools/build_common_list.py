#!/usr/bin/env python3
"""
Produce `wordlist-common.txt` — the vocabulary allowed to appear IN A GRID.

Why this exists
---------------
SCOWL (via Debian's `wamerican`) is a spell-checker list: it is complete, not
curated. Every word in it is real, and plenty of them are useless in a casual
word game. The first generated build shipped a level 1 requiring SARI, SITAR
and ASTIR, and a level 2 requiring SEPTA. Those are legitimate English words
and a terrible first impression — a player stuck on level 1 concludes the game
is broken, not that their vocabulary is lacking.

The fix is a two-tier vocabulary:

  * GRID words come from this filtered list. If the player is *required* to
    find it to progress, it has to be a word they actually know.
  * BONUS words are checked against the full SCOWL list. Discovering an
    obscure word you happened to try is a reward, never a blocker, so being
    generous there costs nothing.

Frequency, not list membership
------------------------------
Debian ships a smaller SCOWL tier (`wamerican-small`) which drops ASTIR and
SITAR, but still keeps PATES and BOLE. Filtering on actual corpus frequency is
sharper. `wordfreq` reports Zipf values — a log scale where 5.0 is roughly
"the", 4.0 is common, 3.0 is recognisable, below 2.5 is specialist.

Measured on the offenders:

    ASTIR 1.60   PATES 1.65   BOLE 2.40   SITAR 2.41
    RIME  2.50   SEPTA 2.70   MELD 2.72   SPATE 2.79
    ---- threshold 3.2 ----
    STAIR 3.26   DELI 3.35    ELBOW 3.84  PASTE 3.91   RATS 4.05   ARTS 4.70

3.2 puts the cut cleanly between the two groups and still leaves ~7,800 usable
base words, far more than the fifteen levels need.

This script is separate from the generator so that `generate_levels.py` has no
third-party dependency and stays runnable with a bare Python install. Re-run
this only if the threshold changes.

    pip install wordfreq
    python3 tools/build_common_list.py
"""

from __future__ import annotations

from pathlib import Path

from wordfreq import zipf_frequency

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "wordlist-source.txt"
BLOCKLIST = HERE / "blocklist.txt"
OUT = HERE / "wordlist-common.txt"

# Zipf floor for words a player can be REQUIRED to find. See module docstring.
MIN_ZIPF = 3.2
MIN_LEN = 3


def load_blocklist() -> set[str]:
    return {
        line.strip().lower()
        for line in BLOCKLIST.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.strip().startswith("#")
    }


def main() -> None:
    blocked = load_blocklist()
    kept: list[str] = []
    considered = 0

    with SOURCE.open(encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            w = line.strip()
            if not w or "'" in w:
                continue
            if not w.isascii() or not w.isalpha() or not w.islower():
                continue
            if len(w) < MIN_LEN or w in blocked:
                continue
            considered += 1
            if zipf_frequency(w, "en") >= MIN_ZIPF:
                kept.append(w)

    kept.sort()
    OUT.write_text("\n".join(kept) + "\n", encoding="utf-8")

    print(f"considered {considered} words from {SOURCE.name}")
    print(f"kept {len(kept)} at zipf >= {MIN_ZIPF} -> {OUT.name}")
    bases = sum(1 for w in kept if 5 <= len(w) <= 7)
    print(f"  usable base words (5-7 letters): {bases}")


if __name__ == "__main__":
    main()
