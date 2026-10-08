# Eäsis — Quenya self-study spec

Personal-use Android app (Kotlin Multiplatform, iOS-ready) that teaches Late Quenya to a learner with no
prior exposure, using data generated from Eldamo. Goal: read the first 12 lines of *Markirya* with
tap-to-translate help within about 40 sessions.

Open work is listed in [BACKLOG.md](BACKLOG.md). This document describes the app as it is.

## Decisions

| Area | Decision |
|---|---|
| Language scope | Late-period Quenya (`l="q"`, 1950–73). |
| Confidence | An entry is `attested` when Eldamo cites at least one occurrence, otherwise `unverified`. Eldamo's raw `mark` is stored and shown as written. |
| Audience | Personal use, sideloaded. Every exercise and form carries its Eldamo `source`. |
| Data | Eldamo XML becomes JSON at build time (`tools/build_data.py`). Lesson prose, the lexicon, and the lesson order are generated. `tools/skeleton.json` is the table of contents: the texts in order, the verses, and where each lesson attaches. `tools/quenya_phonetics.py` is the Appendix E spelling-to-phoneme and stress rules. Neither file holds lesson content. |
| Gloss language | English, stored as `{"en": …}`. |
| Stack | Kotlin Multiplatform + Compose Multiplatform; SQLDelight for review state; kotlinx.serialization for data; FSRS-6 ported from py-fsrs 6.3.2. |
| Reader | Tap a word for its lexicon entry. Play plays the generated AAC clip when the manifest has one. Clips are built offline with Piper (`en_GB-cori-high`); the phonetics module supplies the pronunciation. See [docs/specs/neural-reader-audio.md](docs/specs/neural-reader-audio.md). |
| Free practice | Guided production: English prompt, learner types Quenya, checked against the key (headword + attested variants). The checker explains each word against the lexicon and attested forms; it never judges grammar. |
| Curriculum | One session per line, generated from `tools/skeleton.json`: the warm-ups *Elen síla* and *Aiyá Eärendil*, then *Markirya* lines 1–37. A session teaches its line's new words and the grammar its tokens use for the first time. See [docs/specs/text-first-course.md](docs/specs/text-first-course.md). |
| Session | Reviews → the session's line → new words → grammar → read the line → questions → write it yourself. After the last line, reviews only. |
| Learner | One learner. Review state is one SQLDelight store on the device. |

## Attribution (required)

Eldamo is credited on the About screen. The licence split is in [licence.md](licence.md).

## Course (generated; see `build_data.py` output)

39 sessions: 2 warm-ups, then *Markirya* lines 1–37 in five verses (lines 1–5, 6–13, 14–22, 23–30,
31–37). 76 words and 18 lessons: 4 foundations and 14 grammar features. Line 32 (*ondolissë mornë*)
has no word analysis in Eldamo and teaches no words.

### Correction to an earlier analysis
An early coverage report said `markirya-12` needed 11 features and 28 words and would land at sessions
22–32. That count only included poem lines carrying Eldamo token analysis. Two of the first 12 lines
(*man cenuva fána cirya?*, *man tiruva fána cirya?*) have none, and they contain the **future tense**.
The pipeline now resolves such lines by strict, accent-sensitive lookup (never by guessing), which adds
the future tense and more words and moves the milestone to session 35.
`tools/eldamo_report.py` now prints how many lines lack analysis, and says its counts are lower bounds.

## What Eldamo provides (verified in the XML)

- `<word l v speech gloss>` entries. Late Quenya: 4,010, of which 3,367 are lexicon entries.
- Attested inflected forms: `<ref v source><inflect form="…"/></ref>`; 1,761 distinct forms are used.
- Tokenized phrases: `<word speech="phrase">` with `<element l v form>` per token (72 of 101 milestone
  tokens come directly from this). Texts (`speech="text"`) list their lines in order.
- 106 grammar entries (long HTML prose, 11k–36k characters each).

## Resolved since the first analysis

- **Lesson text.** The pipeline takes an Eldamo grammar entry's lead paragraphs, skips a leading
  table-of-contents bullet list, and stops before "Origins" or "Conceptual Development". It keeps
  going until about 200 characters and never past 700, cutting a too-long lead at the last sentence
  boundary. The full entry stays behind "Read full entry". Nothing is summarised by a model.
- **Drill pools.** All 14 features have enough attested forms to build the generated sessions
  (tested: no feature session has fewer than 3 exercises; most have 5–8).
  `1st-pl-inclusive-poss` has 2 attested forms. That feature is taught inside its phrase, and its
  lesson uses the shared possessive entry. Exercises are built only from attested forms.

## Pipeline (build time)

1. `build_data.py` parses the XML into `lexicon`, `forms`, `phrases` (tokens with lemma, features,
   `resolution`), `lessons`, `curriculum` and `meta` JSON.
2. For each milestone it takes the words and features needed, orders feature lessons by the skeleton
   rank, interleaves vocabulary sessions, and ends each block with a reading session.
3. Invariants are checked on every run (lemmas exist, nothing read before it is taught, contiguous
   session numbers, no empty lessons).
4. At runtime exercises are generated from the data: form choice (from attested forms), cloze and meaning
   (from phrases and glosses), and guided production. Each carries its answer key and source.

## Data quality

Token resolution is strict. Eldamo's `<element>` analysis wins when the tokens line up, including
hyphenated compounds split to match those elements. Otherwise a lemma is accepted only on an
accent-sensitive match. A homonym is a single match when one lemma has at least five times the
attestations of the next. A weaker match is left unresolved. The reader may show a guess, and it
labels every lookup as lookup rather than Eldamo's own analysis.

The *Markirya* lines are Eldamo's editorially normalised second Late Quenya draft (MC/221–2).

## Engineering rules

1. The iOS target must compile in CI. 2. No platform APIs in `core`. 3. Test logic, not screens; every
exercise has tests proving its answer comes from the data. 4. Review state is separate from language data.
