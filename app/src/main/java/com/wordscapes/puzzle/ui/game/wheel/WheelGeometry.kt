package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure geometry for the letter wheel. No drawing, no gesture state, no Compose
 * runtime beyond [Offset] — everything here is a function of the available
 * size and the number of letters, so it is fully unit-testable on the JVM.
 *
 * Instances are computed once per size change and cached by the composable.
 * Recomputing trig on every pointer event would be wasteful, but the real
 * reason to cache is stability: the hit-test results must not shift under the
 * finger mid-drag because a recomposition recalculated a radius slightly
 * differently.
 *
 * Coordinate space is the wheel composable's local pixel space, origin
 * top-left, y down — the same space [androidx.compose.ui.input.pointer.PointerInputChange.position]
 * reports in, so pointer positions can be passed straight in with no mapping.
 */
@Immutable
data class WheelGeometry(
    /** Centre of the wheel disc, in local pixels. */
    val center: Offset,
    /** Radius of the dark disc the letters sit on. */
    val discRadius: Float,
    /** Radius of the ring the letter centres lie on. */
    val orbitRadius: Float,
    /** Radius of each drawn letter circle. */
    val letterRadius: Float,
    /** Radius of each letter's touch target. Deliberately > [letterRadius]. */
    val hitRadius: Float,
    /**
     * Radius within which the finger counts as *resting on* a letter, as
     * opposed to merely reaching towards it. Deliberately < [letterRadius].
     */
    val retraceRadius: Float,
    /** Letter centres, index-aligned with the level's letter list. */
    val letterCenters: List<Offset>,
) {
    val letterCount: Int get() = letterCenters.size

    /**
     * Which letter, if any, contains [point].
     *
     * Returns the index of the *nearest* letter centre within [hitRadius], not
     * the first one found. That distinction matters: because hit radii are
     * inflated past the drawn circles they can overlap, especially at seven
     * letters. First-match would make the result depend on list order, so a
     * finger in the overlap between letters 2 and 3 would always pick 2 —
     * which reads as the wheel favouring one side.
     *
     * Returns -1 for no hit.
     */
    fun hitTest(point: Offset): Int = nearestWithin(point, hitRadius)

    /**
     * Which letter the finger is *resting on*, using the much tighter
     * [retraceRadius]. Returns -1 unless the point is well inside a drawn
     * circle.
     *
     * Two thresholds, because adding a letter and undoing one are not
     * symmetric. Reaching towards a letter should be forgiving — the reported
     * pointer often lands outside a contact patch the player thinks is dead
     * centre. Undoing should require deliberateness, because a false positive
     * there destroys work rather than merely failing to create it.
     *
     * Sharing one radius caused a bug on device: drawing forward past an
     * already-selected letter put the finger inside that letter's generous hit
     * circle without ever visibly touching it, which read as a retrace and
     * silently truncated the word.
     *
     * Drawn circles never overlap (see the geometry tests), so a radius at or
     * below [letterRadius] can match at most one letter — no ambiguity.
     */
    fun hitTestResting(point: Offset): Int = nearestWithin(point, retraceRadius)

    private fun nearestWithin(point: Offset, radius: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in letterCenters.indices) {
            val d = distance(letterCenters[i], point)
            if (d <= radius && d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    /**
     * Every letter crossed by the straight segment [from] → [to], in the order
     * the finger passes through them.
     *
     * This is the fix for the single worst gesture bug in a wheel like this.
     * Pointer events are sampled, not continuous — at ~120 Hz a fast flick
     * across the wheel might produce only five or six samples for the whole
     * drag. Testing only the sampled points means a letter sitting between two
     * consecutive samples is silently skipped, and the player sees a swipe
     * they clearly made produce the wrong word. Inflating [hitRadius] helps,
     * but cannot fix it: the faster the drag, the wider the gaps.
     *
     * Rather than sub-sampling the segment (which just moves the threshold),
     * this solves it exactly: for each letter, take the perpendicular distance
     * from its centre to the segment. If that is within [hitRadius] the finger
     * passed through it, however sparse the sampling. Results are ordered by
     * where along the segment the closest approach happened, so a segment
     * crossing three letters returns them in travel order.
     *
     * With at most eight letters this is a handful of dot products — cheaper
     * than the allocation a sub-sampling loop would do.
     */
    fun hitTestSegment(from: Offset, to: Offset): List<Int> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val lengthSquared = dx * dx + dy * dy

        // Degenerate segment (finger held still): fall back to a point test.
        if (lengthSquared < EPSILON) {
            val hit = hitTest(to)
            return if (hit >= 0) listOf(hit) else emptyList()
        }

        val crossed = ArrayList<Pair<Float, Int>>(letterCenters.size)
        for (i in letterCenters.indices) {
            val c = letterCenters[i]
            // Project the centre onto the segment, clamped to its endpoints.
            val t = (((c.x - from.x) * dx + (c.y - from.y) * dy) / lengthSquared)
                .coerceIn(0f, 1f)
            val closestX = from.x + t * dx
            val closestY = from.y + t * dy
            val d = hypot(c.x - closestX, c.y - closestY)
            if (d <= hitRadius) {
                crossed.add(t to i)
            }
        }
        crossed.sortBy { it.first }
        return crossed.map { it.second }
    }

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    companion object {
        private const val EPSILON = 0.0001f

        /**
         * Fraction of the smaller dimension the disc occupies. Leaves margin so
         * the disc does not touch the screen edges.
         */
        private const val DISC_FRACTION = 0.46f

        /**
         * How far out the letter centres sit, as a fraction of the disc radius.
         * Below ~0.55 the letters bunch in the middle at seven letters; above
         * ~0.72 they overhang the disc edge.
         */
        private const val ORBIT_FRACTION = 0.66f

        /**
         * Letter circle radius as a fraction of the gap between adjacent letter
         * centres. 0.40 leaves a visible gap; much above 0.45 and neighbouring
         * circles touch.
         */
        private const val LETTER_RADIUS_FRACTION = 0.40f

        /**
         * Hard cap on letter radius as a fraction of the disc radius.
         *
         * Without this, radius is derived purely from the gap between adjacent
         * centres — which at three or four letters is huge, giving circles
         * that overhang the disc edge entirely. The cap also stops letter size
         * ballooning at low counts, so a five-letter wheel and a three-letter
         * wheel draw letters at the same size rather than the short one
         * looking like a different game.
         *
         * ORBIT_FRACTION + this must stay <= 1.0, which is what guarantees
         * letters always fit inside the disc: 0.66 + 0.28 = 0.94, leaving 6%
         * of the disc radius as visual margin.
         */
        private const val MAX_LETTER_RADIUS_FRACTION = 0.28f

        /**
         * How much bigger the touch target is than the drawn circle.
         *
         * This is the number to tune on hardware, and it is deliberately
         * aggressive. A hit target matching the visual reads as unresponsive,
         * because a finger contact patch is roughly 9 mm across while the
         * reported pointer position is a single point somewhere inside it —
         * the player aims at the letter and the reported point lands just
         * outside. 1.6 is the starting value; raise it if letters feel like
         * they need precision, lower it if adjacent letters get picked up on
         * a deliberate slow drag.
         */
        const val HIT_RADIUS_MULTIPLIER = 1.6f

        /**
         * Resting radius as a fraction of the drawn circle.
         *
         * Below 1.0 by design: the finger must be visibly on the letter, not
         * merely near it. Raise it and unintended retraces creep back; lower
         * it and deliberate retracing starts to feel finicky.
         */
        const val RETRACE_RADIUS_FRACTION = 0.85f

        /**
         * Absolute floor on the touch target, in pixels, passed in by the
         * caller after converting from dp. Android's minimum recommended touch
         * target is 48 dp across, so 24 dp of radius. At five big letters the
         * computed radius already exceeds this; at seven on a small screen it
         * would not.
         */
        fun compute(
            widthPx: Float,
            heightPx: Float,
            letterCount: Int,
            minHitRadiusPx: Float,
        ): WheelGeometry {
            require(letterCount > 0) { "wheel needs at least one letter" }

            val center = Offset(widthPx / 2f, heightPx / 2f)
            val discRadius = minOf(widthPx, heightPx) * DISC_FRACTION
            val orbitRadius = discRadius * ORBIT_FRACTION

            // Chord between adjacent letter centres. With one letter there is
            // no neighbour, so fall back to the orbit radius.
            val adjacentGap =
                if (letterCount < 2) orbitRadius
                else 2f * orbitRadius * sin(PI.toFloat() / letterCount)

            // Spacing-derived size, capped so low letter counts cannot produce
            // circles that overhang the disc.
            val letterRadius = minOf(
                adjacentGap * LETTER_RADIUS_FRACTION,
                discRadius * MAX_LETTER_RADIUS_FRACTION,
            )
            val hitRadius = maxOf(letterRadius * HIT_RADIUS_MULTIPLIER, minHitRadiusPx)
            val retraceRadius = letterRadius * RETRACE_RADIUS_FRACTION

            // First letter at twelve o'clock, then clockwise. Screen y grows
            // downward, so the usual -PI/2 start angle puts index 0 at the top.
            val centers = List(letterCount) { i ->
                val angle = -PI.toFloat() / 2f + TWO_PI * i / letterCount
                Offset(
                    x = center.x + orbitRadius * cos(angle),
                    y = center.y + orbitRadius * sin(angle),
                )
            }

            return WheelGeometry(
                center = center,
                discRadius = discRadius,
                orbitRadius = orbitRadius,
                letterRadius = letterRadius,
                hitRadius = hitRadius,
                retraceRadius = retraceRadius,
                letterCenters = centers,
            )
        }

        private const val TWO_PI = 2f * PI.toFloat()
    }
}
