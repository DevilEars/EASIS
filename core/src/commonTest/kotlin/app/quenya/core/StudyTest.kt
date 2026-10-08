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
        val after = engine.buildSteps(1)
        assertTrue(after.isNotEmpty() && after.all { it is SessionStep.Review }, "after the course: reviews only")
        val taught = course.curriculum.flatMap { it.lemmas }.toSet()
        assertEquals(taught, store.cards().keys, "one card per taught word")
        assertTrue(rated >= 70, "reviews should accumulate, got $rated")
        assertEquals(engine.sessionCount, store.streak().count, "daily sessions give a full streak")
    }

    @Test fun newWordsBecomeDueAndFailingShortensTheInterval() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        engine.finishSession()                                     // session 1 teaches four words
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

    @Test fun streakUsesLocalDayAcrossTheUtcBoundary() = runSuspend {
        val offset = 2 * 3_600_000L                                // UTC+2, no DST
        val utcMidnight = 1_800_000_000_000L / day * day
        var now = utcMidnight - 3_600_000L                         // 23:00 UTC = 01:00 local
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store, utcOffsetMs = { offset }) { now }
        engine.finishSession()
        val firstDay = store.streak().lastDay
        now += 3_600_000L                                          // 00:00 UTC, still the same local day
        engine.finishSession()
        assertEquals(1, store.streak().count)
        assertEquals(firstDay, store.streak().lastDay)
        now += day
        engine.finishSession()
        assertEquals(2, store.streak().count)
        assertEquals(firstDay + 1, store.streak().lastDay)
    }

    @Test fun readerShowsElenSilaBeforeItIsUnlocked() = runSuspend {
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { 0L }
        assertEquals(listOf("elen-sila"), engine.unlockedPhrases().map { it.id })
        engine.finishSession()
        val ids = engine.unlockedPhrases().map { it.id }
        assertEquals(listOf("elen-sila"), ids.filter { it == "elen-sila" })
        assertTrue("aiya-earendil" !in ids)
    }

    private suspend fun finishCourse(engine: StudyEngine) { repeat(engine.sessionCount) { engine.finishSession() } }

    @Test fun reviewsOnlySessionAfterTheCourse() = runSuspend {
        var now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        finishCourse(engine)
        now += day
        val steps = engine.buildSteps(seed = 9)
        assertTrue(steps.isNotEmpty() && steps.size <= 12 && steps.all { it is SessionStep.Review })
        engine.advance(1)
        val (resumed, at) = StudyEngine(course, store) { now }.resume()!!
        assertEquals(steps, resumed)
        assertEquals(1, at)
        val streakBefore = store.streak().count
        engine.finishSession()
        assertEquals(engine.sessionCount, store.completedSessions())
        assertEquals(streakBefore + 1, store.streak().count)
        assertEquals(null, engine.resume())
    }

    @Test fun nothingDueAfterTheCourseBuildsNothing() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        finishCourse(engine)
        store.cards().keys.forEach { engine.rate(it, Rating.Good) }
        assertEquals(emptyList(), engine.buildSteps(seed = 1))
        assertEquals(null, store.inProgressSession())
    }

    @Test fun resetAllClearsEverything() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        repeat(3) { engine.finishSession() }
        engine.buildSteps(seed = 4)
        engine.resetAll()
        assertTrue(store.cards().isEmpty())
        assertEquals(0, store.completedSessions())
        assertEquals(Streak(), store.streak())
        assertEquals(null, store.inProgressSession())
    }

    @Test fun courseVersionChangeClearsOldInProgressSession() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        repeat(5) { engine.finishSession() }
        store.saveInProgressSession(InProgressSession(6, 1, 3, emptyList()))
        assertTrue(engine.syncCourseVersion("v1"), "no stored version: reset")
        assertEquals(0, store.completedSessions())
        assertEquals(null, store.inProgressSession())
        engine.finishSession()
        assertEquals(false, engine.syncCourseVersion("v1"), "same version: keep progress")
        assertEquals(1, store.completedSessions())
        assertTrue(engine.syncCourseVersion("v2"), "new version: reset")
        assertEquals(0, store.completedSessions())
        assertEquals("v2", store.courseVersion())
    }

    @Test fun lessonExamplesPutTheLineFirstWithoutDuplicates() = runSuspend {
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { 0L }
        val n = course.curriculum.first { "feat-locative" in it.lessons }.n
        repeat(n - 1) { engine.finishSession() }
        val step = engine.buildSteps(seed = 1).filterIsInstance<SessionStep.LessonStep>().first { it.lesson.feature == "locative" }
        val line = course.phraseById.getValue(course.curriculum[n - 1].phrase)
        val lineForms = line.tokens.filter { "locative" in it.features }.map { it.text }
        assertEquals(lineForms, step.examples.take(lineForms.size).map { it.surface })
        assertEquals(step.examples.size, step.examples.map { Norm.skey(it.surface) }.toSet().size)
    }
}
