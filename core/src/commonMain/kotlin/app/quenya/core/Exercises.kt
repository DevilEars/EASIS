package app.quenya.core

import kotlin.random.Random

/**
 * Every exercise carries its answer key AND the data reference it came from
 * (`evidence`). If no evidence exists, no exercise is generated.
 */
sealed interface Exercise {
    val id: String
    val prompt: String
    val evidence: String
}

data class ChoiceExercise(
    override val id: String,
    override val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
    override val evidence: String,
) : Exercise {
    val answer: String get() = options[answerIndex]
}

data class TypedExercise(
    override val id: String,
    override val prompt: String,
    val phraseId: String,
    override val evidence: String,
) : Exercise

class ExerciseFactory(private val course: CourseData, seed: Long) {
    private val rnd = Random(seed)

    private fun usable(lemma: String): LexiconEntry? =
        course.lex[lemma]?.takeIf { it.gloss.en.isNotBlank() && !it.deprecated }

    private fun shuffled(options: List<String>, correct: String): Pair<List<String>, Int> {
        val s = options.shuffled(rnd)
        return s to s.indexOf(correct)
    }

    /** "Which is the <features> of <lemma>?" answered from an attested form of a word in [known]. */
    fun formChoice(feature: String, n: Int, known: Set<String>): List<ChoiceExercise> {
        val pool = course.formsByFeature[feature].orEmpty()
            .filter { it.clean && it.surface.isNotBlank() && usable(it.lemma) != null }
        // Prefer well-attested lemmas, one question per lemma + feature set.
        val candidates = pool.filter { it.lemma in known }.groupBy { it.lemma to it.features }
            .values.map { it.first() }
            .sortedByDescending { course.lex[it.lemma]!!.attestations }
            .take(n * 4).shuffled(rnd)
        val out = mutableListOf<ChoiceExercise>()
        for (f in candidates) {
            if (out.size >= n) break
            val same = pool.filter { it.features == f.features && it.lemma != f.lemma }
                .map { it.surface }.distinctBy(Norm::skey).filter { Norm.skey(it) != Norm.skey(f.surface) }
            val otherForms = course.forms.filter { it.lemma == f.lemma && it.features != f.features && it.clean }
                .map { it.surface }.distinctBy(Norm::skey).filter { Norm.skey(it) != Norm.skey(f.surface) }
            val distractors = (same.shuffled(rnd).take(3) + otherForms.shuffled(rnd)).distinctBy(Norm::skey).take(3)
            if (distractors.size < 2) continue
            val (opts, idx) = shuffled(distractors + f.surface, f.surface)
            val e = course.lex.getValue(f.lemma)
            out += ChoiceExercise(
                id = "form:${f.lemma}:${f.features.joinToString("+")}",
                prompt = "Which is the ${Norm.featureLabel(f.features)} form of ${e.lemma} “${e.gloss.en}”?",
                options = opts, answerIndex = idx,
                explanation = "${f.surface} is the attested ${Norm.featureLabel(f.features)} of ${e.lemma}.",
                evidence = f.source,
            )
        }
        return out
    }

    /** Fill one word of a known phrase. Options are other words from [wordPool]'s phrases. */
    fun cloze(phraseIds: List<String>, n: Int, wordPool: List<String> = phraseIds): List<ChoiceExercise> {
        val phrases = phraseIds.mapNotNull { course.phraseById[it] }.filter { it.tokens.size >= 2 }
        val words = wordPool.mapNotNull { course.phraseById[it] }.flatMap { p -> p.tokens.map { it.text } }
            .distinctBy(Norm::skey)
        val out = mutableListOf<ChoiceExercise>()
        for (p in phrases.shuffled(rnd)) {
            if (out.size >= n) break
            val i = rnd.nextInt(p.tokens.size)
            val answer = p.tokens[i].text
            val d = words.filter { Norm.skey(it) != Norm.skey(answer) }.shuffled(rnd).take(3)
            if (d.size < 2) continue
            val shown = p.tokens.mapIndexed { j, t -> (if (j == i) "____" else t.text) + t.punct }.joinToString(" ")
            val (opts, idx) = shuffled(d + answer, answer)
            out += ChoiceExercise(
                id = "cloze:${p.id}:$i", prompt = "$shown\n“${p.gloss}”", options = opts, answerIndex = idx,
                explanation = "${p.text} = “${p.gloss}”",
                evidence = p.variants.firstOrNull()?.source ?: p.id,
            )
        }
        return out
    }

    /** "What does <lemma> mean?" from the lexicon gloss. */
    fun meaning(lemmaIds: List<String>, n: Int): List<ChoiceExercise> {
        val out = mutableListOf<ChoiceExercise>()
        for (id in lemmaIds.shuffled(rnd)) {
            if (out.size >= n) break
            val e = usable(id) ?: continue
            val pool = course.lexicon.filter {
                it.pos == e.pos && it.id != e.id && it.gloss.en.isNotBlank() && !it.deprecated &&
                    Norm.key(it.gloss.en) != Norm.key(e.gloss.en)
            }.map { it.gloss.en }.distinct()
            if (pool.size < 3) continue
            val d = pool.shuffled(rnd).take(3)
            val (opts, idx) = shuffled(d + e.gloss.en, e.gloss.en)
            out += ChoiceExercise(
                id = "meaning:${e.id}", prompt = "What does ${e.lemma} mean?", options = opts, answerIndex = idx,
                explanation = "${e.lemma} = “${e.gloss.en}”", evidence = "Eldamo lexicon: ${e.id}",
            )
        }
        return out
    }

    /** Guided production: this line from its English, then one earlier line as revision. */
    fun production(current: String, earlier: List<String>): List<TypedExercise> =
        (listOf(current) + earlier.shuffled(rnd).take(1))
            .mapNotNull { course.phraseById[it] }.filter { it.gloss.isNotBlank() }
            .map {
                TypedExercise("prod:${it.id}", "Write in Quenya:\n“${it.gloss}”", it.id,
                    it.variants.firstOrNull()?.source ?: it.id)
            }
}
