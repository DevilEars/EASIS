package app.quenya.core

/** What one study session contains, derived from the curriculum and progress. */
data class SessionPlan(
    val session: Session,
    val lesson: Lesson?,
    val lessonFeature: String?,
    val introLemmas: List<LexiconEntry>,
    val readingPhrases: List<Phrase>,
    val exercises: List<ChoiceExercise>,
    val production: List<TypedExercise>,
)

class Planner(private val course: CourseData) {
    val sessionCount: Int get() = course.curriculum.size

    /** Phrases whose reading slot has been completed (or is in the current session). */
    fun unlockedPhrases(upToSession: Int): List<String> =
        course.curriculum.filter { it.n <= upToSession }
            .flatMap { it.slots }.filter { it.kind == "reading" }.flatMap { it.phrases }

    fun knownLemmas(upToSession: Int): List<String> =
        course.curriculum.filter { it.n <= upToSession }
            .flatMap { it.slots }.filter { it.kind == "vocab" }.flatMap { it.lemmas }

    /** Plan for session [n] (1-based). [seed] makes exercises reproducible. */
    fun plan(n: Int, seed: Long): SessionPlan {
        val s = course.curriculum[n - 1]
        val f = ExerciseFactory(course, seed + n)
        val unlocked = unlockedPhrases(n)
        val exercises = buildList {
            s.slots.forEach { slot ->
                when (slot.kind) {
                    "lesson" -> slot.feature?.let { addAll(f.formChoice(it, 5)) }
                    "practice" -> slot.feature?.let { addAll(f.formChoice(it, 8)) }
                    "vocab" -> addAll(f.meaning(slot.lemmas, slot.lemmas.size))
                    "reading" -> addAll(f.cloze(slot.phrases, 5))
                }
            }
        }
        val production = f.production(unlocked, if (unlocked.isEmpty()) 0 else 2)
        val lessonSlot = s.slots.firstOrNull { it.kind == "lesson" }
        return SessionPlan(
            session = s,
            lesson = lessonSlot?.lesson?.let { course.lessonById[it] },
            lessonFeature = lessonSlot?.feature,
            introLemmas = s.slots.filter { it.kind == "vocab" }.flatMap { it.lemmas }.mapNotNull { course.lex[it] },
            readingPhrases = s.slots.filter { it.kind == "reading" }.flatMap { it.phrases }.mapNotNull { course.phraseById[it] },
            exercises = exercises,
            production = production,
        )
    }
}
