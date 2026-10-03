package app.quenya.core

/** What one study session contains, derived from the curriculum and progress. */
data class SessionPlan(
    val session: Session,
    val lesson: Lesson?,
    val introLemmas: List<LexiconEntry>,
    val readingPhrases: List<Phrase>,
    val exercises: List<ChoiceExercise>,
    val production: List<TypedExercise>,
)

class Planner(private val course: CourseData) {
    val sessionCount: Int get() = course.curriculum.size

    /** Phrases whose reading session has been completed (or is the current one). */
    fun unlockedPhrases(upToSession: Int): List<String> =
        course.curriculum.filter { it.n <= upToSession && it.kind == "reading" }.flatMap { it.phrases }

    fun knownLemmas(upToSession: Int): List<String> =
        course.curriculum.filter { it.n <= upToSession && it.kind == "vocab" }.flatMap { it.lemmas }

    /** Plan for session [n] (1-based). [seed] makes exercises reproducible. */
    fun plan(n: Int, seed: Long): SessionPlan {
        val s = course.curriculum[n - 1]
        val f = ExerciseFactory(course, seed + n)
        val unlocked = unlockedPhrases(n)
        val exercises: List<ChoiceExercise> = when (s.kind) {
            "lesson", "practice" -> s.feature?.let { f.formChoice(it, if (s.kind == "lesson") 5 else 8) }.orEmpty()
            "vocab" -> f.meaning(s.lemmas, s.lemmas.size)
            "reading" -> f.cloze(s.phrases, 5)
            else -> emptyList()
        }
        val production = f.production(unlocked, if (unlocked.isEmpty()) 0 else 2)
        return SessionPlan(
            session = s,
            lesson = s.lesson?.let { course.lessonById[it] },
            introLemmas = s.lemmas.mapNotNull { course.lex[it] },
            readingPhrases = s.phrases.mapNotNull { course.phraseById[it] },
            exercises = exercises,
            production = production,
        )
    }
}
