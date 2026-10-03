package app.quenya.core

/** All language data, indexed. Built from the JSON files by the platform loader. */
class CourseData(
    val lexicon: List<LexiconEntry>,
    val forms: List<FormEntry>,
    val phrases: List<Phrase>,
    val lessons: List<Lesson>,
    val curriculum: List<Session>,
) {
    val lex: Map<String, LexiconEntry> = lexicon.associateBy { it.id }
    val phraseById: Map<String, Phrase> = phrases.associateBy { it.id }
    val lessonById: Map<String, Lesson> = lessons.associateBy { it.id }
    val formsByFeature: Map<String, List<FormEntry>> =
        forms.flatMap { f -> f.features.map { it to f } }.groupBy({ it.first }, { it.second })
    val formsByKey: Map<String, List<FormEntry>> = forms.groupBy { Norm.skey(it.surface) }
    val lemmasByKey: Map<String, List<LexiconEntry>> = lexicon.groupBy { Norm.skey(it.lemma) }

    /** First phrase that contains the lemma, for flashcard example sentences. */
    fun exampleFor(lemmaId: String): Pair<Phrase, Token>? {
        for (p in phrases) for (t in p.tokens) if (t.lemma == lemmaId) return p to t
        return null
    }
}
