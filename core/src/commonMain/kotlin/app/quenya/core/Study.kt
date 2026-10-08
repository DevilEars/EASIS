package app.quenya.core

/** A session the user started but hasn't finished; durable so it survives the app being killed. */
data class InProgressSession(val sessionN: Int, val seed: Long, val stepIndex: Int, val reviewLemmaIds: List<String>)

/** Persistence boundary. The app implements it with SQLDelight; tests use the in-memory one. */
interface ReviewStore {
    suspend fun cards(): Map<String, FsrsCard>
    suspend fun saveCard(id: String, card: FsrsCard)
    suspend fun completedSessions(): Int
    suspend fun setCompletedSessions(n: Int)
    suspend fun streak(): Streak
    suspend fun setStreak(s: Streak)
    suspend fun inProgressSession(): InProgressSession?
    suspend fun saveInProgressSession(s: InProgressSession)
    suspend fun clearInProgressSession()
    suspend fun courseVersion(): String?
    suspend fun setCourseVersion(v: String)
    /** Forget all progress: cards, completed sessions, streak, in-progress session. Keeps the course version. */
    suspend fun clearAll()
}

data class Streak(val count: Int = 0, val lastDay: Long = -1L)

class InMemoryReviewStore : ReviewStore {
    private val map = mutableMapOf<String, FsrsCard>()
    private var done = 0
    private var streak = Streak()
    private var inProgress: InProgressSession? = null
    override suspend fun cards(): Map<String, FsrsCard> = map.toMap()
    override suspend fun saveCard(id: String, card: FsrsCard) { map[id] = card }
    override suspend fun completedSessions() = done
    override suspend fun setCompletedSessions(n: Int) { done = n }
    override suspend fun streak() = streak
    override suspend fun setStreak(s: Streak) { streak = s }
    override suspend fun inProgressSession() = inProgress
    override suspend fun saveInProgressSession(s: InProgressSession) { inProgress = s }
    override suspend fun clearInProgressSession() { inProgress = null }
    private var version: String? = null
    override suspend fun courseVersion() = version
    override suspend fun setCourseVersion(v: String) { version = v }
    override suspend fun clearAll() { map.clear(); done = 0; streak = Streak(); inProgress = null }
}

/** One screen in a study session. */
sealed interface SessionStep {
    data class Review(val lemma: LexiconEntry, val example: Pair<Phrase, Token>?) : SessionStep
    data class LineIntro(val session: Session, val phrase: Phrase) : SessionStep
    data class Intro(val lemmas: List<LexiconEntry>) : SessionStep
    data class LessonStep(val lesson: Lesson, val examples: List<FormEntry>) : SessionStep
    data class Reading(val phrases: List<Phrase>) : SessionStep
    data class Choice(val exercise: ChoiceExercise) : SessionStep
    data class Typed(val exercise: TypedExercise, val phrase: Phrase) : SessionStep
}

/** Orchestrates a session: what to show, and what each answer does to saved state. */
class StudyEngine(
    private val course: CourseData,
    private val store: ReviewStore,
    private val scheduler: FsrsScheduler = FsrsScheduler(),
    private val maxReviews: Int = 12,
    private val utcOffsetMs: () -> Long = { 0L },
    private val nowMs: () -> Long,
) {
    private val planner = Planner(course)
    val sessionCount: Int get() = planner.sessionCount

    suspend fun dueCount(): Int = store.cards().count { it.value.dueMs <= nowMs() }

    /** Reader list: lines of completed sessions, and always the first session's line. */
    suspend fun unlockedPhrases(): List<Phrase> {
        val ids = planner.unlockedPhrases(store.completedSessions())
        val first = course.curriculum.firstOrNull()?.phrase
        val shown = if (first == null || first in ids) ids else listOf(first) + ids
        return shown.mapNotNull { course.phraseById[it] }
    }

    /** Pure function of (n, seed, course, frozen review ids) — the only non-deterministic input to
     *  a session is which cards are due, so freezing that list lets a resume reproduce byte-for-byte
     *  the same steps even if the live due-card set has since changed. */
    private fun assembleSteps(n: Int, seed: Long, reviewLemmaIds: List<String>): List<SessionStep> {
        val steps = mutableListOf<SessionStep>()
        reviewLemmaIds.forEach { id ->
            course.lex[id]?.let { steps += SessionStep.Review(it, course.exampleFor(id)) }
        }
        if (n > sessionCount) return steps                     // after the course: reviews only
        val plan = planner.plan(n, seed)
        steps += SessionStep.LineIntro(plan.session, plan.line)
        if (plan.introLemmas.isNotEmpty()) steps += SessionStep.Intro(plan.introLemmas)
        val known = planner.knownLemmas(n)
        plan.lessons.forEach { steps += SessionStep.LessonStep(it, lessonExamples(it, plan.line, known)) }
        steps += SessionStep.Reading(listOf(plan.line))
        plan.exercises.forEach { steps += SessionStep.Choice(it) }
        plan.production.forEach { steps += SessionStep.Typed(it, course.phraseById.getValue(it.phraseId)) }
        return steps
    }

    /** The line's own tokens with the feature first, then attested forms of known words, then others. */
    private fun lessonExamples(lesson: Lesson, line: Phrase, known: Set<String>): List<FormEntry> {
        val feature = lesson.feature ?: return emptyList()
        val source = line.variants.firstOrNull()?.source ?: line.id
        val fromLine = line.tokens.filter { feature in it.features && it.lemma != null }
            .map { FormEntry(it.lemma!!, it.text, it.features, source, clean = true) }
        val attested = course.formsByFeature[feature].orEmpty().filter { it.clean }
            .sortedWith(compareByDescending<FormEntry> { it.lemma in known }.thenByDescending { course.lex[it.lemma]?.attestations ?: 0 })
        return (fromLine + attested).distinctBy { Norm.skey(it.surface) }.take(6)
    }

    /** Steps for the next session: reviews, then the next line. After the last line, reviews only;
     *  empty when nothing is due. Persists an in-progress marker so the session is resumable. */
    suspend fun buildSteps(seed: Long): List<SessionStep> {
        val n = store.completedSessions() + 1
        val now = nowMs()
        val reviewIds = store.cards().entries.filter { it.value.dueMs <= now }.sortedBy { it.value.dueMs }
            .take(maxReviews).map { it.key }
        if (n > sessionCount && reviewIds.isEmpty()) return emptyList()
        val steps = assembleSteps(n, seed, reviewIds)
        store.saveInProgressSession(InProgressSession(n, seed, 0, reviewIds))
        return steps
    }

    /** Reconstructs the exact step list of a session the user left mid-way, plus where they were. */
    suspend fun resume(): Pair<List<SessionStep>, Int>? {
        val saved = store.inProgressSession() ?: return null
        if (saved.sessionN != store.completedSessions() + 1) {
            store.clearInProgressSession()
            return null
        }
        return assembleSteps(saved.sessionN, saved.seed, saved.reviewLemmaIds) to saved.stepIndex
    }

    /** Record which step the user has reached, so resume lands in the right place. */
    suspend fun advance(toIndex: Int) {
        val saved = store.inProgressSession() ?: return
        store.saveInProgressSession(saved.copy(stepIndex = toIndex))
    }

    suspend fun rate(lemmaId: String, rating: Rating) {
        val card = store.cards()[lemmaId] ?: return
        store.saveCard(lemmaId, scheduler.review(card, rating, nowMs()))
    }

    /** Mark the session done: create cards for newly taught words and update the streak.
     *  A reviews-only session (after the last line) only updates the streak. */
    suspend fun finishSession() {
        val n = store.completedSessions() + 1
        if (n <= sessionCount) {
            val existing = store.cards()
            course.curriculum[n - 1].lemmas.filter { it !in existing }
                .forEach { store.saveCard(it, FsrsCard(dueMs = nowMs())) }
            store.setCompletedSessions(n)
        }
        store.clearInProgressSession()
        val today = (nowMs() + utcOffsetMs()) / FsrsScheduler.DAY_MS
        val s = store.streak()
        store.setStreak(when {
            s.lastDay == today -> s
            s.lastDay == today - 1 -> Streak(s.count + 1, today)
            else -> Streak(1, today)
        })
    }

    /** Retake: start the course over with no history. */
    suspend fun resetAll() = store.clearAll()

    /** Progress belongs to one curriculum. When the bundled course changes, start over. */
    suspend fun syncCourseVersion(version: String): Boolean {
        if (store.courseVersion() == version) return false
        store.clearAll()
        store.setCourseVersion(version)
        return true
    }
}
