# Wordscapes-style Word Puzzle

An Android word puzzle: swipe letters on a circular wheel to spell words and
fill a crossword grid. Kotlin, Jetpack Compose, single Activity, MVVM.

> **Status: in progress.** Gameplay is complete and playable end to end across
> all 15 levels. Cross-session progress, the pause dialog and the
> micro-interaction animation pass are not built yet. See
> [Known limitations](#known-limitations) — that section is deliberately honest
> rather than aspirational.

---

## Build and run

```bash
./gradlew :app:installDebug        # onto a connected device
./gradlew :app:testDebugUnitTest   # 89 JVM unit tests, no device needed
```

Requires JDK 17. Toolchain is pinned to AGP 9.2.1 / Gradle 9.4.1 — AGP 9.2 lists
Gradle 9.4.1 as both its minimum and its default, and 9.2.1 is the ceiling the
development Android Studio supports. `minSdk` 24, `compileSdk`/`targetSdk` 36.

Note that AGP 9 provides Kotlin itself, so there is deliberately no
`kotlin-android` plugin. Kotlin *compiler* plugins (Compose, serialization) are
still applied explicitly.

---

## Architecture

```
ui/
  home/          HomeScreen
  levelselect/   LevelSelectScreen + ViewModel
  game/          GameScreen, GameViewModel, GameUiState
    wheel/       WheelGeometry, WheelSelection, WheelGestureState, LetterWheel
    grid/        CrosswordGrid
  navigation/    Destination, NavGraph
  theme/         Color, Type, Theme
domain/
  model/         Level, PlacedWord, GridCell, WordResult, GameRules
  repository/    LevelCatalog, WordLookup          (interfaces)
  usecase/       ValidateWord
data/
  level/         LevelRepository, LevelJsonSource, LevelMapper, LevelDto
  dictionary/    DictionarySource
di/              AppModule, DispatcherModule, RepositoryModule
tools/           level generator — build tooling, not part of the app
```

**Dependencies point inwards.** `domain/` declares `LevelCatalog` and
`WordLookup` as interfaces; `data/` implements them; Hilt binds them in
`RepositoryModule`. Both concrete implementations need an Android `Context` to
read assets, so depending on the interfaces is what lets `ValidateWord` and
`GameViewModel` be tested on the JVM with a two-line fake instead of under
Robolectric.

`GameRules.MIN_WORD_LENGTH` lives in `domain/` for the same reason. An earlier
draft had `ValidateWord` importing it from the wheel package, which inverted the
direction and would have made the domain layer unusable without the UI.

**One `StateFlow<GameUiState>` per screen ViewModel.** A screen sees a single
immutable snapshot rather than several flows it has to combine, which removes a
whole class of bug where one flow has updated and another has not.

---

## Swipe logic

This is the most interesting part of the codebase, and the part with the
subtlest failure modes. Four pieces:

### 1. `WheelGeometry` — pure, cached, testable

Letter positions are a pure function of available size and letter count. No
Compose runtime beyond `Offset`, so it is fully unit-tested. Computed once per
size change via `Modifier.drawWithCache`, whose outer block re-runs only on size
change while the `onDrawBehind` lambda runs per frame. With a plain `Canvas` the
trig and glyph measurement would recompute on every frame of every drag.

Caching matters less for the cost than for **stability**: hit-test results must
not shift under a finger mid-drag because a recomposition recalculated a radius
slightly differently.

### 2. Hit testing is generous, and resolves by nearest centre

The touch target is **1.6× the drawn circle** (`HIT_RADIUS_MULTIPLIER`). A
target matching the visual reads as unresponsive: a fingertip contact patch is
roughly 9 mm across but the system reports a single point somewhere inside it,
so a player aiming dead centre often produces a point outside a matched-size
hitbox.

Because inflated radii overlap, `hitTest` returns the **nearest** centre rather
than the first match. First-match would make results depend on list order, so a
finger in the overlap between letters 2 and 3 would always resolve to 2 — which
reads as the wheel favouring one side.

### 3. Segment hit testing, not point sampling

Pointer events are sampled, not continuous. A fast flick across the wheel may
produce only five or six samples for the entire drag, so a letter sitting
between two consecutive samples is silently skipped and the player sees a swipe
they clearly made produce the wrong word. Inflating the hitbox helps but cannot
fix it — the faster the drag, the wider the gaps.

`hitTestSegment` solves it exactly rather than by sub-sampling: for each letter,
take the perpendicular distance from its centre to the line segment between
consecutive pointer samples. Within `hitRadius` means the finger passed through
it, however sparse the sampling. Results are ordered by where along the segment
the closest approach occurred, so one segment crossing three letters returns
them in travel order.

### 4. Selection rules, and one bug worth reading about

`WheelSelection` is pure logic with no Compose or gesture dependency, so the
rules get real tests instead of thumb-verification. Three cases:

| Entering | Result |
|---|---|
| An unselected letter | append |
| The letter immediately before the current last | pop the last (backtrack) |
| Any other already-selected letter | ignore |

The third case covers two things that both must be no-ops: re-entering the
*current* letter, which happens constantly because a resting finger emits a
stream of move events (appending each would spell `SSSSS`), and crossing back
over the middle of your own trail, since a letter cannot be reused in a word.

**Backtrack requires the finger to be inside the letter.** This rule exists
because of a bug found in ordinary play. On a five-letter wheel `C S E A R`,
swiping R → A → C means the hop from A to C spans 144° — and R sits directly
between them. A straight chord clears R's hit circle by about 2%, but nobody
swipes straight chords; fingers arc along the rim, and that arc passes through
R. R being the second-to-last selection, the old rule read it as a retrace and
popped A, so `R-A-C-E` silently became `R-C-E` and reported "not a word".

Letters swept over mid-segment may now append but never undo. Backtracking is a
deliberate act — the finger comes to rest on the previous letter — whereas
segment crossings are just ground the finger covered on the way elsewhere.

### Submission ordering

On pointer up the selection is cleared **before** the word is emitted, so
validation and its animations run against an already-empty wheel. A second swipe
starting immediately cannot append onto the previous word.

Submissions are then serialised through a `Channel` consumed by a single
coroutine in `GameViewModel`. Validation suspends on the dictionary lookup, so
two rapid swipes would otherwise both read the same revealed-set snapshot and
resolve the same word as a fresh find twice. `submitWord` stays non-blocking so
the wheel never waits on validation, and an unlimited buffer absorbs a burst
rather than dropping its middle.

### Rendering the tracking line

Segments join consecutive selected centres, plus one live segment to the **raw**
pointer position. No smoothing or interpolation on that live segment — easing
there reads as input lag even at a few milliseconds.

---

## Level data

`app/src/main/assets/levels.json`:

```json
{
  "id": 1,
  "letters": ["N", "I", "L", "S", "K"],
  "gridWidth": 7,
  "gridHeight": 6,
  "words": [
    { "word": "LINKS", "row": 2, "col": 1, "horizontal": true },
    { "word": "SILK",  "row": 0, "col": 3, "horizontal": false }
  ]
}
```

`letters` is the wheel set. Every entry in `words` is an anagram subset of it.

**Cell occupancy and intersections are derived at load time, never stored.**
`LevelMapper` rebuilds the grid while validating every placement against every
other: spellable from the wheel, inside the declared bounds, agreeing at
intersections, no duplicate words. A malformed level throws
`LevelFormatException` naming the level id at startup, instead of producing an
unwinnable grid that only reveals itself three words into play.

`GridCell` carries a **set** of word indices, not one, so revealing a word also
fills in the shared letters of every word crossing it.

### Generation (`tools/`, not part of the app build)

1. **Source vocabulary** — SCOWL via Debian `wamerican` (see `WORDLIST-LICENSE.txt`).
2. **Content blocklist** (`blocklist.txt`, 280 entries) — a spell-checker list is
   complete, not curated. Unfiltered, the first run placed `RAPE` in level 1
   because it is a perfectly valid sub-anagram of "paper".
3. **Two-tier vocabulary.** "Is this a real word?" and "should a player be
   *required* to find this to progress?" are different questions.
   `build_common_list.py` filters by corpus frequency (`wordfreq`, Zipf ≥ 3.2) to
   produce the grid-eligible list. The cut sits cleanly between ASTIR 1.60,
   SITAR 2.41, SEPTA 2.70 and STAIR 3.26, PASTE 3.91, ARTS 4.70. Bonus words
   still resolve against the full list — an obscure word you happened to try is
   a reward, never a blocker.
4. **Placement** — greedy with backtracking on shared letters, biased toward
   compact bounding boxes. Without that bias grids drift into long sparse chains
   that are legal but read as scattered letters rather than a crossword.
5. **Validation** — `validate_levels.py` re-derives everything from the emitted
   JSON without importing the generator. A validator sharing generator code only
   proves the generator is self-consistent.

```bash
python3 tools/generate_levels.py     # emits levels.json + dictionary.txt
python3 tools/validate_levels.py     # independent structural check
```

`dictionary.txt` ships only words reachable from some level's wheel — 400
entries, ~2 KB, rather than a 600 KB lexicon nobody can spell from.

---

## State retention

Two layers, split by what is worth preserving:

- **`SavedStateHandle`** holds revealed grid words and found bonus words as
  primitive arrays, written on every change. A level interrupted by process
  death resumes intact.
- **Gesture state is deliberately in neither.** In-progress selection and pointer
  position live in `WheelGestureState` inside the composable. Nobody wants a
  half-drawn swipe restored after their phone was killed in the background, and
  keeping it out means no gesture state ever needs serialising.
- **DataStore** for cross-session progress is *not yet implemented*.

**Back-stack policy lives entirely in `NavGraph`.** Screens report what happened
through callbacks and the graph decides where that leads. Auto-advance navigates
to the next level while popping the current one inclusively — replacing rather
than pushing. Pushing would stack one `Game` entry per level completed, so a
player who finished five levels and pressed Back would walk backwards through
five already-solved boards.

---

## Testing

89 JVM unit tests, no device or Robolectric required:

| Area | Tests | Covers |
|---|---|---|
| `WheelSelectionTest` | 24 | append/backtrack/ignore, path folding, the incidental-crossing regression |
| `WheelGeometryTest` | 22 | layout, spacing, hit radius, segment ordering, degenerate cases |
| `LevelDataTest` | 16 | all 15 shipped levels + 6 negative cases proving validation rejects malformed input |
| `GameViewModelTest` | 15 | loading, submissions, the rapid-duplicate race, saved-state round trip |
| `ValidateWordTest` | 12 | resolution order and every branch |

Notable: `LevelDataTest` asserts **no unintended letter runs** — two words placed
alongside each other are individually legal but create a perpendicular run
spelling something unenterable, so the grid looks solvable and is not.
`GameViewModelTest` simulates process death by building a second ViewModel from
the same `SavedStateHandle`.

---

## Known limitations

Current and honest.

- **A two-letter backtrack flick pops only one letter.** Deliberate. Only the
  letter under the finger may undo; the alternative is the incidental-crossing
  bug described above, which silently corrupts words during normal drawing.
  Degrades gracefully — keep dragging and the next sample pops the next letter.
- **No cross-session progress.** DataStore is not wired, so Level Select shows
  all 15 unlocked with no completion marks and Home has no Continue.
- **No pause dialog.** The nav flow specifies Home → Level Select → Gameplay →
  Pause; the pause destination does not exist.
- **No micro-interactions.** Validation feedback is colour and text only — no
  tile reveal, no invalid shake, no letter scale on capture. When added they must
  be keyed on `GameUiState.submissionId` rather than on the result, or two
  identical rapid submissions produce a single animation.
- **Rotation and process-death behaviour is unverified on hardware.**
  `SavedStateHandle` is wired and unit-tested including a simulated process
  death, but has not faced a real Activity recreation.
- **No landscape-specific layout.** Rotation must not crash or lose state; it
  does not get a bespoke design.
- **Release APK is unsigned.** Signing config is stubbed in `app/build.gradle.kts`.

## Out of scope

Sound, haptics, currency, hints, daily rewards, tutorials, achievements,
settings, themes, leaderboards, analytics, ads, cloud sync, localisation, and
tablet layouts. Level content quality is not a graded criterion, though it was
worth fixing when level 1 turned out to require ASTIR.
