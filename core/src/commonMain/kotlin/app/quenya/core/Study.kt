package app.quenya.core

/** Persistence boundary. The app implements it with SQLDelight; tests use the in-memory one. */
interface ReviewStore {
    suspend fun cards(): Map<String, FsrsCard>
    suspend fun saveCard(id: String, card: FsrsCard)
    suspend fun completedSessions(): Int
    suspend fun setCompletedSessions(n: Int)
    suspend fun streak(): Streak
    suspend fun setStreak(s: Streak)
}

data class Streak(val count: Int = 0, val lastDay: Long = -1L)

class InMemoryReviewStore : ReviewStore {
    private val map = mutableMapOf<String, FsrsCard>()
    private var done = 0
    private var streak = Streak()
    override suspend fun cards(): Map<String, FsrsCard> = map.toMap()
    override suspend fun saveCard(id: String, card: FsrsCard) { map[id] = card }
    override suspend fun completedSessions() = done
    override suspend fun setCompletedSessions(n: Int) { done = n }
    override suspend fun streak() = streak
    override suspend fun setStreak(s: Streak) { streak = s }
}

/** One screen in a study session. */
sealed interface SessionStep {
    data class Review(val lemma: LexiconEntry, val example: Pair<Phrase, Token>?) : SessionStep
    data class LessonStep(val lesson: Lesson, val feature: String?, val examples: List<FormEntry>) : SessionStep
    data class Intro(val lemmas: List<LexiconEntry>) : SessionStep
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
    private val nowMs: () -> Long,
) {
    private val planner = Planner(course)
    val sessionCount: Int get() = planner.sessionCount

    suspend fun dueCount(): Int = store.cards().count { it.value.dueMs <= nowMs() }

    /** Steps for the next session: reviews, new material, exercises, production. */
    suspend fun buildSteps(seed: Long): List<SessionStep> {
        val n = store.completedSessions() + 1
        if (n > sessionCount) return emptyList()
        val plan = planner.plan(n, seed)
        val steps = mutableListOf<SessionStep>()
        val now = nowMs()
        store.cards().entries.filter { it.value.dueMs <= now }.sortedBy { it.value.dueMs }
            .take(maxReviews).forEach { (id, _) ->
                course.lex[id]?.let { steps += SessionStep.Review(it, course.exampleFor(id)) }
            }
        plan.lesson?.let { l ->
            val ex = plan.session.feature?.let { f ->
                course.formsByFeature[f].orEmpty().filter { it.clean }.sortedByDescending { course.lex[it.lemma]?.attestations ?: 0 }
                    .distinctBy { it.lemma }.take(6)
            }.orEmpty()
            steps += SessionStep.LessonStep(l, plan.session.feature, ex)
        }
        if (plan.introLemmas.isNotEmpty()) steps += SessionStep.Intro(plan.introLemmas)
        if (plan.readingPhrases.isNotEmpty()) steps += SessionStep.Reading(plan.readingPhrases)
        plan.exercises.forEach { steps += SessionStep.Choice(it) }
        plan.production.forEach { steps += SessionStep.Typed(it, course.phraseById.getValue(it.phraseId)) }
        return steps
    }

    suspend fun rate(lemmaId: String, rating: Rating) {
        val card = store.cards()[lemmaId] ?: return
        store.saveCard(lemmaId, scheduler.review(card, rating, nowMs()))
    }

    /** Mark the session done: create cards for newly taught words and update the streak. */
    suspend fun finishSession() {
        val n = store.completedSessions() + 1
        if (n > sessionCount) return
        val existing = store.cards()
        course.curriculum[n - 1].lemmas.filter { it !in existing }
            .forEach { store.saveCard(it, FsrsCard(dueMs = nowMs())) }
        store.setCompletedSessions(n)
        val today = nowMs() / FsrsScheduler.DAY_MS
        val s = store.streak()
        store.setStreak(when {
            s.lastDay == today -> s
            s.lastDay == today - 1 -> Streak(s.count + 1, today)
            else -> Streak(1, today)
        })
    }
}
