package app.quenya.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Replays py-fsrs 6.3.2 traces (fuzzing off) through the Kotlin port. */
class FsrsReferenceTest {
    private val scheduler = FsrsScheduler()

    @Test
    fun matchesReferenceTraces() {
        var reviews = 0
        FsrsReferenceData.traces.forEachIndexed { ti, trace ->
            var card = FsrsCard()
            trace.split("\n").forEachIndexed { li, line ->
                val f = line.split(" ")
                val now = f[0].toLong()
                val rating = Rating.entries.first { it.value == f[1].toInt() }
                card = scheduler.review(card, rating, now)
                val where = "trace $ti review $li"
                assertEquals(f[2].toInt(), card.state.value, "$where state")
                assertEquals(f[3].toInt(), card.step ?: -1, "$where step")
                assertClose(f[4].toDouble(), card.stability!!, "$where stability")
                assertClose(f[5].toDouble(), card.difficulty!!, "$where difficulty")
                assertEquals(f[6].toLong(), card.dueMs, "$where due")
                reviews++
            }
        }
        assertEquals(1200, reviews)
    }

    @Test
    fun retrievabilityDecaysAndStartsAtOne() {
        var card = scheduler.review(FsrsCard(), Rating.Good, 0L)
        card = scheduler.review(card, Rating.Good, 10 * FsrsScheduler.MINUTE)
        val day = FsrsScheduler.DAY_MS
        val r0 = scheduler.retrievability(card, card.lastReviewMs!!)
        val r10 = scheduler.retrievability(card, card.lastReviewMs!! + 10 * day)
        assertClose(1.0, r0, "r at t=0")
        assertTrue(r10 < r0, "retrievability should decay")
    }

    private fun assertClose(expected: Double, actual: Double, msg: String) {
        assertTrue(abs(expected - actual) <= 1e-9 * maxOf(1.0, abs(expected)), "$msg: expected $expected got $actual")
    }
}
