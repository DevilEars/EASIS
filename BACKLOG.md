# Backlog

Ordered roughly by how much each item could change the plan.

## 1. Measure noun-class dependence of the endings  *(requested)* — done, no plan change
`tools/eldamo_report.py` now has a `noun_class_report` (BACKLOG item 4 in its own docstring) that
groups every attested case/plural ending by the lemma's stem class (vocalic vs which consonant).
Measured from the 1,186 late-Quenya nouns' actual attested `<ref><inflect>` forms, not from
Eldamo's `<inflect-table>`/`<class>` elements — those 500/411 elements turned out to describe other
languages' and speeches' declension tables (Sindarin-looking `strong-I`/`weak-II gendered` classes
among them), not Quenya noun paradigms, so they weren't the right evidence source after all.

Finding: only **plural** genuinely splits by stem class (vocalic nouns take mostly `-r`, ~371
attested forms, vs. consonantal nouns taking `-i` with a leading consonant repeated, ~143 forms).
The other five case features (genitive, allative, ablative, locative, instrumental) show the same
suffix across stem classes, give or take a phonological epenthetic vowel (`-nna`/`-na`,
`-llo`/`-ello`) too minor to need separate teaching. Locative's 17 attested forms are thin and
scattered — worth rechecking if Eldamo adds more late-Quenya locative citations.

The plural split isn't a gap, either: Eldamo's own "plural nouns" grammar entry — the entry the
`plural` lesson is generated from — opens by stating the rule directly ("Quenya has two general
plural suffixes: -i used after consonantal nouns and -r used after (most) vocalic nouns"), and
`Exercises.kt`'s `formChoice` already samples attested forms per feature at random across all
lemmas, so a plural-practice session draws from both patterns' pools without any code change.
**No curriculum restructuring needed** — `skeleton.json` and the 49-session plan stand as-is.

## 2. Calibrate the session model with real learning
`tools/skeleton.json` assumes 2 sessions per core feature, 1 per minor feature, 6 new words per session.
These are guesses. After about 10 real sessions, adjust and regenerate (`python tools/build_data.py`).

## 3. Verify the build on a real machine — done
`gradle :androidApp:assembleDebug` and `gradle :composeApp:compileKotlinIosSimulatorArm64` both succeed
with the versions already in `gradle/libs.versions.toml` (Kotlin 2.4.10 / AGP 9.1.0 / Compose 1.12.1 /
SQLDelight 2.3.2). No version changes were needed. Remaining: install/run on an actual device or
simulator, and add the iOS Xcode wrapper (item 10) so the iOS target can launch.

## 4. Eldamo marks
Words carry a raw `mark` (`#`, `†`, `-`, `|`, `*`, `^`). The app shows them unchanged and does not
interpret them. Read Eldamo's key, then map them onto `attested` / `reconstructed` / `neologism`.

## 5. Unresolved and heuristic tokens
Eldamo has no token analysis for 8 of the 37 *Markirya* lines, so those tokens are matched by strict
lookup (`resolution` field). 4 tokens stay unresolved (`ëar-celumessen`, `talta-taltala`,
`ondolissë`, `mornë`); `cirya` is ambiguous between homonyms. Options: split hyphenated compounds,
add an override file, or read Eldamo's phrase notes ("Decomposition").

## 6. Rule-derived forms
Only 727 late-Quenya words have attested inflected forms, and some features have tiny pools
(1st-pl-inclusive possessive: 2). Add a paradigm engine that derives missing forms, tagged
`reconstructed`, and drop any exercise it cannot derive reliably.

## 7. Generated audio in the Reader  *(requested)*
Tap-to-play pronunciation for Reader phrases and words. Doesn't touch session/curriculum math, so it
ranks low on "changes the plan" — but it's its own item because of the data-pipeline work involved.
- Generate at build time, like everything else (`tools/build_data.py`), not with an on-device synthesis
  engine — scope to curriculum content only (lesson examples, *Markirya* lines), not the full lexicon.
  That scoping plus a synthesized (not recorded) voice is where the space saving comes from.
- Before writing an IPA ruleset from Appendix E by hand: `tools/build_data.py:22-23` currently excludes
  Eldamo POS categories `phoneme`, `phonetic-group`, `phonetic-rule`, `phonetics` — check whether Eldamo
  already encodes the sound rules needed, instead of re-deriving them.
- Feed the ruleset to a small rule-based synthesizer (e.g. eSpeak NG) offline, encode low-bitrate Opus,
  write clips + a manifest (`audio.json`, id → clip) into `composeResources/files/audio/`, parsed by
  `DataLoader` like the other JSON.
- UI: a play button in `PhraseView` and `TokenDetail` (`App.kt`), backed by a thin KMP `expect/actual`
  player (Android `MediaPlayer` / iOS `AVAudioPlayer`).
- Inherits item 5's gaps: tokens with no resolved lemma can't be transcribed, so they can't get audio
  either, until that's fixed.

## 8. Data editing on the phone
Export / import one bundle file through the system file picker, so lexicon edits need no rebuild.

## 9. Other periods and neo-Elvish
Eldamo also has middle/early Quenya (`mq`, `eq`) and a neo-Quenya layer (`nq`). Add them as labelled
layers (`neologism`) once the core course works.

## 10. Afrikaans glosses
Eldamo ships English, Russian and Polish glosses only. `gloss` is already `{ "en": … }`. A machine
translation pass could add `af`, tagged as machine-translated and reviewable in a file.

## 11. Smaller items
- iOS: add the Xcode wrapper project that calls `MainViewController()`.
- Read the "Decomposition" notes of phrase entries to improve token analysis.
- Lesson summaries: spot-check all 18 for sentences cut at a colon (e.g. genitive).
- Streak uses UTC days; switch to local time.
- Optional: FSRS interval fuzzing and parameter fitting from your own review history.
