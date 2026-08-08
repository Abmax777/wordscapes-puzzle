#!/usr/bin/env python3
"""
Independent validator for the generated assets.

Deliberately does NOT import generate_levels.py — it re-derives everything from
the emitted JSON. A validator that shares code with the generator only proves
the generator is self-consistent, not that it is correct.

Mirrors the assertions in LevelRepositoryTest.kt, so a failure here is a
failure there.

    python3 tools/validate_levels.py
"""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEVELS = ROOT / "app" / "src" / "main" / "assets" / "levels.json"
DICT = ROOT / "app" / "src" / "main" / "assets" / "dictionary.txt"
SOURCE = Path(__file__).resolve().parent / "wordlist-source.txt"
BLOCKLIST = Path(__file__).resolve().parent / "blocklist.txt"

failures: list[str] = []


def fail(level_id: object, msg: str) -> None:
    failures.append(f"level {level_id}: {msg}")


def real_words() -> set[str]:
    out = set()
    for line in SOURCE.open(encoding="utf-8", errors="ignore"):
        w = line.strip()
        if w and w.isascii() and w.isalpha() and w.islower():
            out.add(w)
    return out


def main() -> int:
    data = json.loads(LEVELS.read_text(encoding="utf-8"))
    levels = data["levels"]
    dictionary = {w.strip() for w in DICT.read_text(encoding="utf-8").split() if w.strip()}
    lexicon = real_words()
    blocked = {
        ln.strip().lower()
        for ln in BLOCKLIST.read_text(encoding="utf-8").splitlines()
        if ln.strip() and not ln.strip().startswith("#")
    }

    # 0. nothing offensive survived into either shipped asset
    for w in sorted(dictionary & blocked):
        fail("dictionary.txt", f"blocked word {w!r} shipped as a bonus word")

    if len(levels) < 10:
        fail("*", f"only {len(levels)} levels; the brief requires 10-20")

    seen_ids = set()

    for lvl in levels:
        lid = lvl["id"]
        if lid in seen_ids:
            fail(lid, "duplicate id")
        seen_ids.add(lid)

        letters = [c.upper() for c in lvl["letters"]]
        if not (5 <= len(letters) <= 7):
            fail(lid, f"wheel has {len(letters)} letters, expected 5-7")
        pool = Counter(letters)

        w, h = lvl["gridWidth"], lvl["gridHeight"]
        cells: dict[tuple[int, int], str] = {}

        for pw in lvl["words"]:
            word = pw["word"].upper()
            r, c, horiz = pw["row"], pw["col"], pw["horizontal"]

            # 1. real English word, and not blocked
            if word.lower() not in lexicon:
                fail(lid, f"{word!r} is not in the source lexicon")
            if word.lower() in blocked:
                fail(lid, f"{word!r} is on the blocklist and must not be placed")

            # 2. spellable from the wheel
            wc = Counter(word)
            if any(wc[ch] > pool[ch] for ch in wc):
                fail(lid, f"{word!r} is not a sub-anagram of {''.join(letters)}")

            # 3. within declared bounds
            end_r = r + (0 if horiz else len(word) - 1)
            end_c = c + (len(word) - 1 if horiz else 0)
            if r < 0 or c < 0 or end_r >= h or end_c >= w:
                fail(lid, f"{word!r} at ({r},{c}) escapes {w}x{h} grid")
                continue

            # 4. intersections agree
            dr, dc = (0, 1) if horiz else (1, 0)
            for i, ch in enumerate(word):
                pos = (r + dr * i, c + dc * i)
                prev = cells.get(pos)
                if prev is not None and prev != ch:
                    fail(lid, f"{word!r} conflicts at {pos}: {prev} vs {ch}")
                cells[pos] = ch

        # 5. no unintended adjacent runs — every maximal horizontal and
        #    vertical run of 2+ letters must be a declared word
        declared = {pw["word"].upper() for pw in lvl["words"]}
        for axis, (dr, dc) in (("row", (0, 1)), ("col", (1, 0))):
            for pos in sorted(cells):
                back = (pos[0] - dr, pos[1] - dc)
                if back in cells:
                    continue                      # not the start of a run
                run, p = "", pos
                while p in cells:
                    run += cells[p]
                    p = (p[0] + dr, p[1] + dc)
                if len(run) > 1 and run not in declared:
                    fail(lid, f"unintended {axis} run {run!r} at {pos}")

        # 6. grid is fully connected (single crossword, not islands)
        if cells:
            start = next(iter(cells))
            stack, seen = [start], {start}
            while stack:
                r, c = stack.pop()
                for nb in ((r + 1, c), (r - 1, c), (r, c + 1), (r, c - 1)):
                    if nb in cells and nb not in seen:
                        seen.add(nb)
                        stack.append(nb)
            if len(seen) != len(cells):
                fail(lid, f"grid is disconnected ({len(cells)-len(seen)} orphan cells)")

        # 7. declared dimensions are tight, not padded
        if cells:
            max_r = max(r for r, _ in cells)
            max_c = max(c for _, c in cells)
            if max_r != h - 1 or max_c != w - 1:
                fail(lid, f"declared {w}x{h} but content occupies {max_c+1}x{max_r+1}")

        # 8. every grid word resolves in the shipped dictionary
        for word in declared:
            if word.lower() not in dictionary:
                fail(lid, f"{word!r} missing from dictionary.txt")

    print(f"levels:     {len(levels)}")
    print(f"dictionary: {len(dictionary)} words")
    print(f"lexicon:    {len(lexicon)} words")

    if failures:
        print(f"\nFAILED — {len(failures)} problem(s):")
        for f in failures:
            print(f"  {f}")
        return 1

    print("\nAll checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
