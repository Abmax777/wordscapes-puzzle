package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure geometry for the letter wheel — a function of size and letter count, so
 * it unit-tests on the JVM. Coordinates are the composable's local pixel space.
 */
@Immutable
data class WheelGeometry(
    val center: Offset,
    val discRadius: Float,
    /** Radius of the ring the letter centres lie on. */
    val orbitRadius: Float,
    /** Radius of each drawn letter circle. */
    val letterRadius: Float,
    /** Touch target. Deliberately larger than [letterRadius]. */
    val hitRadius: Float,
    /** Counts as *resting on* a letter. Deliberately smaller than [letterRadius]. */
    val retraceRadius: Float,
    val letterCenters: List<Offset>,
) {
    val letterCount: Int get() = letterCenters.size

    /**
     * Nearest letter within [hitRadius], or -1. Nearest rather than first-match
     * because inflated hit circles overlap, and order-dependence reads as bias.
     */
    fun hitTest(point: Offset): Int = nearestWithin(point, hitRadius)

    /**
     * Nearest letter within the much tighter [retraceRadius], or -1. Reaching
     * is forgiving; undoing must be deliberate. See README, swipe logic.
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
     * Letters crossed by the segment [from] → [to], in travel order. Perpendicular
     * distance, so a fast flick cannot skip a letter between two sparse samples.
     */
    fun hitTestSegment(from: Offset, to: Offset): List<Int> {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val lengthSquared = dx * dx + dy * dy

        // Finger held still.
        if (lengthSquared < EPSILON) {
            val hit = hitTest(to)
            return if (hit >= 0) listOf(hit) else emptyList()
        }

        val crossed = ArrayList<Pair<Float, Int>>(letterCenters.size)
        for (i in letterCenters.indices) {
            val c = letterCenters[i]
            val t = (((c.x - from.x) * dx + (c.y - from.y) * dy) / lengthSquared)
                .coerceIn(0f, 1f)
            val d = hypot(c.x - (from.x + t * dx), c.y - (from.y + t * dy))
            if (d <= hitRadius) crossed.add(t to i)
        }
        crossed.sortBy { it.first }
        return crossed.map { it.second }
    }

    private fun distance(a: Offset, b: Offset): Float = hypot(a.x - b.x, a.y - b.y)

    companion object {
        private const val EPSILON = 0.0001f
        private const val TWO_PI = 2f * PI.toFloat()

        private const val DISC_FRACTION = 0.46f
        private const val ORBIT_FRACTION = 0.66f
        private const val LETTER_RADIUS_FRACTION = 0.40f

        /** Cap on letter radius. ORBIT_FRACTION + this <= 1.0 keeps letters inside the disc. */
        private const val MAX_LETTER_RADIUS_FRACTION = 0.28f

        /** Tune on hardware. A target matching the visual reads as unresponsive. */
        const val HIT_RADIUS_MULTIPLIER = 1.6f

        /** Below 1.0 by design: the finger must be visibly on the letter to undo. */
        const val RETRACE_RADIUS_FRACTION = 0.85f

        /** @param minHitRadiusPx floor for the touch target, normally 24 dp in px. */
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

            val adjacentGap =
                if (letterCount < 2) orbitRadius
                else 2f * orbitRadius * sin(PI.toFloat() / letterCount)

            val letterRadius = minOf(
                adjacentGap * LETTER_RADIUS_FRACTION,
                discRadius * MAX_LETTER_RADIUS_FRACTION,
            )

            // Index 0 at twelve o'clock, then clockwise. Screen y grows downward.
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
                hitRadius = maxOf(letterRadius * HIT_RADIUS_MULTIPLIER, minHitRadiusPx),
                retraceRadius = letterRadius * RETRACE_RADIUS_FRACTION,
                letterCenters = centers,
            )
        }
    }
}
