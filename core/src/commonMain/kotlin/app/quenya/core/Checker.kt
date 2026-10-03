package app.quenya.core

enum class Verdict { EXACT, ACCENTS_DIFFER, ATTESTED_VARIANT, INCORRECT }

data class TokenNote(val typed: String, val message: String)

data class CheckResult(
    val verdict: Verdict,
    val expected: String,
    val notes: List<TokenNote>,
) {
    /** Accent-only and attested-variant answers count as correct (with a note). */
    val correct: Boolean get() = verdict != Verdict.INCORRECT
}

/**
 * Checks a typed phrase against an answer key (canonical phrase + attested variants).
 * It never claims a sentence is "ungrammatical": it reports whether the answer
 * matches the key, and explains each word against the lexicon and attested forms.
 */
class Checker(private val course: CourseData) {

    fun check(typed: String, phrase: Phrase): CheckResult {
        val expectedTokens = phrase.tokens.map { it.text }
        val t = Norm.words(typed)
        val strict = t.map(Norm::skey)
        val canonical = expectedTokens.map(Norm::skey)
        val variants = phrase.variants.map { v -> Norm.words(v.text) }

        if (strict == canonical) return CheckResult(Verdict.EXACT, phrase.text, emptyList())
        if (variants.any { v -> v.map(Norm::skey) == strict })
            return CheckResult(Verdict.ATTESTED_VARIANT, phrase.text,
                listOf(TokenNote(typed.trim(), "This exact wording is attested, though Eldamo's headword is “${phrase.text}”.")))

        val loose = t.map(Norm::key)
        if (loose == expectedTokens.map(Norm::key) || variants.any { v -> v.map(Norm::key) == loose }) {
            val notes = t.mapIndexedNotNull { i, w ->
                val e = expectedTokens.getOrNull(i)
                if (e != null && Norm.skey(w) != Norm.skey(e)) TokenNote(w, "Accents: write “$e”.") else null
            }
            return CheckResult(Verdict.ACCENTS_DIFFER, phrase.text, notes)
        }
        return CheckResult(Verdict.INCORRECT, phrase.text, explain(t, phrase))
    }

    private fun explain(typed: List<String>, phrase: Phrase): List<TokenNote> {
        val notes = mutableListOf<TokenNote>()
        if (typed.isEmpty()) return listOf(TokenNote("", "Nothing typed."))
        if (typed.size < phrase.tokens.size) notes += TokenNote("", "The answer has ${phrase.tokens.size} words; you wrote ${typed.size}.")
        if (typed.size > phrase.tokens.size) notes += TokenNote("", "The answer has ${phrase.tokens.size} words; you wrote ${typed.size}.")
        typed.forEachIndexed { i, w ->
            val exp = phrase.tokens.getOrNull(i)
            val k = Norm.skey(w)
            if (exp != null && k == Norm.skey(exp.text)) return@forEachIndexed       // word is right
            val asForm = course.formsByKey[k].orEmpty()
            val asLemma = course.lemmasByKey[k].orEmpty()
            val msg = when {
                exp != null && asForm.any { it.lemma == exp.lemma } -> {
                    val f = asForm.first { it.lemma == exp.lemma }
                    "“$w” is the ${Norm.featureLabel(f.features)} of ${lemmaOf(f.lemma)}: right word, wrong form for this slot."
                }
                asForm.isNotEmpty() -> {
                    val f = asForm.first()
                    "“$w” is the ${Norm.featureLabel(f.features)} of ${lemmaOf(f.lemma)} (${glossOf(f.lemma)}), but it isn't what's asked here."
                }
                asLemma.isNotEmpty() -> "“$w” is a real word (${asLemma.first().gloss.en}), but not the one asked for here."
                exp != null && Norm.editDistance(Norm.key(w), Norm.key(exp.text)) <= 2 ->
                    "“$w” is close to the right word; check spelling and endings."
                else -> "“$w” isn't in the lexicon or among the attested forms."
            }
            notes += TokenNote(w, msg)
        }
        return notes
    }

    private fun lemmaOf(id: String) = course.lex[id]?.lemma ?: id
    private fun glossOf(id: String) = course.lex[id]?.gloss?.en.orEmpty()
}
