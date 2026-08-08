package com.wordscapes.puzzle.ui.game.wheel

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Geometry is pure, so it gets real tests rather than hand-verification on a
 * device. The segment tests matter most: they cover the fast-swipe case that
 * is hard to reproduce deliberately by hand and easy to regress.
 */
class WheelGeometryTest {

    private val size = 1000f
    private val minHit = 60f

    private fun geo(letters: Int, minHitRadiusPx: Float = minHit) =
        WheelGeometry.compute(size, size, letters, minHitRadiusPx)

    // ── Layout ───────────────────────────────────────────────────────────────

    @Test
    fun `produces one centre per letter`() {
        for (n in 3..7) {
            assertEquals(n, geo(n).letterCenters.size)
        }
    }

    @Test
    fun `first letter sits at twelve o'clock`() {
        val g = geo(5)
        val first = g.letterCenters.first()
        assertEquals("x should equal centre x", g.center.x, first.x, 0.01f)
        assertTrue(
            "first letter must be above centre (screen y grows downward)",
            first.y < g.center.y,
        )
    }

    @Test
    fun `letters are evenly spaced around the orbit`() {
        val g = geo(6)
        val distances = g.letterCenters.map { hypot(it.x - g.center.x, it.y - g.center.y) }
        distances.forEach {
            assertEquals("every letter sits on the orbit radius", g.orbitRadius, it, 0.5f)
        }

        // Adjacent gaps should all match, including the wrap-around pair.
        val gaps = g.letterCenters.indices.map { i ->
            val a = g.letterCenters[i]
            val b = g.letterCenters[(i + 1) % g.letterCount]
            hypot(a.x - b.x, a.y - b.y)
        }
        val first = gaps.first()
        gaps.forEach { assertEquals("uneven spacing", first, it, 0.5f) }
    }

    @Test
    fun `letters run clockwise`() {
        val g = geo(4)
        // Index 0 top, 1 right, 2 bottom, 3 left.
        assertTrue(g.letterCenters[1].x > g.center.x)
        assertTrue(g.letterCenters[2].y > g.center.y)
        assertTrue(g.letterCenters[3].x < g.center.x)
    }

    @Test
    fun `drawn letters never overlap each other`() {
        for (n in 3..8) {
            val g = geo(n)
            val a = g.letterCenters[0]
            val b = g.letterCenters[1]
            val gap = hypot(a.x - b.x, a.y - b.y)
            assertTrue(
                "at $n letters, circles of r=${g.letterRadius} overlap at gap=$gap",
                gap > 2f * g.letterRadius,
            )
        }
    }

    @Test
    fun `letters stay inside the disc`() {
        for (n in 3..8) {
            val g = geo(n)
            g.letterCenters.forEach { c ->
                val fromCentre = hypot(c.x - g.center.x, c.y - g.center.y)
                assertTrue(
                    "letter at $n overhangs the disc edge",
                    fromCentre + g.letterRadius <= g.discRadius,
                )
            }
        }
    }

    // ── Hit radius ───────────────────────────────────────────────────────────

    @Test
    fun `hit radius always exceeds the drawn radius`() {
        for (n in 3..8) {
            val g = geo(n)
            assertTrue(
                "hit target must be larger than the visual at $n letters",
                g.hitRadius > g.letterRadius,
            )
        }
    }

    @Test
    fun `hit radius respects the minimum touch target floor`() {
        // Tiny wheel, big floor: the floor must win.
        val g = WheelGeometry.compute(200f, 200f, 7, minHitRadiusPx = 80f)
        assertEquals(80f, g.hitRadius, 0.01f)
    }

    // ── Point hit testing ────────────────────────────────────────────────────

    @Test
    fun `hit test finds the letter under an exact centre`() {
        val g = geo(5)
        g.letterCenters.forEachIndexed { i, c ->
            assertEquals("centre of letter $i should hit letter $i", i, g.hitTest(c))
        }
    }

    @Test
    fun `hit test misses the middle of the wheel`() {
        val g = geo(5)
        assertEquals(-1, g.hitTest(g.center))
    }

    @Test
    fun `hit test misses well outside the disc`() {
        val g = geo(5)
        assertEquals(-1, g.hitTest(Offset(0f, 0f)))
    }

    @Test
    fun `hit test picks the nearest letter, not the first in list order`() {
        // Seven letters gives the tightest packing, so hit radii overlap most.
        val g = geo(7)
        val a = g.letterCenters[2]
        val b = g.letterCenters[3]
        val midpoint = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

        // Nudge decisively toward letter 3.
        val towardB = Offset(
            midpoint.x + (b.x - midpoint.x) * 0.5f,
            midpoint.y + (b.y - midpoint.y) * 0.5f,
        )
        assertEquals(
            "a point nearer letter 3 must not resolve to letter 2",
            3, g.hitTest(towardB),
        )
    }

    // ── Segment hit testing: the fast-swipe path ─────────────────────────────

    @Test
    fun `segment across the wheel catches letters a point test would skip`() {
        val g = geo(6)
        val from = g.letterCenters[0]
        val to = g.letterCenters[3]   // diametrically opposite

        // A fast flick reports only the endpoints. Point-testing those two
        // samples finds two letters; the segment must find the path between.
        val pointOnly = listOfNotNull(
            g.hitTest(from).takeIf { it >= 0 },
            g.hitTest(to).takeIf { it >= 0 },
        )
        val viaSegment = g.hitTestSegment(from, to)

        assertEquals(listOf(0, 3), pointOnly)
        assertTrue("segment must start at the origin letter", viaSegment.first() == 0)
        assertTrue("segment must end at the destination letter", viaSegment.last() == 3)
    }

    @Test
    fun `segment returns letters in travel order`() {
        val g = geo(6)
        val forward = g.hitTestSegment(g.letterCenters[0], g.letterCenters[3])
        val backward = g.hitTestSegment(g.letterCenters[3], g.letterCenters[0])
        assertEquals(
            "reversing the segment must reverse the order",
            forward, backward.reversed(),
        )
    }

    @Test
    fun `segment through adjacent letters catches both`() {
        val g = geo(5)
        val result = g.hitTestSegment(g.letterCenters[0], g.letterCenters[1])
        assertTrue("expected both endpoints, got $result", result.containsAll(listOf(0, 1)))
        assertEquals(0, result.first())
        assertEquals(1, result.last())
    }

    @Test
    fun `zero length segment behaves like a point test`() {
        val g = geo(5)
        val c = g.letterCenters[2]
        assertEquals(listOf(2), g.hitTestSegment(c, c))
        assertEquals(emptyList<Int>(), g.hitTestSegment(g.center, g.center))
    }

    @Test
    fun `segment missing every letter returns empty`() {
        val g = geo(5)
        // A short segment right through the dead centre of the wheel.
        val a = Offset(g.center.x - 5f, g.center.y)
        val b = Offset(g.center.x + 5f, g.center.y)
        assertEquals(emptyList<Int>(), g.hitTestSegment(a, b))
    }

    @Test
    fun `segment does not report the same letter twice`() {
        val g = geo(7)
        val result = g.hitTestSegment(g.letterCenters[1], g.letterCenters[5])
        assertEquals(
            "duplicates would double-append during selection",
            result.size, result.toSet().size,
        )
    }

    // ── Stability ────────────────────────────────────────────────────────────

    @Test
    fun `geometry is deterministic for the same inputs`() {
        val a = geo(6)
        val b = geo(6)
        assertEquals("same inputs must give an equal instance", a, b)
    }

    @Test
    fun `geometry changes when the size changes`() {
        val a = WheelGeometry.compute(1000f, 1000f, 6, minHit)
        val b = WheelGeometry.compute(800f, 800f, 6, minHit)
        assertNotEquals(a, b)
    }

    @Test
    fun `non-square bounds use the smaller dimension`() {
        val wide = WheelGeometry.compute(1600f, 800f, 5, minHit)
        val tall = WheelGeometry.compute(800f, 1600f, 5, minHit)
        assertEquals(
            "disc should be sized by the constraining dimension",
            wide.discRadius, tall.discRadius, 0.01f,
        )
        assertTrue("disc must fit vertically", wide.discRadius * 2 <= 800f)
    }

    @Test
    fun `single letter does not divide by zero`() {
        val g = geo(1)
        assertEquals(1, g.letterCount)
        assertTrue(g.letterRadius > 0f)
        assertTrue(g.hitRadius > 0f)
        assertTrue(abs(g.letterCenters[0].x - g.center.x) < 0.01f)
    }
}
