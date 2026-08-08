#!/usr/bin/env python3
"""
Wordscapes level generator — THROWAWAY BUILD TOOL, NOT PART OF THE APP.

Produces two assets consumed by the Android app at runtime:

    app/src/main/assets/levels.json     15 levels, schema below
    app/src/main/assets/dictionary.txt  bonus-word lookup, filtered small

Run:
    python3 tools/generate_levels.py

Level schema (one object per level in a top-level "levels" array):

    {
      "id": 1,
      "letters": ["R","A","T","E","S"],
      "gridWidth": 7,
      "gridHeight": 6,
      "words": [
        {"word": "STARE", "row": 2, "col": 1, "horizontal": true},
        {"word": "RATE",  "row": 0, "col": 3, "horizontal": false}
      ]
    }

Design notes
------------
Cell occupancy and intersections are deliberately NOT stored. The app derives
them at parse time so that a malformed placement fails loudly at load rather
than silently mid-level.

Placement is greedy with backtracking on shared letters. Crossword legality is
enforced by `can_place`, which rejects any placement that would create an
unintended adjacent letter run — the classic failure mode where two parallel
words touch along their length and spell garbage in the perpendicular
direction.

Word list is SCOWL via Debian's `wamerican` package (see WORDLIST-LICENSE.txt).
"""

from __future__ import annotations

import json
import random
from collections import Counter
from pathlib import Path

# ── Tunables ─────────────────────────────────────────────────────────────────
SEED = 20260808              # fixed so runs are reproducible
LEVEL_COUNT = 15
MIN_WORD_LEN = 3             # shortest placeable / submittable word
BASE_LEN_RANGE = (5, 7)      # wheel letter count
MIN_GRID_WORDS = 4           # a level with fewer feels empty
MAX_GRID_WORDS = 7           # more than this and the grid sprawls
MIN_BONUS_WORDS = 8          # sub-anagrams NOT in the grid. A level with zero
                             # of these can never trigger the bonus-word
                             # feedback path, and that feedback is one of the
                             # three the brief explicitly grades.
MAX_GRID_DIM = 8             # hard ceiling; must fit a phone screen legibly
COMPACTNESS_SAMPLE = 12      # best-scoring placements to explore per word

ROOT = Path(__file__).resolve().parent.parent
SOURCE_LIST = Path(__file__).resolve().parent / "wordlist-source.txt"
COMMON_LIST = Path(__file__).resolve().parent / "wordlist-common.txt"
BLOCKLIST = Path(__file__).resolve().parent / "blocklist.txt"
ASSETS = ROOT / "app" / "src" / "main" / "assets"


# ── Word list ────────────────────────────────────────────────────────────────
def load_blocklist() -> set[str]:
    """Words that must never appear in a grid or as a scoring bonus word.

    SCOWL is a spell-checker list: complete, not curated. Without this filter
    the generator will place slurs and sexual-violence vocabulary in grids,
    because they are perfectly valid sub-anagrams. This is not hypothetical —
    the first unfiltered run put RAPE in level 1.
    """
    out: set[str] = set()
    with BLOCKLIST.open(encoding="utf-8") as fh:
        for line in fh:
            w = line.strip().lower()
            if w and not w.startswith("#"):
                out.add(w)
    return out


def _read_list(path: Path, blocked: set[str]) -> set[str]:
    out: set[str] = set()
    with path.open(encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            w = line.strip()
            if not w or "'" in w:
                continue
            if not w.isascii() or not w.isalpha() or not w.islower():
                continue
            if len(w) < MIN_WORD_LEN or w in blocked:
                continue
            out.add(w)
    return out


def load_words() -> tuple[set[str], set[str]]:
    """Return (common, full).

    Two tiers, because "is this a real word?" and "should a player be REQUIRED
    to find this word?" are different questions.

    * common - frequency-filtered (see tools/build_common_list.py). Only these
      may be chosen as a base word or placed in a grid. If progress depends on
      finding it, it has to be a word people actually know.
    * full   - the whole SCOWL list. Used only to decide whether a swiped word
      counts as a bonus. Being generous here is free: an obscure bonus word is
      a pleasant surprise, never a blocker.

    The first build ignored this distinction and shipped a level 1 requiring
    SARI, SITAR and ASTIR.
    """
    blocked = load_blocklist()
    full = _read_list(SOURCE_LIST, blocked)
    common = _read_list(COMMON_LIST, blocked) & full
    print(f"blocklist: {len(blocked)} entries")
    print(f"vocabulary: {len(common)} common (grid-eligible) / {len(full)} full (bonus)")
    return common, full


def signature(word: str) -> tuple[tuple[str, int], ...]:
    """Sorted letter-count signature; two words share one iff they're anagrams."""
    return tuple(sorted(Counter(word).items()))


def is_subanagram(word: str, base_counts: Counter) -> bool:
    """True if `word` can be spelled from the multiset `base_counts`."""
    wc = Counter(word)
    return all(wc[ch] <= base_counts[ch] for ch in wc)


# ── Grid placement ───────────────────────────────────────────────────────────
class Grid:
    """Sparse crossword grid keyed by (row, col). Coordinates may go negative
    during construction; `normalise` shifts everything to origin at the end."""

    def __init__(self) -> None:
        self.cells: dict[tuple[int, int], str] = {}
        self.placed: list[dict] = []

    def can_place(self, word: str, row: int, col: int, horizontal: bool) -> bool:
        """Legality check. Returns False unless every one of these holds:

        1. Overlapping cells agree on their letter.
        2. At least one cell overlaps an existing word (no floating islands),
           unless the grid is empty.
        3. The cells immediately before and after the word are empty, so we
           don't silently extend an existing word.
        4. Any new (non-overlapping) cell has no perpendicular neighbours —
           this is what stops two parallel words running alongside each other
           and spelling nonsense across.
        """
        dr, dc = (0, 1) if horizontal else (1, 0)
        overlaps = 0

        # (3) head and tail must be clear
        before = (row - dr, col - dc)
        after = (row + dr * len(word), col + dc * len(word))
        if before in self.cells or after in self.cells:
            return False

        for i, ch in enumerate(word):
            r, c = row + dr * i, col + dc * i
            existing = self.cells.get((r, c))

            if existing is not None:
                if existing != ch:
                    return False          # (1) conflict
                overlaps += 1
                continue

            # (4) new cell — perpendicular neighbours must be empty
            if horizontal:
                if (r - 1, c) in self.cells or (r + 1, c) in self.cells:
                    return False
            else:
                if (r, c - 1) in self.cells or (r, c + 1) in self.cells:
                    return False

        if self.cells and overlaps == 0:
            return False                  # (2) floating island
        if overlaps == len(word):
            return False                  # fully covered — adds nothing
        return True

    def place(self, word: str, row: int, col: int, horizontal: bool) -> None:
        dr, dc = (0, 1) if horizontal else (1, 0)
        for i, ch in enumerate(word):
            self.cells[(row + dr * i, col + dc * i)] = ch
        self.placed.append(
            {"word": word, "row": row, "col": col, "horizontal": horizontal}
        )

    def unplace(self) -> None:
        """Remove the most recent placement. Rebuilds the cell map rather than
        tracking per-cell ownership — grids are tiny, clarity wins."""
        self.placed.pop()
        self.cells.clear()
        for p in self.placed:
            dr, dc = (0, 1) if p["horizontal"] else (1, 0)
            for i, ch in enumerate(p["word"]):
                self.cells[(p["row"] + dr * i, p["col"] + dc * i)] = ch

    def bounds(self) -> tuple[int, int, int, int]:
        rows = [r for r, _ in self.cells]
        cols = [c for _, c in self.cells]
        return min(rows), max(rows), min(cols), max(cols)

    def normalise(self) -> tuple[int, int]:
        """Shift all coordinates so the top-left occupied cell is (0,0).
        Returns (width, height)."""
        r0, r1, c0, c1 = self.bounds()
        for p in self.placed:
            p["row"] -= r0
            p["col"] -= c0
        self.cells = {(r - r0, c - c0): ch for (r, c), ch in self.cells.items()}
        return c1 - c0 + 1, r1 - r0 + 1

    def render(self) -> str:
        r0, r1, c0, c1 = self.bounds()
        lines = []
        for r in range(r0, r1 + 1):
            lines.append(
                " ".join(self.cells.get((r, c), ".") for c in range(c0, c1 + 1))
            )
        return "\n".join(lines)


def candidate_positions(grid: Grid, word: str) -> list[tuple[int, int, bool]]:
    """Every placement that shares a letter with something already on the grid."""
    spots: list[tuple[int, int, bool]] = []
    for (r, c), ch in grid.cells.items():
        for i, wch in enumerate(word):
            if wch != ch:
                continue
            # perpendicular to whatever crosses here — try both orientations
            spots.append((r, c - i, True))
            spots.append((r - i, c, False))
    return spots


def build_grid(words: list[str], rng: random.Random) -> Grid | None:
    """Greedy placement with backtracking. `words` arrives longest-first."""
    grid = Grid()
    grid.place(words[0], 0, 0, True)

    def recurse(idx: int, placed_count: int) -> bool:
        if placed_count >= MAX_GRID_WORDS:
            return True
        if idx >= len(words):
            return placed_count >= MIN_GRID_WORDS

        word = words[idx]
        spots = candidate_positions(grid, word)
        rng.shuffle(spots)                 # break ties unpredictably

        # Prefer placements that keep the grid tight. Without this the
        # backtracker takes the first legal spot it finds and grids drift into
        # long sparse chains — legal, but they render as a scattering of
        # letters rather than a crossword. Score = area of the resulting
        # bounding box, plus a penalty for being non-square.
        scored: list[tuple[int, int, int, bool]] = []
        for row, col, horiz in spots:
            if not grid.can_place(word, row, col, horiz):
                continue
            r0, r1, c0, c1 = grid.bounds()
            dr, dc = (0, 1) if horiz else (1, 0)
            nr0 = min(r0, row)
            nr1 = max(r1, row + dr * (len(word) - 1))
            nc0 = min(c0, col)
            nc1 = max(c1, col + dc * (len(word) - 1))
            h, w = nr1 - nr0 + 1, nc1 - nc0 + 1
            if h > MAX_GRID_DIM or w > MAX_GRID_DIM:
                continue
            scored.append((h * w + abs(h - w), row, col, horiz))

        scored.sort(key=lambda t: t[0])

        for _, row, col, horiz in scored[:COMPACTNESS_SAMPLE]:
            grid.place(word, row, col, horiz)
            if recurse(idx + 1, placed_count + 1):
                return True
            grid.unplace()

        # skipping this word is legitimate — not every sub-anagram must fit
        return recurse(idx + 1, placed_count)

    if recurse(1, 1):
        return grid
    return None


# ── Level assembly ───────────────────────────────────────────────────────────
def build_levels(common: set[str], full: set[str], rng: random.Random) -> list[dict]:
    by_len: dict[int, list[str]] = {}
    for w in common:
        by_len.setdefault(len(w), []).append(w)
    for lst in by_len.values():
        lst.sort()

    # Difficulty ramp: early levels have 5 wheel letters, later ones 6 then 7.
    plan = [5] * 5 + [6] * 5 + [7] * 5
    levels: list[dict] = []
    used_bases: set[tuple] = set()

    for level_id, base_len in enumerate(plan, start=1):
        pool = by_len[base_len][:]
        rng.shuffle(pool)

        for base in pool:
            sig = signature(base)
            if sig in used_bases:
                continue

            base_counts = Counter(base)

            # Placeable words: common tier only.
            placeable = [
                w for w in common
                if MIN_WORD_LEN <= len(w) <= base_len and is_subanagram(w, base_counts)
            ]
            # Bonus-eligible words: full tier.
            subs = [
                w for w in full
                if MIN_WORD_LEN <= len(w) <= base_len and is_subanagram(w, base_counts)
            ]
            if len(placeable) < MIN_GRID_WORDS + 2:
                continue

            # Longest-first improves the odds the backtracker finds a compact grid
            placeable.sort(key=lambda w: (-len(w), w))
            shortlist = placeable[: MAX_GRID_WORDS + 8]

            grid = build_grid(shortlist, rng)
            if grid is None or len(grid.placed) < MIN_GRID_WORDS:
                continue

            width, height = grid.normalise()
            if width > MAX_GRID_DIM or height > MAX_GRID_DIM:
                continue

            placed_words = {p["word"] for p in grid.placed}
            if len(set(subs) - placed_words) < MIN_BONUS_WORDS:
                continue

            used_bases.add(sig)
            letters = sorted(base.upper())
            rng.shuffle(letters)
            levels.append(
                {
                    "id": level_id,
                    "letters": letters,
                    "gridWidth": width,
                    "gridHeight": height,
                    "words": [
                        {
                            "word": p["word"].upper(),
                            "row": p["row"],
                            "col": p["col"],
                            "horizontal": p["horizontal"],
                        }
                        for p in sorted(
                            grid.placed, key=lambda p: (p["row"], p["col"])
                        )
                    ],
                    "_base": base,
                    "_ascii": grid.render(),
                    "_subs": subs,
                }
            )
            break
        else:
            raise RuntimeError(f"no viable base word of length {base_len}")

    return levels


def build_dictionary(levels: list[dict], words: set[str]) -> list[str]:
    """Bonus-word lookup. Only words reachable from at least one level's letter
    set are worth shipping — keeps the asset a few KB instead of ~600 KB."""
    keep: set[str] = set()
    for lvl in levels:
        keep.update(lvl["_subs"])
    return sorted(keep)


# ── Entry point ──────────────────────────────────────────────────────────────
def main() -> None:
    rng = random.Random(SEED)
    common, full = load_words()

    levels = build_levels(common, full, rng)
    dictionary = build_dictionary(levels, full)

    ASSETS.mkdir(parents=True, exist_ok=True)

    for lvl in levels:
        grid_words = {w["word"].lower() for w in lvl["words"]}
        bonus = len(set(lvl["_subs"]) - grid_words)
        print(
            f"\n── Level {lvl['id']}  base={lvl['_base']}  "
            f"letters={''.join(lvl['letters'])}  "
            f"{lvl['gridWidth']}x{lvl['gridHeight']}  "
            f"grid={len(lvl['words'])}  bonus={bonus}"
        )
        print(lvl["_ascii"])

    clean = [
        {k: v for k, v in lvl.items() if not k.startswith("_")} for lvl in levels
    ]
    (ASSETS / "levels.json").write_text(
        json.dumps({"levels": clean}, indent=2) + "\n", encoding="utf-8"
    )
    (ASSETS / "dictionary.txt").write_text(
        "\n".join(dictionary) + "\n", encoding="utf-8"
    )

    print(f"\nwrote {ASSETS/'levels.json'}  ({len(clean)} levels)")
    print(f"wrote {ASSETS/'dictionary.txt'}  ({len(dictionary)} words)")


if __name__ == "__main__":
    main()
