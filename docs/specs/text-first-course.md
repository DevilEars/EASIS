# Text-first course

The course teaches one thing: reading *Markirya*, all 37 lines. Every session works toward one line. The learner always knows which line comes next, and every word and grammar topic in the course is one that a line needs.

This replaces the topic-ordered curriculum (43 sessions of lessons, practice, vocabulary, and readings). That order taught grammar for eight sessions before any word, drew questions from words the learner had never met, and gave no view of the goal.

## Course structure

The course is 39 sessions, one per line:

| Session | Line | Label on screen |
|---|---|---|
| 1 | *elen síla lúmenn’ omentielvo* | Warm-up 1 of 2 |
| 2 | *aiya Eärendil elenion ancalima* | Warm-up 2 of 2 |
| 3–39 | *Markirya* lines 1–37 | Line N of 37 |

A session teaches the line's new words (0–4, the lemmas of its tokens not taught earlier) and the grammar topics its tokens use for the first time. Measured on the current data: 76 words, 14 grammar topics.

The four foundation lessons attach to a session in `tools/skeleton.json`: pronunciation and word order to session 1, noun classes to the session that first teaches the plural (session 2), and the definite article to the session that first uses *i* (*Markirya* line 3, session 5).

A session that teaches more than one topic shows them in the skeleton's order, foundations first.

*Markirya* line 32, *ondolissë mornë*, has no word analysis in Eldamo. Its session reads and plays the line with Eldamo's gloss and teaches no words.

*Markirya* has five verses, each opening with a *Man …?* question: lines 1–5, 6–13, 14–22, 23–30, and 31–37. The verse ranges are listed in `tools/skeleton.json`, because line 37 also opens with *Man* but closes verse five.

## Generation

`tools/skeleton.json` holds the table of contents only:

- the texts in order: `elen-sila`, `aiya-earendil`, then the *Markirya* phrase ids in line order;
- the verse ranges of *Markirya*;
- the foundation lessons and where each attaches;
- each grammar feature's Eldamo entry and label.

The ranks, `sessions_per_core_feature`, `sessions_per_minor_feature`, `words_per_vocab_session`, `reading_lines_per_session`, and `markirya_core_lines` are removed.

`build_curriculum` in `tools/build_data.py` emits one session per line, in skeleton order. Each session in `curriculum.json` carries:

- `n`, 1-based;
- `phrase`, the line's phrase id;
- `label`, "Warm-up 1 of 2" or "Line 4 of 37";
- `verse`, 1–5 for *Markirya* lines, absent for warm-ups;
- `lemmas`, the new words, in token order;
- `lessons`, the lesson ids taught here (foundations and features), in skeleton order.

`milestone`, `stretch`, and the slot list are removed from the format.

`build_data.py` fails the run when:

- a token's lemma is first taught after that token's line;
- a token's feature is first taught after that token's line;
- a lemma is taught twice;
- a skeleton phrase id or lesson id does not exist.

The report prints each session's label, new words, and lessons, and the totals.

`meta.json` gains `course_version`: the first 12 hex characters of the SHA-1 of the compact `curriculum.json`. The app resets progress when this changes (see Progress).

## A session

Steps, in order. A step with nothing to show is skipped.

1. **Reviews.** Flashcards for due words, at most 12. The card shows the headword with ▶ Play word. After **Show answer**, the gloss and the example line with ▶ Play line.
2. **This session's line.** The label, the line, its English, and ▶ Play line. "By the end of this session you can read this line."
3. **New words.** Each new word with its part of speech, gloss, and ▶ Play word.
4. **Grammar.** One step per lesson taught here: the Eldamo lesson text as now. Examples are the line's own tokens with that feature first, then attested forms of words already taught, then other attested forms.
5. **Read the line.** The Reader's line view (see Line view).
6. **Questions.** Multiple choice, about 6–8:
   - meaning of each new word;
   - a missing word in this line or an earlier line;
   - for each feature taught here, the form of a word already taught.

   The word being asked about is always one taught at or before this session. Wrong options may come from the whole lexicon.
7. **Write it yourself.** Type this line from its English, then one earlier line. Under the answer field, a row of buttons inserts **á é í ó ú ä ë ï ö ü** at the cursor.

The session header shows the label beside the progress bar.

## Screens

**Home**, during the course:

- "Learn to read *Markirya*, Tolkien’s poem of the white ship, line by line."
- "Next: Line 4 of 37", the line, and its English; a progress bar of lines read.
- Reviews due and streak.
- **Start session** or **Resume session**.
- **Retake course**, a text button.

**Home**, after session 39:

- "You can read all of *Markirya*."
- **Read Markirya** opens the full poem.
- **Review (N due)**, shown when N > 0, starts a reviews-only session.
- Reviews due and streak.
- **Retake course**.

**Session complete:** "You can now read line 4", the line with ▶ Play line, and "4 of 37 lines read". Warm-up sessions say "Warm-up 1 of 2 done".

**Full *Markirya*:** all 37 lines in order, under the headings Verse 1 … Verse 5. Each line is the line view.

**Reader tab:** the unlocked lines (lines whose session is complete, plus the first warm-up line). Warm-ups first, then *Markirya* grouped by verse.

**Line view** (Reader, Read the line, full *Markirya*): the line, its English, and one **▶ Play line** button. The words are tappable text, not buttons. Tapping a word opens a card under the line with its lemma, gloss, and form, and **▶ Play word** when that word has a clip. One card is open per line; tapping another word replaces it.

**Retake course** asks "Start over? This clears all progress." and, on confirm, resets as below and returns to Home.

## Progress

`StudyEngine.resetAll()` deletes every card, sets completed sessions to 0, clears the streak, and clears any in-progress session. Retake calls it.

On start, the app compares `course_version` from `meta.json` with the value stored in the `meta` table. If they differ, or none is stored, it calls `resetAll()` and stores the new value. The first launch after this change therefore starts the new course from session 1.

After session 39, a reviews-only session holds the due reviews (at most 12) and nothing else. It is resumable like any session. Finishing it updates the streak and leaves completed sessions at 39.

## Audio

Audio plays only when the learner taps a ▶ button. No screen or step plays a clip on its own.

`tools/generate_audio.py` adds a word clip for the headword of every lemma in `curriculum.json`, keyed by the same `skey`, beside the token clips. A headword the phonetics module rejects fails the run with its name, as token words do now.

▶ Play word on a review card and in the New words step plays the headword (*cen-*). ▶ Play word in a line's word card plays that word as written in the line (*cenuva*).

## Tests

Python, in `build_data.py`'s checks: the four failure conditions under Generation.

Kotlin, `:core:jvmTest`:

- session N reads its skeleton line, and its new words and lessons match `curriculum.json`;
- no question asks about a word taught after the session;
- `resetAll()` leaves no cards, 0 sessions, no streak, no in-progress session;
- a changed `course_version` resets progress; an unchanged one does not;
- after session 39, the reviews-only session holds only due reviews, resumes, updates the streak, and keeps completed sessions at 39; with nothing due, no session is built.

Builds: `./gradlew :core:jvmTest`, `:androidApp:assembleDebug`, `:composeApp:compileKotlinIosSimulatorArm64`. Then each screen on the Android emulator, shown to the user before any commit.

## Docs

SPEC.md: the Milestones table and the Curriculum decision describe this course. README.md: what a session is. BACKLOG.md: this work. `docs/specs/neural-reader-audio.md`: clip counts after regeneration.
