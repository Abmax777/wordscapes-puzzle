# Wordscapes-style Word Puzzle

An Android word puzzle: swipe letters on a circular wheel to spell words and
fill a crossword grid. Kotlin, Jetpack Compose, single Activity, MVVM.

15 levels, playable end to end with auto-advance, cross-session progress and a
pause menu. Gesture and lifecycle behaviour was verified by hand on a physical
device; [Known limitations](#known-limitations) is honest rather than
aspirational.

## Build and run

```bash
./gradlew :app:installDebug        # onto a connected device
./gradlew :app:testDebugUnitTest   # 94 JVM unit tests, no device needed
./gradlew :app:assembleRelease     # minified, shrunk, signed
```

JDK 17. AGP 9.2.1 / Gradle 9.4.1, `minSdk` 24, `compileSdk` 36. The release
build signs from a gitignored `keystore.properties`; when absent it stays
unsigned rather than failing, so the repo clones and assembles without a key.
R8 is on in release, and `proguard-rules.pro` keeps the kotlinx.serialization
serializers — they are reached reflectively, so without those rules the build
succeeds and `levels.json` parsing fails at runtime. The release APK has been
installed and played through.

## Architecture

```
ui/       home · levelselect · game (wheel, grid) · pause · navigation · theme
domain/   model · repository (interfaces) · usecase
data/     level · dictionary · progress
di/       Hilt modules
tools/    level generator — build tooling, not part of the app
```

Three decisions carry the rest.

**Dependencies point inwards.** `domain/` declares `LevelCatalog`, `WordLookup`
and `ProgressStore` as interfaces; `data/` implements them; Hilt binds them.
Both implementations need an Android `Context`, so depending on the interfaces
is what lets `ValidateWord` and `GameViewModel` be tested on the JVM with a
two-line fake instead of under Robolectric.

**One `StateFlow<GameUiState>` per screen ViewModel.** A screen observes a
single immutable snapshot rather than combining several flows, which removes
the class of bug where one flow has updated and another has not.

**Back-stack policy lives entirely in `NavGraph`.** Screens report what happened
through callbacks and never touch the `NavController`, so every navigation
decision has one home. Destinations are `@Serializable` types used with
type-safe `composable<T>` / `dialog<T>`.

## Swipe logic

`ui/game/wheel/` splits into four files: `WheelGeometry` (pure maths, no Compose
types, JVM-testable), `WheelSelection` (pure selection rules), `WheelGestureState`
(a `@Stable` holder of in-flight state) and `LetterWheel` (drawing and the
pointer loop).

**Raw `awaitEachGesture`, not `detectDragGestures`.** The latter waits for ~16 dp
of touch slop before reporting a drag, which would discard the start of every
swipe and prevent the initial press from selecting a letter at all.

**Three radii, not one.** Early builds shared a single hit radius across
operations whose costs are asymmetric, which produced three separate bugs.
Selecting a letter is forgiving (`hitRadius`, 1.6× the visual, floored at
Android's 48 dp touch target) because a missed letter is a retry. Undoing one is
strict (`retraceRadius`, 0.85× the visual) because an accidental deselection
destroys work in progress. The visual radius is neither.

**Segment hit testing, not point sampling.** At speed, the gap between pointer
samples exceeds a letter's diameter, so testing sample positions alone drops
letters. Each move tests the *segment* from the previous position to the current
one, by perpendicular distance from each letter centre, ordered along the
segment — so a fast swipe collects letters in travel order.

**Retrace is decided by where the finger rests, never by what the path swept.**
Swiping R→A→C→E on a wheel where the arc from A to C passes back over R would
otherwise pop R and yield RCE. If the finger currently rests inside a letter
already selected *and that letter is not the last one*, the selection truncates
there; otherwise the crossed letters are appended.

**Composition is never re-entered during a drag.** Pointer state lives in
`WheelGestureState` and is read inside `drawWithCache`'s draw phase, so a swipe
invalidates the draw only. Measured on device: `LetterWheel` recomposes once
across a five-second continuous drag while `GameScreen` recomposes 21 times
around it.

Submission clears the selection *before* invoking the callback, so a second
swipe starting immediately cannot append onto the previous word, and
`GameViewModel` serialises submissions through a `Channel` so two rapid
identical swipes cannot both pass validation.

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

`letters` is the wheel set; every word is an anagram subset of it.

**Cell occupancy and intersections are derived at load, never stored.**
`LevelMapper` rebuilds the grid while validating each placement against every
other — spellable from the wheel, inside bounds, agreeing at intersections, no
duplicates. A malformed level throws `LevelFormatException` naming the id at
startup rather than producing an unwinnable grid that only reveals itself three
words in. `GridCell` holds a *set* of word indices, so revealing one word fills
the shared letters of every word crossing it.

Levels are generated offline by `tools/` (not part of the app build): SCOWL
vocabulary, a 280-entry content blocklist, a frequency filter so grid words are
words players actually know, greedy placement biased toward compact grids, and
`validate_levels.py`, which re-derives everything from the emitted JSON without
importing the generator. `dictionary.txt` ships only the ~400 words reachable
from some level's wheel rather than a full lexicon.

**Wheel size.** The brief specifies 3–5 letters. `LevelMapper` accepts 3–8 and
`WheelGeometryTest` exercises the layout across that range, but the shipped
levels ramp 5 → 6 → 7 in blocks of five, because a five-letter cap makes levels
11–15 indistinguishable from levels 1–5 and the reference product ramps the same
way.

## State retention

Two layers, split by what is worth preserving.

`SavedStateHandle` holds revealed grid words and found bonus words as primitive
arrays, so a level interrupted by process death resumes intact. DataStore holds
cross-session completion; *unlocked* is derived from that rather than stored,
because two persisted fields that must agree are two fields that can disagree.
Gesture state is in neither — nobody wants a half-drawn swipe restored after a
background kill.

**Pause is a `dialog<T>` destination, not a `composable<T>`.** It renders over
Game, so Game stays composed with its ViewModel alive and Back dismisses only
the dialog. As a screen, Back would pop the player out of the level.

**Auto-advance replaces rather than pushes**, popping the current level
inclusively. Pushing would stack one `Game` entry per level solved, so a player
five levels in would walk backwards through five finished boards.

Verified by hand on a physical device: rotation on every screen including
mid-word, backgrounding and return, "don't keep activities", `am kill` via
Recents, and `am force-stop` and relaunch. The two kill cases differ by design —
a system kill restores the board, a force-stop does not, but completed levels
survive both. That split is why there are two persistence layers.

## Testing

94 JVM unit tests, no device or Robolectric:

| Suite | Tests | Covers |
|---|---|---|
| `WheelGeometryTest` | 27 | layout, spacing, hit radii, segment ordering, degenerate cases |
| `WheelSelectionTest` | 21 | append and retrace, path folding, two on-device regressions |
| `GameViewModelTest` | 18 | submissions, the rapid-duplicate race, saved-state round trip |
| `LevelDataTest` | 16 | all 15 shipped levels, plus 6 negative cases |
| `ValidateWordTest` | 12 | resolution order and every branch |

`LevelDataTest` asserts no *unintended letter runs*: two words placed alongside
each other are individually legal but can create a perpendicular run spelling
something unenterable, so the grid looks solvable and is not.
`GameViewModelTest` simulates process death by building a second ViewModel from
the same `SavedStateHandle`.

## Known limitations

**A retracing sample landing between letters pops nothing.** With the finger in
dead space there is no way to distinguish a retrace from a forward sweep, so the
sample is a no-op and the next one inside a letter resolves it.

**No landscape-specific layout.** Rotation neither crashes nor loses state, but
landscape reuses the portrait composition and sits centred with empty margins.
Locking to portrait — which Wordscapes itself does — was rejected because
rotation is the cheapest way to demonstrate that configuration-change handling
is correct, and locking would hide it rather than prove it.

**Out of scope:** sound, haptics, currency, hints, daily rewards, tutorials,
achievements, settings, themes, leaderboards, analytics, ads, cloud sync,
localisation and tablet layouts.
