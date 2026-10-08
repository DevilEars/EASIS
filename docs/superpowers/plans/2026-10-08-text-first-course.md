# Text-first course Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the course as one session per line (Elen síla, Aiyá Eärendil, *Markirya* 1–37), with screens that always show the line being worked toward, a Retake button, a full-poem view at the end, accented-letter buttons for typing, and audio on review cards.

**Architecture:** `tools/build_data.py` generates a new `curriculum.json` (one session per line: phrase, label, verse, new lemmas, lessons) from a shrunken `tools/skeleton.json`, plus a `course_version` hash in `meta.json`. `:core` (`Planner`, `ExerciseFactory`, `StudyEngine`) builds each session around its line, adds reviews-only sessions after the end, `resetAll()`, and a course-version reset. `composeApp` gets new Home, session, Done, Reader, and full-poem screens.

**Tech Stack:** Python 3 (venv `../.venv-piper`), Kotlin Multiplatform, Compose Multiplatform, SQLDelight, kotlin.test, unittest.

**Spec:** `docs/specs/text-first-course.md`

## Global Constraints

- Never hand-edit `composeApp/src/commonMain/composeResources/files/*.json` or `core/src/commonTest/kotlin/app/quenya/core/Fixtures.kt`; change the generator and regenerate.
- Python runs in the shared venv, from the repo root: `source ../.venv-piper/bin/activate`.
- Always `./gradlew`, never bare `gradle`.
- **Commits:** never commit on your own judgment. At each "Commit checkpoint" stop, show the user what changed, and ask. Commit only after an explicit yes, with the user's own message, no `Co-Authored-By` or other AI trailer. Never push.
- No personal or machine-specific details in tracked files: no absolute paths, user or folder names, device models, serials, or IP addresses.
- Audio plays only when the learner taps a ▶ button.
- Accent row letters, exactly: `á é í ó ú ä ë ï ö ü`.
- Labels, exactly: `Warm-up 1 of 2`, `Line 4 of 37`; reviews cap 12.
- Retake dialog text, exactly: title `Start over?`, body `This clears all progress.`
- Between Task 2 and Task 7 the `composeApp` module does not compile (it still uses the old model). `:core:jvmTest` is green after every task from Task 2 on.

## Review Focus

1. Upgrading an install that has old progress and an in-progress session from the 43-session course: the first launch must clear both, never resume an old session into the new course. (Task 3 test `courseVersionChangeClearsOldInProgressSession`.)
2. Tapping Retake while a session is in progress: Home must show session 1 with no Resume button. (Task 3 test `resetAllClearsEverything` covers the in-progress marker.)
3. *Markirya* line 32 has no analysed words: its session must still build, with no New words step, no grammar, and at least one question. (Task 2 test `lineWithoutAnalysisStillHasASession`.)
4. A grammar example that is both a token of the line and an attested form must appear once, line token first. (Task 3 test `lessonExamplesPutTheLineFirstWithoutDuplicates`.)
5. Accent button with the cursor mid-word or with a selection: the letter replaces the selection and the cursor lands after it. No UI test harness exists, so Task 9 Step 7.3 checks it on the emulator.

---

## File map

| File | Change |
|---|---|
| `tools/skeleton.json` | Rewrite: texts, goal, verses, foundations with `at`, features without ranks. |
| `tools/build_data.py` | New `build_curriculum`, `check_curriculum`, `course_version`, lessons carry `feature`; report. |
| `tools/test_build_data.py` | New: unittest for curriculum and checks. |
| `tools/make_kotlin_fixtures.py` | New Session/Lesson shapes. |
| `tools/generate_audio.py` | `inventory` adds taught headwords. |
| `tools/test_generate_audio.py` | New: unittest for `inventory`. |
| `core/.../Models.kt` | `Session` new shape, `Slot` removed, `Lesson.feature`. |
| `core/.../CourseData.kt` | `sessionByPhrase`. |
| `core/.../Exercises.kt` | `formChoice` known-lemma filter, `cloze` word pool and 2-token lines, `production(current, earlier)`. |
| `core/.../Planner.kt` | Plan built around the line. |
| `core/.../Study.kt` | `SessionStep.LineIntro`, new `LessonStep`, step order, reviews-only, `resetAll`, `syncCourseVersion`, store additions. |
| `core/.../io/DataLoader.kt` | `courseVersion(meta)`. |
| `core/src/commonTest/.../CourseTest.kt`, `StudyTest.kt` | Rewritten/extended. |
| `composeApp/src/commonMain/sqldelight/app/quenya/db/Review.sq` | `deleteAllCards`. |
| `composeApp/.../ui/SqlReviewStore.kt` | `courseVersion`, `setCourseVersion`, `clearAll`. |
| `composeApp/.../ui/AppModel.kt` | Lines read, last finished, retake, Markirya screen. |
| `composeApp/.../ui/LineViews.kt` | New: `PlayButton`, `PhraseView`, `TokenDetail` (moved from App.kt), `versedLines`. |
| `composeApp/.../ui/AccentRow.kt` | New: accent buttons and `insertAtCursor`. |
| `composeApp/.../ui/App.kt` | Startup version sync, Home, session views, Done, Reader, Markirya screen. |
| `SPEC.md`, `README.md`, `BACKLOG.md`, `docs/specs/neural-reader-audio.md` | Docs. |

`core/...` = `core/src/commonMain/kotlin/app/quenya/core`; `composeApp/.../ui` = `composeApp/src/commonMain/kotlin/app/quenya/ui`.

---

### Task 1: Generate the one-line-per-session curriculum

**Files:**
- Modify: `tools/skeleton.json`
- Modify: `tools/build_data.py` (lessons ~243-266, curriculum ~268-330, `main` ~332-356, `check` ~358-390, `report` ~392-404)
- Create: `tools/test_build_data.py`
- Regenerate: `composeApp/src/commonMain/composeResources/files/{lexicon,forms,phrases,lessons,curriculum,meta}.json`

**Interfaces:**
- Produces: `build_curriculum(phrases, skel=SKEL) -> list[dict]`; each dict `{"n", "phrase", "label", "verse"?, "lemmas", "lessons"}` (`verse` only for goal lines).
- Produces: `check_curriculum(sessions, phrases, lessons, skel=SKEL) -> list[str]`; `lessons` is `{id: {"summary", "feature", ...}}`.
- Produces: `course_version(sessions) -> str` (12 hex chars).
- Produces: each lesson in `lessons.json` has `"feature"` (feature name, or `null` for foundations).
- Produces: `meta.json` key `course_version`.

- [ ] **Step 1: Rewrite `tools/skeleton.json`**

```json
{
  "_comment": "Table of contents only. No lesson content is authored here: lesson text comes from Eldamo grammar entries. texts: phrase textIds in course order (one session per line). goal: the text the course works toward; the others are warm-ups. verses: line ranges of the goal text. foundations: lessons attached to the session of phrase `at`. features: teaching order when one session brings several.",
  "texts": ["elen-sila", "aiya-earendil", "markirya"],
  "goal": "markirya",
  "verses": [[1, 5], [6, 13], [14, 22], [23, 30], [31, 37]],
  "foundations": [
    {"id": "pronunciation", "entry": "pronunciation and transcription", "at": "elen-sila"},
    {"id": "word-order",    "entry": "word order",                      "at": "elen-sila"},
    {"id": "noun-classes",  "entry": "noun classes",                    "at": "aiya-earendil"},
    {"id": "article",       "entry": "definite article",                "at": "markirya-03"}
  ],
  "features": [
    {"feature": "plural",                "entry": "plural nouns",      "label": "Plural"},
    {"feature": "genitive",              "entry": "genitive",          "label": "Genitive"},
    {"feature": "allative",              "entry": "allative",          "label": "Allative"},
    {"feature": "ablative",              "entry": "ablative",          "label": "Ablative"},
    {"feature": "locative",              "entry": "locative",          "label": "Locative"},
    {"feature": "instrumental",          "entry": "instrumental",      "label": "Instrumental"},
    {"feature": "3rd-sg-poss",           "entry": "possessive",        "label": "Third person singular possessive"},
    {"feature": "1st-pl-inclusive-poss", "entry": "possessive",        "label": "First person plural inclusive possessive"},
    {"feature": "present",               "entry": "present",           "label": "Present"},
    {"feature": "active-participle",     "entry": "active participle", "label": "Active participle"},
    {"feature": "future",                "entry": "future",            "label": "Future"},
    {"feature": "infinitive",            "entry": "infinitive",        "label": "Infinitive"},
    {"feature": "elided",                "entry": "elision",           "label": "Elided"},
    {"feature": "intensive",             "entry": null,                "label": "Intensive"}
  ]
}
```

- [ ] **Step 2: Write the failing tests** — create `tools/test_build_data.py`:

```python
"""Curriculum generation and its checks, on a small synthetic course. No Eldamo XML needed."""
import copy
import unittest

from build_data import build_curriculum, check_curriculum, course_version


def tok(text, lemma, feats=()):
    return {"text": text, "punct": "", "lemma": lemma, "features": list(feats),
            "gloss": "", "resolution": "eldamo-element"}


PHRASES = [
    {"id": "w1", "textId": "w1", "line": 1, "tokens": [tok("a", "A", ["plural"]), tok("b", "B")]},
    {"id": "g-02", "textId": "g", "line": 2, "tokens": [tok("c", "C", ["plural"]), tok("x", None)]},
    {"id": "g-01", "textId": "g", "line": 1, "tokens": [tok("a", "A"), tok("c", "C", ["genitive"])]},
]
SKEL = {
    "texts": ["w1", "g"], "goal": "g", "verses": [[1, 1], [2, 2]],
    "foundations": [{"id": "pron", "entry": "p", "at": "w1"}],
    "features": [{"feature": "genitive", "entry": "genitive", "label": "Genitive"},
                 {"feature": "plural", "entry": "plural nouns", "label": "Plural"}],
}
LESSONS = {
    "pron": {"summary": "Sounds.", "feature": None},
    "feat-plural": {"summary": "Plurals.", "feature": "plural"},
    "feat-genitive": {"summary": "Genitive.", "feature": "genitive"},
}


class BuildCurriculumTests(unittest.TestCase):
    def test_one_session_per_line_in_text_order(self):
        self.assertEqual(build_curriculum(PHRASES, SKEL), [
            {"n": 1, "phrase": "w1", "label": "Warm-up 1 of 1", "lemmas": ["A", "B"], "lessons": ["pron", "feat-plural"]},
            {"n": 2, "phrase": "g-01", "label": "Line 1 of 2", "verse": 1, "lemmas": ["C"], "lessons": ["feat-genitive"]},
            {"n": 3, "phrase": "g-02", "label": "Line 2 of 2", "verse": 2, "lemmas": [], "lessons": []},
        ])

    def test_generated_course_passes_its_checks(self):
        self.assertEqual(check_curriculum(build_curriculum(PHRASES, SKEL), PHRASES, LESSONS, SKEL), [])

    def test_word_read_before_it_is_taught_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[1]["lemmas"].remove("C"); s[2]["lemmas"].append("C")
        self.assertIn("session 2: C is read before it is taught", check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_feature_used_before_it_is_taught_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[1]["lessons"] = []; s[2]["lessons"] = ["feat-genitive"]
        self.assertIn("session 2: feature genitive is used before it is taught",
                      check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_word_taught_twice_fails(self):
        s = build_curriculum(PHRASES, SKEL)
        s[2]["lemmas"].append("A")
        self.assertIn("lemma taught twice: A", check_curriculum(s, PHRASES, LESSONS, SKEL))

    def test_unknown_lesson_and_phrase_fail(self):
        skel = copy.deepcopy(SKEL)
        skel["foundations"][0]["at"] = "nope"
        s = build_curriculum(PHRASES, SKEL)
        s[0]["lessons"].append("feat-missing")
        problems = check_curriculum(s, PHRASES, LESSONS, skel)
        self.assertIn("skeleton: foundation pron attaches to unknown phrase nope", problems)
        self.assertIn("session 1: unknown lesson feat-missing", problems)

    def test_unlisted_feature_and_empty_text_fail(self):
        skel = copy.deepcopy(SKEL)
        skel["features"] = skel["features"][:1]
        skel["texts"] = ["w1", "g", "ghost"]
        problems = check_curriculum(build_curriculum(PHRASES, SKEL), PHRASES, LESSONS, skel)
        self.assertIn("skeleton: feature plural is used but not listed", problems)
        self.assertIn("skeleton: text ghost has no phrases", problems)

    def test_course_version_is_stable_and_changes_with_content(self):
        s = build_curriculum(PHRASES, SKEL)
        self.assertEqual(course_version(s), course_version(copy.deepcopy(s)))
        self.assertEqual(12, len(course_version(s)))
        s[2]["lemmas"].append("Z")
        self.assertNotEqual(course_version(build_curriculum(PHRASES, SKEL)), course_version(s))


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `source ../.venv-piper/bin/activate && python -m unittest discover -s tools -p 'test_build_data.py' -v`
Expected: FAIL — `ImportError: cannot import name 'check_curriculum'`.

- [ ] **Step 4: Implement in `tools/build_data.py`**

Add `import hashlib` to the import line. In `build_lessons`, give `from_entry` a `feature` argument and pass it through, and add `"feature"` to the generated-lesson dict:

```python
    def from_entry(lid, title, entry, feature=None):
        paras = html_to_text(gram[entry].find("notes").text)
        lessons[lid] = {"id": lid, "title": title, "entry": entry, "generated": False, "feature": feature,
                        "summary": summarize(paras), "body": paras,
                        "source": f"Eldamo grammar entry “{entry}” (CC BY 4.0, Paul Strack)"}
    for f in SKEL["foundations"]:
        from_entry(f["id"], f["entry"].capitalize(), f["entry"])
    for f in [feature_info(x, set(gram)) for x in used_features(phrases)]:
        lid = "feat-" + f["feature"]
        label = f.get("label") or f["feature"].replace("-", " ").capitalize()
        if f["entry"]:
            from_entry(lid, label, f["entry"], f["feature"])
        else:  # no Eldamo entry: describe from the data itself
            t, p = next((t, p) for p in phrases for t in p["tokens"] if f["feature"] in t["features"])
            summ = (f"Eldamo has no grammar entry for “{f['feature']}”. In the text it appears as "
                    f"“{t['text']}” ({t['gloss']}), from “{p['text']}” = “{p['gloss']}”.")
            lessons[lid] = {"id": lid, "title": label, "entry": None, "feature": f["feature"],
                            "generated": True, "summary": summ, "body": [summ],
                            "source": "Generated from Eldamo phrase data"}
    return lessons
```

In `feature_info`, the fallback dict drops `"rank"` and `"core"`:

```python
    return {"feature": f, "entry": guess if guess in gram_names else None, "auto": True}
```

Replace `interleave` and the whole old `build_curriculum` with:

```python
def course_lines(phrases, skel):
    """The course's phrases in teaching order: each skeleton text's lines, in line order."""
    return [p for tid in skel["texts"]
            for p in sorted((p for p in phrases if p["textId"] == tid), key=lambda p: p["line"])]

def build_curriculum(phrases, skel=SKEL):
    """One session per line. A session teaches the line's new lemmas and the features its tokens
    use for the first time, plus any foundation lessons attached to that line."""
    order = {f["feature"]: i for i, f in enumerate(skel["features"])}
    lines = course_lines(phrases, skel)
    warmups = [p["id"] for p in lines if p["textId"] != skel["goal"]]
    goal_count = len(lines) - len(warmups)
    verse_of = {ln: v for v, (a, b) in enumerate(skel["verses"], 1) for ln in range(a, b + 1)}
    sessions, seen_l, seen_f = [], set(), set()
    for n, p in enumerate(lines, 1):
        lemmas = []
        for t in p["tokens"]:
            if t["lemma"] and t["lemma"] not in seen_l and t["lemma"] not in lemmas:
                lemmas.append(t["lemma"])
        feats = sorted({x for t in p["tokens"] for x in t["features"]} - seen_f,
                       key=lambda x: (order.get(x, len(order)), x))
        seen_l |= set(lemmas); seen_f |= set(feats)
        s = {"n": n, "phrase": p["id"]}
        if p["textId"] == skel["goal"]:
            s["label"] = f"Line {p['line']} of {goal_count}"
            s["verse"] = verse_of.get(p["line"])
        else:
            s["label"] = f"Warm-up {warmups.index(p['id']) + 1} of {len(warmups)}"
        s["lemmas"] = lemmas
        s["lessons"] = [f["id"] for f in skel["foundations"] if f["at"] == p["id"]] + ["feat-" + x for x in feats]
        sessions.append(s)
    return sessions

def course_version(sessions):
    blob = json.dumps(sessions, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha1(blob.encode("utf-8")).hexdigest()[:12]
```

In `main`, replace the `sessions = ...` line and the `meta.json` dump:

```python
    sessions = build_curriculum(phrases)
```

```python
    dump("meta.json", {"eldamo_version": version, "language": LANG,
                       "attribution": "Data © 2008–2026 Paul Strack, Eldamo (https://eldamo.org), CC BY 4.0.",
                       "glosses": ["en"], "course_version": course_version(sessions)})
```

Replace the session-related half of `check` (everything after the forms loop) so it reads:

```python
def check(lex, forms, phrases, lessons, sessions):
    P = []
    for p in phrases:
        for t in p["tokens"]:
            if t["lemma"] and t["lemma"] not in lex:
                P.append(f"{p['id']}: lemma {t['lemma']} not in lexicon")
    for f in forms:
        if f["lemma"] not in lex: P.append(f"form of unknown lemma {f['lemma']}")
    return P + check_curriculum(sessions, phrases, lessons)

def check_curriculum(sessions, phrases, lessons, skel=SKEL):
    P = []
    by_id = {p["id"]: p for p in phrases}
    if [s["n"] for s in sessions] != list(range(1, len(sessions) + 1)): P.append("session numbers not contiguous")
    for tid in skel["texts"]:
        if not any(p["textId"] == tid for p in phrases): P.append(f"skeleton: text {tid} has no phrases")
    for f in skel["foundations"]:
        if f["at"] not in by_id: P.append(f"skeleton: foundation {f['id']} attaches to unknown phrase {f['at']}")
    listed = {f["feature"] for f in skel["features"]}
    for x in sorted({x for p in phrases for t in p["tokens"] for x in t["features"]} - listed):
        P.append(f"skeleton: feature {x} is used but not listed")
    lemma_at, feat_at = {}, {}
    for s in sessions:
        for l in s["lemmas"]:
            if l in lemma_at: P.append(f"lemma taught twice: {l}")
            lemma_at.setdefault(l, s["n"])
        for lid in s["lessons"]:
            les = lessons.get(lid)
            if les is None: P.append(f"session {s['n']}: unknown lesson {lid}"); continue
            if not les["summary"].strip(): P.append(f"empty lesson {lid}")
            elif les["summary"].rstrip().endswith(":"): P.append(f"summary ends on a colon: {lid}")
            if les.get("feature"): feat_at.setdefault(les["feature"], s["n"])
    for s in sessions:
        p = by_id.get(s["phrase"])
        if p is None: P.append(f"session {s['n']}: unknown phrase {s['phrase']}"); continue
        for t in p["tokens"]:
            if t["lemma"] and lemma_at.get(t["lemma"], 10**9) > s["n"]:
                P.append(f"session {s['n']}: {t['lemma']} is read before it is taught")
            for x in t["features"]:
                if feat_at.get(x, 10**9) > s["n"]:
                    P.append(f"session {s['n']}: feature {x} is used before it is taught")
    return P
```

Replace the curriculum lines of `report` (from `print(f"curriculum: ...` through the milestone loop) with:

```python
    print(f"curriculum: {len(sessions)} sessions, "
          f"{sum(len(s['lemmas']) for s in sessions)} words, {sum(len(s['lessons']) for s in sessions)} lessons")
    for s in sessions:
        print(f"  {s['n']:2} {s['label']:15} words {s['lemmas']} lessons {s['lessons']}")
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `source ../.venv-piper/bin/activate && python -m unittest discover -s tools -p 'test_build_data.py' -v`
Expected: 8 tests, OK.

- [ ] **Step 6: Regenerate the data and read the report**

Run: `source ../.venv-piper/bin/activate && python tools/build_data.py tools/eldamo-data.xml`
Expected: exit 0; `curriculum: 39 sessions, 76 words, 18 lessons`; `PROBLEMS: none`; session 1 `Warm-up 1 of 2` with lessons `['pronunciation', 'word-order', 'feat-genitive', 'feat-allative', 'feat-1st-pl-inclusive-poss', 'feat-present']`; session 5 `Line 3 of 37` with `['article']`; session 34 `Line 32 of 37` with no words. `meta.json` contains `course_version`.

- [ ] **Step 7: Commit checkpoint** — show the user `git diff --stat` and the report; ask for approval and their message. Files: `tools/skeleton.json tools/build_data.py tools/test_build_data.py composeApp/src/commonMain/composeResources/files/*.json`.

---

### Task 2: Build each session around its line (`:core` model, planner, exercises, steps)

**Files:**
- Modify: `tools/make_kotlin_fixtures.py`
- Regenerate: `core/src/commonTest/kotlin/app/quenya/core/Fixtures.kt`
- Modify: `core/.../Models.kt`, `core/.../CourseData.kt`, `core/.../Exercises.kt`, `core/.../Planner.kt`, `core/.../Study.kt` (SessionStep + `assembleSteps` only)
- Test: `core/src/commonTest/kotlin/app/quenya/core/CourseTest.kt` (rewrite), `StudyTest.kt` (compile fixes)

**Interfaces:**
- Consumes: Task 1 JSON shapes.
- Produces:
  - `data class Session(val n: Int, val phrase: String, val label: String, val verse: Int? = null, val lemmas: List<String> = emptyList(), val lessons: List<String> = emptyList())`
  - `Lesson` gains last parameter `val feature: String? = null`. `Slot` is deleted.
  - `CourseData.sessionByPhrase: Map<String, Session>`
  - `ExerciseFactory.formChoice(feature: String, n: Int, known: Set<String>)`, `cloze(phraseIds: List<String>, n: Int, wordPool: List<String> = phraseIds)`, `production(current: String, earlier: List<String>)`
  - `Planner.unlockedPhrases(upToSession: Int): List<String>`, `Planner.knownLemmas(upToSession: Int): Set<String>`, `Planner.plan(n, seed): SessionPlan`
  - `data class SessionPlan(val session: Session, val line: Phrase, val lessons: List<Lesson>, val introLemmas: List<LexiconEntry>, val exercises: List<ChoiceExercise>, val production: List<TypedExercise>)`
  - `SessionStep.LineIntro(val session: Session, val phrase: Phrase)`; `SessionStep.LessonStep(val lesson: Lesson, val examples: List<FormEntry>)` (no `feature` field — use `lesson.feature`).

- [ ] **Step 1: Update `tools/make_kotlin_fixtures.py`**

Replace the `need |= ...` curriculum line, `les_kt`, `slot_kt`, and `ses_kt` with:

```python
need |= {l for s in cur for l in s["lemmas"]}
```

```python
def les_kt(l): return f'Lesson({q(l["id"])}, {q(l["title"])}, {q(l["entry"])}, {b(l["generated"])}, {q(l["summary"])}, emptyList(), {q(l["source"])}, {q(l.get("feature"))})'
def ses_kt(s):
    verse = s["verse"] if s.get("verse") is not None else "null"
    return f'Session({s["n"]}, {q(s["phrase"])}, {q(s["label"])}, {verse}, {ql(s["lemmas"])}, {ql(s["lessons"])})'
```

(Delete `slot_kt`.) Run: `source ../.venv-piper/bin/activate && python tools/make_kotlin_fixtures.py`
Expected: `wrote Fixtures.kt: … 39 sessions`.

- [ ] **Step 2: Write the failing tests** — replace `CourseTest.kt` from `everySessionPlansAndHasContent` down to the end of the class (keep `textNormalisation` and the three `checker…` tests unchanged) with:

```kotlin
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
}
```

In `StudyTest.kt`, change the `taught` line in `fullCourseRunsEndToEnd` to:

```kotlin
        val taught = course.curriculum.flatMap { it.lemmas }.toSet()
```

and in `newWordsBecomeDueAndFailingShortensTheInterval` replace `repeat(9) { engine.finishSession() }                      // session 9 is the first vocab session` with:

```kotlin
        engine.finishSession()                                     // session 1 teaches four words
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :core:jvmTest`
Expected: compilation FAILS (`Unresolved reference: phrase`, `Slot`, `line`…).

- [ ] **Step 4: Update `Models.kt`** — delete `Slot`; replace `Session`; add `feature` to `Lesson`:

```kotlin
@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val entry: String? = null,
    val generated: Boolean,
    val summary: String,
    val body: List<String> = emptyList(),
    val source: String,
    val feature: String? = null,    // the grammar feature this lesson teaches; null for foundations
)
```

```kotlin
/** One session teaches one line: its new words and the lessons its grammar needs. */
@Serializable
data class Session(
    val n: Int,
    val phrase: String,             // phrase id of the line this session reads
    val label: String,              // "Warm-up 1 of 2" | "Line 4 of 37"
    val verse: Int? = null,         // verse of the goal text; null for warm-ups
    val lemmas: List<String> = emptyList(),
    val lessons: List<String> = emptyList(),
)
```

- [ ] **Step 5: `CourseData.kt`** — add after `lessonById`:

```kotlin
    val sessionByPhrase: Map<String, Session> = curriculum.associateBy { it.phrase }
```

- [ ] **Step 6: `Exercises.kt`** — replace `formChoice`'s signature and its first statement, `cloze`'s signature and its first two statements, and the whole of `production`:

```kotlin
    /** "Which is the <features> of <lemma>?" answered from an attested form of a word in [known]. */
    fun formChoice(feature: String, n: Int, known: Set<String>): List<ChoiceExercise> {
        val pool = course.formsByFeature[feature].orEmpty()
            .filter { it.clean && it.surface.isNotBlank() && usable(it.lemma) != null }
        // Prefer well-attested lemmas, one question per lemma + feature set.
        val candidates = pool.filter { it.lemma in known }.groupBy { it.lemma to it.features }
```

(the rest of `formChoice` — `.values.map { it.first() }` onward — is unchanged).

```kotlin
    /** Fill one word of a known phrase. Options are other words from [wordPool]'s phrases. */
    fun cloze(phraseIds: List<String>, n: Int, wordPool: List<String> = phraseIds): List<ChoiceExercise> {
        val phrases = phraseIds.mapNotNull { course.phraseById[it] }.filter { it.tokens.size >= 2 }
        val words = wordPool.mapNotNull { course.phraseById[it] }.flatMap { p -> p.tokens.map { it.text } }
            .distinctBy(Norm::skey)
```

(the loop from `val out = mutableListOf<ChoiceExercise>()` on is unchanged).

```kotlin
    /** Guided production: this line from its English, then one earlier line as revision. */
    fun production(current: String, earlier: List<String>): List<TypedExercise> =
        (listOf(current) + earlier.shuffled(rnd).take(1))
            .mapNotNull { course.phraseById[it] }.filter { it.gloss.isNotBlank() }
            .map {
                TypedExercise("prod:${it.id}", "Write in Quenya:\n“${it.gloss}”", it.id,
                    it.variants.firstOrNull()?.source ?: it.id)
            }
```

- [ ] **Step 7: Replace `Planner.kt` entirely**

```kotlin
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
            lessons.mapNotNull { it.feature }.forEach { addAll(f.formChoice(it, 2, known)) }
        }
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
```

- [ ] **Step 8: `Study.kt`** — replace the `SessionStep` interface and `assembleSteps`; make `unlockedPhrases` use the first session's line:

```kotlin
/** One screen in a study session. */
sealed interface SessionStep {
    data class Review(val lemma: LexiconEntry, val example: Pair<Phrase, Token>?) : SessionStep
    data class LineIntro(val session: Session, val phrase: Phrase) : SessionStep
    data class Intro(val lemmas: List<LexiconEntry>) : SessionStep
    data class LessonStep(val lesson: Lesson, val examples: List<FormEntry>) : SessionStep
    data class Reading(val phrases: List<Phrase>) : SessionStep
    data class Choice(val exercise: ChoiceExercise) : SessionStep
    data class Typed(val exercise: TypedExercise, val phrase: Phrase) : SessionStep
}
```

```kotlin
    /** Reader list: lines of completed sessions, and always the first session's line. */
    suspend fun unlockedPhrases(): List<Phrase> {
        val ids = planner.unlockedPhrases(store.completedSessions())
        val first = course.curriculum.firstOrNull()?.phrase
        val shown = if (first == null || first in ids) ids else listOf(first) + ids
        return shown.mapNotNull { course.phraseById[it] }
    }
```

```kotlin
    private fun assembleSteps(n: Int, seed: Long, reviewLemmaIds: List<String>): List<SessionStep> {
        val steps = mutableListOf<SessionStep>()
        reviewLemmaIds.forEach { id ->
            course.lex[id]?.let { steps += SessionStep.Review(it, course.exampleFor(id)) }
        }
        val plan = planner.plan(n, seed)
        steps += SessionStep.LineIntro(plan.session, plan.line)
        if (plan.introLemmas.isNotEmpty()) steps += SessionStep.Intro(plan.introLemmas)
        val known = planner.knownLemmas(n)
        plan.lessons.forEach { steps += SessionStep.LessonStep(it, lessonExamples(it, plan.line, known)) }
        steps += SessionStep.Reading(listOf(plan.line))
        plan.exercises.forEach { steps += SessionStep.Choice(it) }
        plan.production.forEach { steps += SessionStep.Typed(it, course.phraseById.getValue(it.phraseId)) }
        return steps
    }

    /** The line's own tokens with the feature first, then attested forms of known words, then others. */
    private fun lessonExamples(lesson: Lesson, line: Phrase, known: Set<String>): List<FormEntry> {
        val feature = lesson.feature ?: return emptyList()
        val source = line.variants.firstOrNull()?.source ?: line.id
        val fromLine = line.tokens.filter { feature in it.features && it.lemma != null }
            .map { FormEntry(it.lemma!!, it.text, it.features, source, clean = true) }
        val attested = course.formsByFeature[feature].orEmpty().filter { it.clean }
            .sortedWith(compareByDescending<FormEntry> { it.lemma in known }.thenByDescending { course.lex[it.lemma]?.attestations ?: 0 })
        return (fromLine + attested).distinctBy { Norm.skey(it.surface) }.take(6)
    }
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :core:jvmTest`
Expected: BUILD SUCCESSFUL; CourseTest and StudyTest pass.

- [ ] **Step 10: Commit checkpoint** — show `git diff --stat` and test output; ask for approval and message.

---

### Task 3: Session lifecycle — reviews after the end, Retake, course-version reset

**Files:**
- Modify: `core/.../Study.kt` (`ReviewStore`, `InMemoryReviewStore`, `buildSteps`, `assembleSteps` head, `finishSession`, new `resetAll`, `syncCourseVersion`)
- Modify: `core/.../io/DataLoader.kt`
- Test: `core/src/commonTest/kotlin/app/quenya/core/StudyTest.kt`

**Interfaces:**
- Consumes: Task 2 `assembleSteps`, `SessionStep.LineIntro`, `SessionStep.LessonStep`.
- Produces: `ReviewStore.courseVersion(): String?`, `setCourseVersion(v: String)`, `clearAll()` (cards, completed, streak, in-progress; not the course version).
- Produces: `StudyEngine.resetAll()`, `StudyEngine.syncCourseVersion(version: String): Boolean` (true when it reset).
- Produces: `DataLoader.courseVersion(meta: String): String`.

- [ ] **Step 1: Write the failing tests** — in `StudyTest.kt`, replace the end of `fullCourseRunsEndToEnd` from `assertEquals(0, engine.buildSteps(1).size, …)` through `assertTrue(rated > 100, …)` with:

```kotlin
        val after = engine.buildSteps(1)
        assertTrue(after.isNotEmpty() && after.all { it is SessionStep.Review }, "after the course: reviews only")
        val taught = course.curriculum.flatMap { it.lemmas }.toSet()
        assertEquals(taught, store.cards().keys, "one card per taught word")
        assertTrue(rated >= 70, "reviews should accumulate, got $rated")
```

Change `readerShowsElenSilaBeforeItIsUnlocked`'s `repeat(12) { engine.finishSession() }` to `engine.finishSession()`.

Add these tests to the class:

```kotlin
    private suspend fun finishCourse(engine: StudyEngine) { repeat(engine.sessionCount) { engine.finishSession() } }

    @Test fun reviewsOnlySessionAfterTheCourse() = runSuspend {
        var now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        finishCourse(engine)
        now += day
        val steps = engine.buildSteps(seed = 9)
        assertTrue(steps.isNotEmpty() && steps.size <= 12 && steps.all { it is SessionStep.Review })
        engine.advance(1)
        val (resumed, at) = StudyEngine(course, store) { now }.resume()!!
        assertEquals(steps, resumed)
        assertEquals(1, at)
        val streakBefore = store.streak().count
        engine.finishSession()
        assertEquals(engine.sessionCount, store.completedSessions())
        assertEquals(streakBefore + 1, store.streak().count)
        assertEquals(null, engine.resume())
    }

    @Test fun nothingDueAfterTheCourseBuildsNothing() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        finishCourse(engine)
        store.cards().keys.forEach { engine.rate(it, Rating.Good) }
        assertEquals(emptyList(), engine.buildSteps(seed = 1))
        assertEquals(null, store.inProgressSession())
    }

    @Test fun resetAllClearsEverything() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        repeat(3) { engine.finishSession() }
        engine.buildSteps(seed = 4)
        engine.resetAll()
        assertTrue(store.cards().isEmpty())
        assertEquals(0, store.completedSessions())
        assertEquals(Streak(), store.streak())
        assertEquals(null, store.inProgressSession())
    }

    @Test fun courseVersionChangeClearsOldInProgressSession() = runSuspend {
        val now = 1_800_000_000_000L
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { now }
        repeat(5) { engine.finishSession() }
        store.saveInProgressSession(InProgressSession(6, 1, 3, emptyList()))
        assertTrue(engine.syncCourseVersion("v1"), "no stored version: reset")
        assertEquals(0, store.completedSessions())
        assertEquals(null, store.inProgressSession())
        engine.finishSession()
        assertEquals(false, engine.syncCourseVersion("v1"), "same version: keep progress")
        assertEquals(1, store.completedSessions())
        assertTrue(engine.syncCourseVersion("v2"), "new version: reset")
        assertEquals(0, store.completedSessions())
        assertEquals("v2", store.courseVersion())
    }

    @Test fun lessonExamplesPutTheLineFirstWithoutDuplicates() = runSuspend {
        val store = InMemoryReviewStore()
        val engine = StudyEngine(course, store) { 0L }
        val n = course.curriculum.first { "feat-locative" in it.lessons }.n
        repeat(n - 1) { engine.finishSession() }
        val step = engine.buildSteps(seed = 1).filterIsInstance<SessionStep.LessonStep>().first { it.lesson.feature == "locative" }
        val line = course.phraseById.getValue(course.curriculum[n - 1].phrase)
        val lineForms = line.tokens.filter { "locative" in it.features }.map { it.text }
        assertEquals(lineForms, step.examples.take(lineForms.size).map { it.surface })
        assertEquals(step.examples.size, step.examples.map { Norm.skey(it.surface) }.toSet().size)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :core:jvmTest --tests 'app.quenya.core.StudyTest'`
Expected: compilation FAILS (`Unresolved reference: resetAll`, `syncCourseVersion`, `courseVersion`).

- [ ] **Step 3: Implement in `Study.kt`**

Add to `ReviewStore`:

```kotlin
    suspend fun courseVersion(): String?
    suspend fun setCourseVersion(v: String)
    /** Forget all progress: cards, completed sessions, streak, in-progress session. Keeps the course version. */
    suspend fun clearAll()
```

Add to `InMemoryReviewStore`:

```kotlin
    private var version: String? = null
    override suspend fun courseVersion() = version
    override suspend fun setCourseVersion(v: String) { version = v }
    override suspend fun clearAll() { map.clear(); done = 0; streak = Streak(); inProgress = null }
```

At the top of `assembleSteps`, after the reviews loop, stop for reviews-only sessions:

```kotlin
        if (n > sessionCount) return steps                     // after the course: reviews only
```

Replace `buildSteps` and `finishSession`, and add `resetAll` and `syncCourseVersion`:

```kotlin
    /** Steps for the next session: reviews, then the next line. After the last line, reviews only;
     *  empty when nothing is due. Persists an in-progress marker so the session is resumable. */
    suspend fun buildSteps(seed: Long): List<SessionStep> {
        val n = store.completedSessions() + 1
        val now = nowMs()
        val reviewIds = store.cards().entries.filter { it.value.dueMs <= now }.sortedBy { it.value.dueMs }
            .take(maxReviews).map { it.key }
        if (n > sessionCount && reviewIds.isEmpty()) return emptyList()
        val steps = assembleSteps(n, seed, reviewIds)
        store.saveInProgressSession(InProgressSession(n, seed, 0, reviewIds))
        return steps
    }
```

```kotlin
    /** Mark the session done: create cards for newly taught words and update the streak.
     *  A reviews-only session (after the last line) only updates the streak. */
    suspend fun finishSession() {
        val n = store.completedSessions() + 1
        if (n <= sessionCount) {
            val existing = store.cards()
            course.curriculum[n - 1].lemmas.filter { it !in existing }
                .forEach { store.saveCard(it, FsrsCard(dueMs = nowMs())) }
            store.setCompletedSessions(n)
        }
        store.clearInProgressSession()
        val today = (nowMs() + utcOffsetMs()) / FsrsScheduler.DAY_MS
        val s = store.streak()
        store.setStreak(when {
            s.lastDay == today -> s
            s.lastDay == today - 1 -> Streak(s.count + 1, today)
            else -> Streak(1, today)
        })
    }

    /** Retake: start the course over with no history. */
    suspend fun resetAll() = store.clearAll()

    /** Progress belongs to one curriculum. When the bundled course changes, start over. */
    suspend fun syncCourseVersion(version: String): Boolean {
        if (store.courseVersion() == version) return false
        store.clearAll()
        store.setCourseVersion(version)
        return true
    }
```

- [ ] **Step 4: `DataLoader.kt`** — add:

```kotlin
    /** Hash of curriculum.json written by build_data.py; progress resets when it changes. */
    fun courseVersion(meta: String): String =
        json.parseToJsonElement(meta).jsonObject.getValue("course_version").jsonPrimitive.content
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :core:jvmTest`
Expected: BUILD SUCCESSFUL, all CourseTest and StudyTest pass.

- [ ] **Step 6: Commit checkpoint** — show the diff and test output; ask for approval and message.

---

### Task 4: Store and app model

**Files:**
- Modify: `composeApp/src/commonMain/sqldelight/app/quenya/db/Review.sq`
- Modify: `composeApp/.../ui/SqlReviewStore.kt`
- Modify: `composeApp/.../ui/AppModel.kt`

**Interfaces:**
- Consumes: Task 3 `ReviewStore` additions, `StudyEngine.resetAll`.
- Produces (`AppModel`): `Screen.Markirya`; `val courseDone: Boolean`; `val linesTotal: Int`; `val linesRead: Int`; `var lastFinished: Session?` (null after a reviews-only session); `suspend fun retake()`; `val goalPhrases: List<Phrase>`.

- [ ] **Step 1: `Review.sq`** — append:

```sql
deleteAllCards:
DELETE FROM card;
```

- [ ] **Step 2: `SqlReviewStore.kt`** — add:

```kotlin
    override suspend fun courseVersion(): String? = q.getMeta("course_version").executeAsOneOrNull()
    override suspend fun setCourseVersion(v: String) { q.setMeta("course_version", v) }

    override suspend fun clearAll() {
        q.transaction {
            q.deleteAllCards()
            listOf("completed", "streak", "inprogress").forEach { q.deleteMeta(it) }
        }
    }
```

- [ ] **Step 3: `AppModel.kt`** — set `enum class Screen { Home, Study, Done, Reader, Markirya, About }`, and add to the class:

```kotlin
    /** The session just finished, or null when it was reviews only. Shown on the Done screen. */
    var lastFinished by mutableStateOf<Session?>(null); private set

    val courseDone: Boolean get() = completed >= total
    val linesTotal: Int get() = course.curriculum.count { it.verse != null }
    val linesRead: Int get() = course.curriculum.take(completed).count { it.verse != null }
    /** The goal text's lines in order, for the full-poem screen. */
    val goalPhrases: List<Phrase> get() = course.curriculum.filter { it.verse != null }.mapNotNull { course.phraseById[it.phrase] }

    suspend fun retake() {
        engine.resetAll()
        refresh()
        screen = Screen.Home
    }
```

and replace `next()`:

```kotlin
    suspend fun next() {
        if (index + 1 < steps.size) { index++; engine.advance(index); return }
        lastFinished = steps.firstNotNullOfOrNull { (it as? SessionStep.LineIntro)?.session }
        engine.finishSession()
        refresh()
        screen = Screen.Done
    }
```

- [ ] **Step 4: Verify** — `./gradlew :core:jvmTest` passes. (`composeApp` still fails to compile until Task 7; expected.)

- [ ] **Step 5: Commit checkpoint** — fold into Task 7's checkpoint unless the user asks otherwise.

---

### Task 5: Line views, Reader by verse, full-poem screen

**Files:**
- Create: `composeApp/.../ui/LineViews.kt`
- Modify: `composeApp/.../ui/App.kt` (delete `PlayButton`, `PhraseView`, `TokenDetail`; replace `ReaderScreen`; add `MarkiryaScreen`; add `Screen.Markirya` branch in `Root`)

**Interfaces:**
- Produces: `@Composable internal fun PlayButton(audioPlayer: AudioPlayer, filename: String?, label: String)`, `@Composable internal fun PhraseView(p: Phrase, course: CourseData, audioPlayer: AudioPlayer)`, `internal fun LazyListScope.versedLines(phrases: List<Phrase>, course: CourseData, audioPlayer: AudioPlayer)`.

- [ ] **Step 1: Create `LineViews.kt`** — move `PlayButton`, `PhraseView`, and `TokenDetail` out of `App.kt` unchanged except `private` → `internal` on `PlayButton` and `PhraseView`, and add `versedLines`:

```kotlin
package app.quenya.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quenya.core.*
import app.quenya.ui.resources.Res
import kotlinx.coroutines.launch

/** Reads a generated clip from resources and plays it; nothing when the clip is absent. Plays only on tap. */
@Composable
internal fun PlayButton(audioPlayer: AudioPlayer, filename: String?, label: String) {
    if (filename == null) return
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { audioPlayer.play(Res.readBytes("files/audio/$filename")) } }) { Text("▶ $label") }
}

/** Lines under headings: warm-ups first, then each verse of the goal text. */
internal fun LazyListScope.versedLines(phrases: List<Phrase>, course: CourseData, audioPlayer: AudioPlayer) {
    val groups = phrases.groupBy { course.sessionByPhrase[it.id]?.verse }
    groups[null]?.let { warmups ->
        item(key = "h-warmups") { Text("Warm-ups", style = MaterialTheme.typography.titleMedium) }
        items(warmups, key = { it.id }) { PhraseView(it, course, audioPlayer) }
    }
    groups.keys.filterNotNull().sorted().forEach { v ->
        item(key = "h-verse-$v") { Text("Verse $v", style = MaterialTheme.typography.titleMedium) }
        items(groups.getValue(v), key = { it.id }) { PhraseView(it, course, audioPlayer) }
    }
}
```

followed by the moved `PhraseView` and `TokenDetail` bodies, verbatim from `App.kt`.

- [ ] **Step 2: In `App.kt`** — delete the three moved functions; replace `ReaderScreen` and add `MarkiryaScreen`:

```kotlin
@Composable
private fun ReaderScreen(course: CourseData, unlockedPhrases: List<Phrase>, audioPlayer: AudioPlayer) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Reader", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Lines appear here as you finish their sessions. Tap a word for its meaning and form.", style = MaterialTheme.typography.bodySmall) }
        versedLines(unlockedPhrases, course, audioPlayer)
    }
}

@Composable
private fun MarkiryaScreen(course: CourseData, phrases: List<Phrase>, audioPlayer: AudioPlayer) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Markirya", style = MaterialTheme.typography.headlineMedium) }
        versedLines(phrases, course, audioPlayer)
    }
}
```

In `Root`'s `when`, add: `Screen.Markirya -> MarkiryaScreen(m.course, m.goalPhrases, m.audioPlayer)`.

- [ ] **Step 3: Verify** — deferred to Task 7's build (App.kt still has old-model references).

---

### Task 6: Session screens — line intro, lessons, review audio, accent row, header, Done

**Files:**
- Create: `composeApp/.../ui/AccentRow.kt`
- Modify: `composeApp/.../ui/App.kt` (`stepLabel`, `readingLabel` removed, `StudyScreen`, `ReviewView`, `LessonView`, new `LineIntroView`, `TypedView`, `DoneScreen`)

**Interfaces:**
- Consumes: Task 2 `SessionStep.LineIntro`, `LessonStep(lesson, examples)`; Task 4 `AppModel.lastFinished`, `linesRead`, `linesTotal`; Task 5 `PlayButton`, `PhraseView`.
- Produces: `@Composable fun AccentRow(onInsert: (String) -> Unit)`, `fun insertAtCursor(value: TextFieldValue, s: String): TextFieldValue`.

- [ ] **Step 1: Create `AccentRow.kt`**

```kotlin
package app.quenya.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Letters the course spells with that phone keyboards hide behind a long-press. */
private const val ACCENTS = "áéíóúäëïöü"

/** Replaces the selection (or inserts at the cursor) and puts the cursor after the new text. */
fun insertAtCursor(value: TextFieldValue, s: String): TextFieldValue {
    val start = value.selection.min
    val text = value.text.replaceRange(start, value.selection.max, s)
    return TextFieldValue(text, TextRange(start + s.length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentRow(onInsert: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ACCENTS.forEach { c ->
            OutlinedButton({ onInsert(c.toString()) }, Modifier.widthIn(min = 44.dp), contentPadding = PaddingValues(0.dp)) {
                Text(c.toString())
            }
        }
    }
}
```

- [ ] **Step 2: In `App.kt`** — delete `readingLabel`; replace `stepLabel`:

```kotlin
private fun stepLabel(step: SessionStep): String = when (step) {
    is SessionStep.Review -> "Reviews"
    is SessionStep.LineIntro -> "This session's line"
    is SessionStep.Intro -> "New words"
    is SessionStep.LessonStep -> "${step.lesson.title} — grammar"
    is SessionStep.Reading -> "Read the line"
    is SessionStep.Choice -> "Questions"
    is SessionStep.Typed -> "Write it yourself"
}
```

In `StudyScreen`, put the session label beside the bar and route the new steps:

```kotlin
    val label = m.steps.firstNotNullOfOrNull { (it as? SessionStep.LineIntro)?.session?.label } ?: "Reviews"
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (m.index + 1f) / m.steps.size }, modifier = Modifier.weight(1f))
            TextButton({ m.exitSession() }) { Text("✕") }
        }
        Text(stepLabel(step), style = MaterialTheme.typography.labelLarge)
        key(m.index) {
            val next: () -> Unit = { scope.launch { m.next() } }
            when (step) {
                is SessionStep.Review -> ReviewView(step, m.course, m.audioPlayer) { r -> scope.launch { m.rate(step.lemma.id, r); m.next() } }
                is SessionStep.LineIntro -> LineIntroView(step, m.course, m.audioPlayer, next)
                is SessionStep.Intro -> IntroView(step, m.course, m.audioPlayer, next)
                is SessionStep.LessonStep -> LessonView(step, m.course, next)
                is SessionStep.Reading -> ReadingView(step, m.course, m.audioPlayer, next)
                is SessionStep.Choice -> ChoiceView(step.exercise, next)
                is SessionStep.Typed -> TypedView(step, m.checker, next)
            }
        }
    }
```

Replace `ReviewView`:

```kotlin
@Composable
private fun ReviewView(s: SessionStep.Review, course: CourseData, audioPlayer: AudioPlayer, onRate: (Rating) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Text(s.lemma.lemma, style = MaterialTheme.typography.displaySmall)
    PlayButton(audioPlayer, course.audioForWord(s.lemma.lemma), "Play word")
    if (!shown) Button({ shown = true }) { Text("Show answer") }
    else {
        Text("“${s.lemma.gloss.en}”", style = MaterialTheme.typography.titleLarge)
        s.example?.let { (p, _) ->
            Text("${p.text} — “${p.gloss}”")
            PlayButton(audioPlayer, course.audioForLine(p.id), "Play line")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { r -> Button({ onRate(r) }) { Text(r.name) } }
        }
    }
}
```

Add `LineIntroView`:

```kotlin
@Composable
private fun ColumnScope.LineIntroView(s: SessionStep.LineIntro, course: CourseData, audioPlayer: AudioPlayer, onNext: () -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.session.label, style = MaterialTheme.typography.titleMedium)
        Text(s.phrase.text, style = MaterialTheme.typography.headlineSmall)
        Text("“${s.phrase.gloss}”", style = MaterialTheme.typography.bodyLarge)
        PlayButton(audioPlayer, course.audioForLine(s.phrase.id), "Play line")
        Text("By the end of this session you can read this line.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onNext) { Text("Continue") }
}
```

In `IntroView`, add an `audioPlayer: AudioPlayer` parameter and, after each word's `Text("${e.pos} — “${e.gloss.en}”")`, add `PlayButton(audioPlayer, course.audioForWord(e.lemma), "Play word")`.

`LessonView` is unchanged (its `s.lesson`, `s.examples` still exist).

In `TypedView`, replace the `text` state, field, and check button:

```kotlin
    var value by remember { mutableStateOf(TextFieldValue("")) }
```

```kotlin
        OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), enabled = result == null, singleLine = true)
        val r = result
        if (r == null) {
            AccentRow { value = insertAtCursor(value, it) }
            Button({ result = checker.check(value.text, s.phrase) }, enabled = value.text.isNotBlank()) { Text("Check") }
        } else {
```

(add `import androidx.compose.ui.text.input.TextFieldValue`; the `else` branch body is unchanged).

Replace `DoneScreen`:

```kotlin
@Composable
private fun DoneScreen(m: AppModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = m.lastFinished
        val p = s?.let { m.course.phraseById[it.phrase] }
        when {
            s == null || p == null -> Text("Reviews complete", style = MaterialTheme.typography.headlineMedium)
            s.verse == null -> Text("${s.label} done", style = MaterialTheme.typography.headlineMedium)
            else -> Text("You can now read line ${p.line}", style = MaterialTheme.typography.headlineMedium)
        }
        if (p != null) {
            Text(p.text, style = MaterialTheme.typography.titleLarge)
            Text("“${p.gloss}”")
            PlayButton(m.audioPlayer, m.course.audioForLine(p.id), "Play line")
        }
        Text("${m.linesRead} of ${m.linesTotal} lines read. Streak: ${m.streak} day(s).")
        Button({ m.go(Screen.Home) }) { Text("Home") }
    }
}
```

- [ ] **Step 3: Verify** — deferred to Task 7's build.

---

### Task 7: Home, Retake, startup version check

**Files:**
- Modify: `composeApp/.../ui/App.kt` (`App` `LaunchedEffect`, `Root`, `HomeScreen`)

**Interfaces:**
- Consumes: Task 3 `DataLoader.courseVersion`, `StudyEngine.syncCourseVersion`; Task 4 `AppModel` members.

- [ ] **Step 1: Startup** — in `App`'s `LaunchedEffect`, build the engine first and sync before `refresh()`:

```kotlin
            val (course, meta) = loadAssets()
            val store = SqlReviewStore(driver)
            val engine = StudyEngine(course, store, utcOffsetMs = utcOffsetMs, nowMs = nowMs)
            engine.syncCourseVersion(DataLoader.courseVersion(meta))
            val m = AppModel(course, engine, store, DataLoader.about(meta), audioPlayer, themePreference)
            m.refresh()
            model = m
```

- [ ] **Step 2: `Root`** — pass retake: `Screen.Home -> HomeScreen(m, onStart = { scope.launch { m.start() } }, onResume = { scope.launch { m.resume() } }, onRetake = { scope.launch { m.retake() } })`.

- [ ] **Step 3: Replace `HomeScreen`**

```kotlin
@Composable
private fun HomeScreen(m: AppModel, onStart: () -> Unit, onResume: () -> Unit, onRetake: () -> Unit) {
    var confirmRetake by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eäsis", style = MaterialTheme.typography.headlineLarge)
        Text("Elvish as She is Spoke.", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                append("Learn to read ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Markirya") }
                append(", Tolkien’s poem of the white ship, line by line.")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val s = m.nextSession
        val p = s?.let { m.course.phraseById[it.phrase] }
        if (s != null && p != null) {
            Text("Next: ${s.label}", style = MaterialTheme.typography.titleMedium)
            Text(p.text, style = MaterialTheme.typography.titleLarge)
            Text("“${p.gloss}”", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                buildAnnotatedString {
                    append("You can read all of ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Markirya") }
                    append(".")
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Button({ m.go(Screen.Markirya) }) { Text("Read Markirya") }
        }
        LinearProgressIndicator(progress = { m.linesRead.toFloat() / m.linesTotal }, modifier = Modifier.fillMaxWidth())
        Text("${m.linesRead} of ${m.linesTotal} lines read", style = MaterialTheme.typography.labelSmall)
        Row {
            Text("Reviews due: ${m.due}    ")
            Text("Streak: ${m.streak} day(s)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        when {
            m.inProgress -> Button(onResume) { Text("Resume session") }
            !m.courseDone -> Button(onStart) { Text("Start session") }
            m.due > 0 -> Button(onStart) { Text("Review (${m.due} due)") }
        }
        TextButton({ confirmRetake = true }) { Text("Retake course") }
    }
    if (confirmRetake) {
        AlertDialog(
            onDismissRequest = { confirmRetake = false },
            title = { Text("Start over?") },
            text = { Text("This clears all progress.") },
            confirmButton = { TextButton({ confirmRetake = false; onRetake() }) { Text("Start over") } },
            dismissButton = { TextButton({ confirmRetake = false }) { Text("Cancel") } },
        )
    }
}
```

- [ ] **Step 4: Build everything**

Run: `./gradlew :core:jvmTest :androidApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL. Fix any unused-import or missing-import errors from Tasks 5–7 (e.g. `FlowRow`/`clickable` imports left in `App.kt` after the move; `ColumnScope` import is covered by `androidx.compose.foundation.layout.*`).

- [ ] **Step 5: Commit checkpoint** — Tasks 4–7 together: show `git diff --stat` and the build output; ask for approval and message. (The screens are checked on the emulator in Task 9 before the user is asked about any later commit.)

---

### Task 8: Headword audio

**Files:**
- Modify: `tools/generate_audio.py` (`inventory`, ~118-127)
- Create: `tools/test_generate_audio.py`
- Regenerate: `composeApp/src/commonMain/composeResources/files/audio/*.m4a`, `audio.json`

**Interfaces:**
- Produces: `inventory(files_dir) -> (words: dict[skey, text], lines: dict[phrase_id, text])`, now including every curriculum lemma's headword.

- [ ] **Step 1: Write the failing test** — `tools/test_generate_audio.py`:

```python
"""Which clips generate_audio.py plans. No Piper, no ffmpeg."""
import json
import tempfile
import unittest
from pathlib import Path

from generate_audio import inventory


class InventoryTests(unittest.TestCase):
    def test_taught_headwords_join_token_words(self):
        with tempfile.TemporaryDirectory() as d:
            files = Path(d)
            (files / "phrases.json").write_text(json.dumps([
                {"id": "p1", "text": "man cenuva", "tokens": [{"text": "man"}, {"text": "cenuva"}]}]), encoding="utf-8")
            (files / "lexicon.json").write_text(json.dumps([
                {"id": "man", "lemma": "man"}, {"id": "ken", "lemma": "cen-"}, {"id": "x", "lemma": "unused"}]),
                encoding="utf-8")
            (files / "curriculum.json").write_text(json.dumps([{"n": 1, "lemmas": ["man", "ken"]}]), encoding="utf-8")
            words, lines = inventory(files)
        self.assertEqual({"man": "man", "cenuva": "cenuva", "cen": "cen-"}, words)
        self.assertEqual({"p1": "man cenuva"}, lines)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run it to verify it fails**

Run: `source ../.venv-piper/bin/activate && python -m unittest discover -s tools -p 'test_generate_audio.py' -v`
Expected: FAIL — `cen` missing from `words`.

- [ ] **Step 3: Implement** — replace `inventory`:

```python
def inventory(files_dir):
    """Word clips: each token's text, then each taught lemma's headword. Line clips: each phrase."""
    phrases = json.loads((files_dir / "phrases.json").read_text(encoding="utf-8"))
    lexicon = {e["id"]: e for e in json.loads((files_dir / "lexicon.json").read_text(encoding="utf-8"))}
    curriculum = json.loads((files_dir / "curriculum.json").read_text(encoding="utf-8"))
    words, lines = {}, {}
    for phrase in phrases:
        for token in phrase["tokens"]:
            key = skey(token["text"])
            if key and key not in words:
                words[key] = token["text"]
        lines[phrase["id"]] = phrase["text"]
    for session in curriculum:
        for lemma_id in session["lemmas"]:
            headword = lexicon[lemma_id]["lemma"]
            key = skey(headword)
            if key and key not in words:
                words[key] = headword
    return words, lines
```

Update the module docstring's pipeline line to: `Pipeline: phrase tokens and taught headwords -> tools/quenya_phonetics.py -> …`.

- [ ] **Step 4: Run the tests**

Run: `source ../.venv-piper/bin/activate && python -m unittest discover -s tools -p 'test_*.py' -v`
Expected: all OK (build_data, generate_audio, piper_utterance).

- [ ] **Step 5: Regenerate audio**

Run: `source ../.venv-piper/bin/activate && python tools/generate_audio.py`
Expected: exit 0; prints `N unique words + 39 lines -> M clips, audio/ … KiB` with N > 84. Record N, M, KiB for Task 9's docs.

- [ ] **Step 6: Commit checkpoint** — show the counts and `git status`; ask for approval and message.

---

### Task 9: Docs, device check, sign-off

**Files:**
- Modify: `SPEC.md` (Decisions → Curriculum row; Milestones section), `README.md` (intro paragraph), `BACKLOG.md` (new done entry at top of the done list), `docs/specs/neural-reader-audio.md` (inventory paragraph and counts)

- [ ] **Step 1: `SPEC.md`** — Curriculum row becomes: `One session per line, generated from tools/skeleton.json: the warm-ups Elen síla and Aiyá Eärendil, then Markirya lines 1–37. A session teaches its line's new words and the grammar its tokens use for the first time. See docs/specs/text-first-course.md.` Replace the Milestones table and paragraph with a `## Course` section: `39 sessions: 2 warm-ups, then Markirya lines 1–37 in five verses. 76 words, 18 lessons (4 foundations, 14 grammar features).` (use the numbers from Task 1's report).

- [ ] **Step 2: `README.md`** — replace the last two sentences of the intro paragraph with: `Each session teaches one line of a text: its new words and grammar, then you read and hear it. Two short warm-up lines come first; the goal is all 37 lines of *Markirya*.`

- [ ] **Step 3: `BACKLOG.md`** — add a done entry: `Text-first course (docs/specs/text-first-course.md): one session per line, Retake, full-poem view, accent buttons, review audio, headword clips.`

- [ ] **Step 4: `docs/specs/neural-reader-audio.md`** — in "Inventory and filenames", first paragraph becomes: `The generator reads phrases.json, curriculum.json, and lexicon.json from its output directory. The default is composeApp/src/commonMain/composeResources/files.` Add after the word-clip paragraph: `Every lemma in curriculum.json also gets a word clip for its headword, under the headword's skey, unless a token already has that key.` Update the library sentence with Task 8's counts.

- [ ] **Step 5: Personal-details scan**

Run: `git diff | grep -n -i -E '/Users/|/home/' ; echo "exit $?"`
Expected: `exit 1` (no matches).

- [ ] **Step 6: Full build and install**

Run: `./gradlew :core:jvmTest :androidApp:assembleDebug :composeApp:compileKotlinIosSimulatorArm64 && ./gradlew :androidApp:installDebug`
Expected: BUILD SUCCESSFUL; `Installed on N device(s)`.

- [ ] **Step 7: Emulator walk-through with screenshots** — launch `app.quenya`, take a screenshot (`adb -s emulator-5554 exec-out screencap -p > <scratch>/<name>.png`) of each and check:
  1. Home: goal sentence, "Next: Warm-up 1 of 2", *elen síla lúmenn’ omentielvo*, its English, `0 of 37 lines read`, Start session, Retake course.
  2. Session: header `Warm-up 1 of 2`; This session's line with ▶ Play line; New words with ▶ Play word; grammar steps (Pronunciation…, Word order…, Genitive…); Read the line; Questions; Write it yourself with the accent row.
  3. Accent row (Review Focus 5): type `mar`, tap between `m` and `a` to place the cursor, tap `á` → field reads `máar`, cursor after `á`; select `ar` and tap `ë` → `máë`.
  4. Done: `Warm-up 1 of 2 done`, the line with ▶ Play line, `0 of 37 lines read`.
  5. Review card: start session 2 right after finishing session 1 (session 1's words are due immediately, so session 2 opens with review cards). Check the headword with ▶ Play word, and after Show answer the example line with ▶ Play line.
  6. Reader: `Warm-ups` heading with finished lines.
  7. Retake: dialog `Start over?` / `This clears all progress.`; after Start over, Home shows Warm-up 1 of 2 and no Resume.
  8. No audio plays without tapping ▶.

- [ ] **Step 8: Sign-off** — show the user the screenshots and a summary of what changed since the last approved commit; ask for approval and their message before committing the docs and anything uncommitted. Do not push.
