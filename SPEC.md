# Quenya Self-Study App: Spec (MVP build)

Personal-use Android app (Kotlin Multiplatform, iOS-ready) that teaches Late Quenya to a learner with no
prior exposure, using data generated from Eldamo. Goal: read the first 12 lines of *Markirya* with
tap-to-translate help within about 40 sessions.

## Decisions

| Area | Decision |
|---|---|
| Language scope | Late-period Quenya (`l="q"`, 1950–73) is the core. Other periods and neo-Elvish are a later, labelled layer (backlog 8). |
| Confidence | Entries are `attested` (≥1 cited occurrence) or `unverified`. Eldamo's raw `mark` is shown unchanged and not interpreted (backlog 4). |
| Audience | Personal use, sideloaded. Data carries `source` references throughout. |
| Data | Eldamo XML → JSON at build time (`tools/build_data.py`). **No hand-authored content.** |
| Gloss language | English, stored as `{"en": …}`. Eldamo has no Afrikaans (backlog 9). |
| Stack | Kotlin Multiplatform + Compose Multiplatform; SQLDelight for review state; kotlinx.serialization for data; FSRS-6 ported from py-fsrs 6.3.2. |
| Free practice | Guided production: English prompt, learner types Quenya, checked against the key (headword + attested variants). The checker explains each word against the lexicon and attested forms; it never judges grammar. |
| Curriculum | Generated backwards from three target texts, topic order from `tools/skeleton.json` (table of contents only). |
| Session | 15 min: reviews → new material → exercises → production. |
| Deferred | Export/import of data bundles (backlog 7), speech, multi-user. |

## Attribution (required)

Data © 2008–2026 Paul Strack, [Eldamo](https://eldamo.org), CC BY 4.0. Credited on the About screen.
Tolkien's texts remain under copyright; this is a personal, non-distributed build.

## Milestones (generated; see `build_data.py` output)

| Milestone | Text | Session | Cumulative grammar features | Cumulative words taught |
|---|---|---|---|---|
| `elen-sila` | *elen síla lúmenn' omentielvo* | **13** | 4 | 4 |
| `aiya-earendil` | *aiya Eärendil elenion ancalima* | **18** | 6 | 7 |
| `markirya-12` | *Markirya*, lines 1–12 | **35** | 12 | 32 |
| `markirya-full` | *Markirya*, all 37 lines (stretch) | 49 | 14 | 74 |

The course is 49 sessions: 18 lessons, 10 practice, 14 vocabulary, 7 reading. The 40-session target
is met for `markirya-12`, with **5 sessions to spare**, which is thin (see risks).

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

- **Lesson text length.** The pipeline takes the entry's lead paragraph(s), skipping a leading table of
  contents and stopping before "Origins"/"Conceptual Development" (about 250–580 characters), and keeps the
  full entry behind "Read full entry". Nothing is summarised by a model, so nothing can be invented.
- **Drill pools.** All 14 features have enough attested forms to build the generated sessions
  (tested: no feature session has fewer than 3 exercises; most have 5–8).

## Pipeline (build time)

1. `build_data.py` parses the XML into `lexicon`, `forms`, `phrases` (tokens with lemma, features,
   `resolution`), `lessons`, `curriculum` and `meta` JSON.
2. For each milestone it takes the words and features needed, orders feature lessons by the skeleton
   rank, interleaves vocabulary sessions, and ends each block with a reading session.
3. Invariants are checked on every run (lemmas exist, nothing read before it is taught, contiguous
   session numbers, no empty lessons).
4. At runtime exercises are generated from the data: form choice (from attested forms), cloze and meaning
   (from phrases and glosses), and guided production. Each carries its answer key and source.

## Data quality notes

- 4 of 101 tokens stay unresolved (`ëar-celumessen`, `talta-taltala`, `ondolissë`, `mornë`); 3 `cirya`
  tokens are ambiguous between homonyms. The reader marks heuristic matches as "matched by lookup".
- The *Markirya* lines are Eldamo's editorially normalised second Late Quenya draft (MC/221–2).

## Engineering rules

1. The iOS target must compile in CI. 2. No platform APIs in `core`. 3. Test logic, not screens; every
exercise has tests proving its answer comes from the data. 4. Review state is separate from language data.

## Risks

- **Thin margin.** 5 spare sessions. Noun-class dependence of endings (backlog 1) could consume them.
- **Session model is a guess** (backlog 2).
- **Unverified build.** The Android/iOS layer has never been compiled (backlog 3).
- **Thin pools for rare features.** `1st-pl-inclusive-poss` has 2 attested forms; it is taught inside the
  phrase and its lesson draws on the shared possessive entry.
