package app.quenya.core

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The in-memory store never suspends, so a bare stdlib coroutine start is enough. */
private fun <T> runSuspend(block: suspend () -> T): T {
    var result: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
    return result!!.getOrThrow()
}

class StudyTest {
    private val course = Fixtures.course()
    private val day = FsrsScheduler.DAY_MS

    @Test fun fullCourseRunsEndToEnd() = runSuspend {
        var now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        var rated = 0
        for (n in 1..engine.sessionCount) {
            val steps = engine.buildSteps(seed = n.toLong())
            assertTrue(steps.isNotEmpty(), "session $n has no steps")
            steps.filterIsInstance<SessionStep.Review>().forEach { engine.rate(it.lemma.id, Rating.Good); rated++ }
            engine.finishSession()
            assertEquals(n, store.completedSessions())
            now += day                                           // one session per day
        }
        assertEquals(0, engine.buildSteps(1).size, "nothing after the last session")
        val taught = course.curriculum.flatMap { s -> s.slots.flatMap { it.lemmas } }.toSet()
        assertEquals(taught, store.cards().keys, "one card per taught word")
        assertTrue(rated > 100, "reviews should accumulate, got $rated")
        assertEquals(engine.sessionCount, store.streak().count, "daily sessions give a full streak")
    }

    @Test fun newWordsBecomeDueAndFailingShortensTheInterval() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        repeat(9) { engine.finishSession() }                      // session 9 is the first vocab session
        val ids = store.cards().keys.toList()
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, engine.dueCount())
        val good = ids[0]; val again = ids[1]
        engine.rate(good, Rating.Good); engine.rate(again, Rating.Again)
        assertTrue(store.cards().getValue(again).dueMs <= store.cards().getValue(good).dueMs)
    }

    @Test fun killedMidSessionResumesToTheExactSameSteps() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        val steps = engine.buildSteps(seed = 42)
        assertTrue(steps.isNotEmpty())
        engine.advance(2)
        // Simulate the process dying and restarting: a brand-new engine over the same durable store.
        val revived = StudyEngine(course, store) { now }
        val resumed = revived.resume()
        assertTrue(resumed != null, "resume() should find the saved in-progress session")
        val (resumedSteps, resumedIndex) = resumed
        assertEquals(steps.map { it::class }, resumedSteps.map { it::class }, "resumed steps should match exactly")
        assertEquals(2, resumedIndex)
    }

    @Test fun finishingASessionClearsResumeState() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        engine.buildSteps(seed = 1)
        engine.finishSession()
        assertEquals(null, engine.resume())
    }

    @Test fun streakResetsAfterAGapAndHoldsWithinADay() = runSuspend {
        var now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        engine.finishSession(); now += 1_000; engine.finishSession()
        assertEquals(1, store.streak().count)                      // same day: no double count
        now += day; engine.finishSession()
        assertEquals(2, store.streak().count)
        now += 3 * day; engine.finishSession()
        assertEquals(1, store.streak().count)                      // gap: reset
    }
}
