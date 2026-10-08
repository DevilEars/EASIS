package app.quenya.core

/** All language data, indexed. Built from the JSON files by the platform loader. */
class CourseData(
    val lexicon: List<LexiconEntry>,
    val forms: List<FormEntry>,
    val phrases: List<Phrase>,
    val lessons: List<Lesson>,
    val curriculum: List<Session>,
    val audio: AudioManifest = AudioManifest(),
) {
    val lex: Map<String, LexiconEntry> = lexicon.associateBy { it.id }
    val phraseById: Map<String, Phrase> = phrases.associateBy { it.id }
    val lessonById: Map<String, Lesson> = lessons.associateBy { it.id }
    val sessionByPhrase: Map<String, Session> = curriculum.associateBy { it.phrase }
    val formsByFeature: Map<String, List<FormEntry>> =
        forms.flatMap { f -> f.features.map { it to f } }.groupBy({ it.first }, { it.second })
    val formsByKey: Map<String, List<FormEntry>> = forms.groupBy { Norm.skey(it.surface) }
    val lemmasByKey: Map<String, List<LexiconEntry>> = lexicon.groupBy { Norm.skey(it.lemma) }

    /** First phrase that contains the lemma, for flashcard example sentences. */
    fun exampleFor(lemmaId: String): Pair<Phrase, Token>? {
        for (p in phrases) for (t in p.tokens) if (t.lemma == lemmaId) return p to t
        return null
    }

    /** Audio clip filename (under files/audio/) for a word's exact surface text, if generated. */
    fun audioForWord(text: String): String? = audio.words[Norm.skey(text)]

    /** Audio clip filename for a whole Reader phrase/line, if generated. */
    fun audioForLine(phraseId: String): String? = audio.lines[phraseId]
}
