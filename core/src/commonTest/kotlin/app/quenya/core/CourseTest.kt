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

    @Test fun everySessionPlansAndHasContent() {
        for (n in 1..planner.sessionCount) {
            val p = planner.plan(n, seed = 7)
            when (p.session.kind) {
                "lesson" -> assertTrue(p.lesson != null && p.lesson.summary.isNotBlank(), "session $n lesson")
                "vocab" -> assertTrue(p.introLemmas.size == p.session.lemmas.size, "session $n lemmas missing")
                "reading" -> assertTrue(p.readingPhrases.isNotEmpty(), "session $n phrases")
            }
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

    @Test fun featureSessionsAreFilled() {
        val short = mutableListOf<String>()
        for (n in 1..planner.sessionCount) {
            val p = planner.plan(n, 11)
            val s = p.session
            if ((s.kind == "lesson" || s.kind == "practice") && s.feature != null) {
                println("session $n ${s.kind} ${s.feature}: ${p.exercises.size} exercises")
                if (p.exercises.size < 3) short += "session $n ${s.feature} (${p.exercises.size})"
            }
        }
        println("SHORT feature sessions: $short")
        assertTrue(short.size <= 3, "too many thin feature sessions: $short")
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

    @Test fun readingsOnlyUseTaughtVocabulary() {
        for (s in course.curriculum.filter { it.kind == "reading" }) {
            val taught = planner.knownLemmas(s.n).toSet()
            for (pid in s.phrases) for (t in course.phraseById.getValue(pid).tokens)
                t.lemma?.let { assertTrue(it in taught, "session ${s.n}: $it read before taught") }
        }
    }

    @Test fun productionOnlyFromUnlockedPhrases() {
        assertTrue(planner.plan(1, 3).production.isEmpty())
        for (n in 1..planner.sessionCount) {
            val unlocked = planner.unlockedPhrases(n).toSet()
            for (e in planner.plan(n, 3).production) assertTrue(e.phraseId in unlocked, "session $n offers locked phrase ${e.phraseId}")
        }
    }

    @Test fun milestonesAreWhereThePipelineSaidAndMarkiryaFitsForty() {
        val at = course.curriculum.filter { it.milestone != null }.associate { it.milestone!! to it.n }
        println("milestones: $at")
        assertEquals(setOf("elen-sila", "aiya-earendil", "markirya-12", "markirya-full"), at.keys)
        assertTrue(at.getValue("markirya-12") <= 40, "markirya-12 lands at ${at["markirya-12"]}")
        assertTrue(at.getValue("elen-sila") < at.getValue("aiya-earendil") && at.getValue("aiya-earendil") < at.getValue("markirya-12"))
    }
}
