# Backlog

Ordered roughly by how much each item could change the plan.

## 1. Measure noun-class dependence of the endings  *(requested)*
Quenya case and plural endings can differ by noun class (vocalic vs consonantal and so on).
If they do, every case unit multiplies in size and the session estimates are too low.
- Eldamo has 500 `<class>` elements and `<inflect-table form=… from="inflect">` tables that group
  forms by class. Count, for each milestone feature, how many distinct ending patterns appear.
- Re-run the curriculum model with one sub-session per extra pattern and compare against session 40.
- Likely outcome to check: `markirya-12` currently lands at session 35, so there are only 5 spare sessions.

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
