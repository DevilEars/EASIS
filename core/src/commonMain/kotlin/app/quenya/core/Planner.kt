package app.quenya.core

/** What one study session contains: its line, and what the line needs. */
data class SessionPlan(
    val session: Session,
    val line: Phrase,
    val lessons: List<Lesson>,
    val introLemmas: List<LexiconEntry>,
    val exercises: List<ChoiceExercise>,
    val production: List<TypedExercise>,
)

class Planner(private val course: CourseData) {
    private companion object { const val MIN_QUESTIONS = 6 }

    val sessionCount: Int get() = course.curriculum.size

    /** Lines of sessions 1..[upToSession], in course order. */
    fun unlockedPhrases(upToSession: Int): List<String> = course.curriculum.take(upToSession).map { it.phrase }

    fun knownLemmas(upToSession: Int): Set<String> = course.curriculum.take(upToSession).flatMap { it.lemmas }.toSet()

    /** Plan for session [n] (1-based). [seed] makes exercises reproducible. */
    fun plan(n: Int, seed: Long): SessionPlan {
        val s = course.curriculum[n - 1]
        val f = ExerciseFactory(course, seed + n)
        val known = knownLemmas(n)
        val unlocked = unlockedPhrases(n)
        val earlier = unlocked.dropLast(1)
        val lessons = s.lessons.mapNotNull { course.lessonById[it] }
        val exercises = buildList {
            addAll(f.meaning(s.lemmas, s.lemmas.size))
            addAll(f.cloze(listOf(s.phrase), 1, unlocked))
            addAll(f.cloze(earlier, 1, unlocked))
            // Two questions per new grammar topic; one each when a line brings three or more,
            // so a session stays at about 6–8 questions.
            val features = lessons.mapNotNull { it.feature }
            val perFeature = if (features.size >= 3) 1 else 2
            features.forEach { addAll(f.formChoice(it, perFeature, known)) }
            // Short lines bring few new words: top up with words and lines already met.
            addAll(f.meaning((known - s.lemmas.toSet()).toList(), (MIN_QUESTIONS - size).coerceAtLeast(0)))
            addAll(f.cloze(earlier, (MIN_QUESTIONS - size).coerceAtLeast(0), unlocked))
        }.distinctBy { it.id }
        return SessionPlan(
            session = s,
            line = course.phraseById.getValue(s.phrase),
            lessons = lessons,
            introLemmas = s.lemmas.mapNotNull { course.lex[it] },
            exercises = exercises,
            production = f.production(s.phrase, earlier),
        )
    }
}
