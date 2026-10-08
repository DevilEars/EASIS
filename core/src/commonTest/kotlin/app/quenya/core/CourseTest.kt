package app.quenya.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Runs against REAL Eldamo-derived data (see Fixtures.kt). */
class CourseTest {
    private val course = Fixtures.course()
    private val planner = Planner(course)
    private val checker = Checker(course)

    @Test fun textNormalisation() {
        assertEquals("elen", Norm.skey("Elen"))
        assertEquals("cenuva", Norm.skey("kenuva"))                 // k == c
        assertEquals("lúmenn", Norm.skey("lúmenn’"))                 // strict key keeps accents
        assertEquals(Norm.skey("lúmë"), Norm.skey("lu\u0301me\u0308"))     // decomposed input composes
        assertTrue(Norm.skey("lúmë") != Norm.skey("lume"))           // strict keeps accents
        assertEquals(Norm.key("lúmë"), Norm.key("lume"))             // loose folds them
        assertEquals(1, Norm.editDistance("elen", "eleni"))
    }

    @Test fun checkerAcceptsEveryCanonicalPhrase() {
        for (p in course.phrases) {
            val typed = p.tokens.joinToString(" ") { it.text }
            assertEquals(Verdict.EXACT, checker.check(typed, p).verdict, p.id)
            assertEquals(Verdict.EXACT, checker.check(typed.uppercase(), p).verdict, "${p.id} uppercase")
        }
    }

    @Test fun checkerHandlesAccentsAndVariants() {
        val sila = course.phraseById.getValue("elen-sila")
        assertEquals(Verdict.EXACT, checker.check("elen si\u0301la lu\u0301menn' omentielvo", sila).verdict)
        val noAccents = checker.check("elen sila lumenn omentielvo", sila)
        assertEquals(Verdict.ACCENTS_DIFFER, noAccents.verdict)
        assertTrue(noAccents.correct && noAccents.notes.isNotEmpty())
        val variant = sila.variants.first { Norm.words(it.text).map(Norm::skey) != sila.tokens.map { t -> Norm.skey(t.text) } }
        assertEquals(Verdict.ATTESTED_VARIANT, checker.check(variant.text, sila).verdict)
    }

    @Test fun checkerExplainsWrongForm() {
        val aiya = course.phraseById.getValue("aiya-earendil")
        val r = checker.check("aiya Eärendil eleni ancalima", aiya)
        assertEquals(Verdict.INCORRECT, r.verdict)
        assertFalse(r.correct)
        val note = r.notes.first { it.typed == "eleni" }.message
        assertTrue("plural" in note && "wrong form" in note, note)
        assertTrue(checker.check("aiya", aiya).notes.any { "words" in it.message })
        assertTrue(checker.check("aiya Eärendil xyzzy ancalima", aiya).notes.any { "isn't in the lexicon" in it.message })
    }

    @Test fun sessionsReadTheTextsInOrder() {
        val expected = listOf("elen-sila", "aiya-earendil") + (1..37).map { "markirya-" + it.toString().padStart(2, '0') }
        assertEquals(expected, course.curriculum.map { it.phrase })
        assertEquals("Warm-up 1 of 2", course.curriculum[0].label)
        assertEquals("Line 4 of 37", course.curriculum[5].label)
        assertEquals(listOf(null, null, 1, 1, 1, 1, 1, 2), course.curriculum.take(8).map { it.verse })
        assertEquals(5, course.curriculum.last().verse)
    }

    @Test fun planMatchesTheSession() {
        for (s in course.curriculum) {
            val p = planner.plan(s.n, seed = 7)
            assertEquals(s.phrase, p.line.id, "session ${s.n}")
            assertEquals(s.lemmas, p.introLemmas.map { it.id }, "session ${s.n} words")
            assertEquals(s.lessons, p.lessons.map { it.id }, "session ${s.n} lessons")
            assertTrue(p.lessons.all { it.summary.isNotBlank() }, "session ${s.n} empty lesson")
            assertTrue(p.exercises.size >= 2, "session ${s.n}: ${p.exercises.size} questions")
            assertEquals(s.phrase, p.production.first().phraseId, "session ${s.n} writes its own line first")
        }
    }

    @Test fun everyWordAndFeatureIsTaughtByItsLine() {
        for (s in course.curriculum) {
            val words = planner.knownLemmas(s.n)
            val feats = course.curriculum.take(s.n).flatMap { it.lessons }.mapNotNull { course.lessonById[it]?.feature }.toSet()
            for (t in course.phraseById.getValue(s.phrase).tokens) {
                t.lemma?.let { assertTrue(it in words, "session ${s.n}: $it read before taught") }
                t.features.forEach { assertTrue(it in feats, "session ${s.n}: $it used before taught") }
            }
        }
    }

    @Test fun questionsOnlyAskAboutWordsAlreadyMet() {
        for (seed in 1L..3L) for (s in course.curriculum) {
            val known = planner.knownLemmas(s.n)
            val unlocked = planner.unlockedPhrases(s.n).toSet()
            for (e in planner.plan(s.n, seed).exercises) {
                val parts = e.id.split(":")
                when (parts[0]) {
                    "meaning", "form" -> assertTrue(parts[1] in known, "session ${s.n} ${e.id}: unmet word")
                    "cloze" -> assertTrue(parts[1] in unlocked, "session ${s.n} ${e.id}: locked line")
                }
            }
            for (e in planner.plan(s.n, seed).production) assertTrue(e.phraseId in unlocked, "session ${s.n} ${e.phraseId}")
        }
    }

    @Test fun exerciseInvariants() {
        var total = 0
        for (seed in 1L..3L) for (n in 1..planner.sessionCount) {
            for (e in planner.plan(n, seed).exercises) {
                total++
                val where = "session $n ${e.id}"
                assertTrue(e.evidence.isNotBlank(), "$where: no evidence")
                assertTrue(e.answerIndex in e.options.indices, "$where: bad answerIndex")
                assertEquals(e.options.size, e.options.map(Norm::skey).toSet().size, "$where: duplicate options $e")
                assertTrue(e.options.size in 3..4, "$where: ${e.options.size} options")
                assertEquals(1, e.options.count { it == e.answer }, "$where: answer not unique")
                assertTrue(e.explanation.isNotBlank(), "$where: no explanation")
            }
        }
        assertTrue(total > 300, "expected many exercises, got $total")
    }

    @Test fun formAnswersComeFromAttestedForms() {
        for (n in 1..planner.sessionCount) for (e in planner.plan(n, 5).exercises) {
            if (!e.id.startsWith("form:")) continue
            val (_, lemma, feats) = e.id.split(":")
            val f = feats.split("+")
            assertTrue(course.forms.any { it.lemma == lemma && it.features == f && it.surface == e.answer && it.source == e.evidence },
                "${e.id}: answer ${e.answer} is not an attested form with source ${e.evidence}")
        }
    }

    @Test fun lineWithoutAnalysisStillHasASession() {
        val s = course.curriculum.first { it.phrase == "markirya-32" }
        val p = planner.plan(s.n, 1)
        assertTrue(p.introLemmas.isEmpty() && p.lessons.isEmpty())
        assertTrue(p.exercises.isNotEmpty(), "line 32 needs at least one question")
    }

    @Test fun sessionsAskAboutSixToEightQuestions() {
        for (seed in 1L..3L) for (n in 1..planner.sessionCount) {
            val count = planner.plan(n, seed).exercises.size
            assertTrue(count in 6..9, "session $n asks $count questions")
        }
    }

    @Test fun clozeBlanksATaughtWordWhenTheLineHasOne() {
        for (seed in 1L..3L) for (s in course.curriculum) {
            val known = planner.knownLemmas(s.n)
            for (e in planner.plan(s.n, seed).exercises) {
                if (!e.id.startsWith("cloze:")) continue
                val (_, pid, i) = e.id.split(":")
                val tokens = course.phraseById.getValue(pid).tokens
                if (tokens.none { it.lemma in known }) continue          // line 32: nothing analysed
                assertTrue(tokens[i.toInt()].lemma in known, "session ${s.n} ${e.id}: blanks an untaught word")
            }
        }
    }
}
