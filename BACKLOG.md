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
SQLDelight 2.3.2). No version changes were needed. Reader audio has been confirmed on an emulator.
A physical handset, the Xcode wrapper, and a hosted CI run are listed under smaller items.

## 4. Eldamo marks
Words carry a raw `mark` (`#`, `†`, `-`, `|`, `*`, `^`). The app shows them unchanged and does not
interpret them. Read Eldamo's key, then map them onto `attested` / `reconstructed` / `neologism`.

## 5. Unresolved and heuristic tokens — mostly done
Was 4 unresolved + 3 ambiguous. Two different fixes in `tools/build_data.py`:
- `split_tokens` now also splits on internal hyphens, so a hyphenated compound Eldamo tags as two
  `<element>`s (e.g. `ëar-celumessen` = `ëar` + `celumë` locative-plural, line 8 — inside the core
  12-line milestone) lines up position-for-position instead of being discarded as a whole-string
  lookup miss. Fixed `ëar-celumessen` (line 8) and, as a side effect, `talta-taltala`'s reduplicated
  `talta-`/`talta-` element pair (line 29, stretch-only).
- `resolve_token`'s homonym tie-break now checks attestation dominance (≥5×), not just uniqueness:
  `cirya¹` "ship" (55 attestations) vs. `cirya²` "cleft, pass" (1) is a confident pick, not a guess.
  Fixed all 3 `cirya` occurrences (lines 1, 6, 31 — two of them core).

Remaining: `ondolissë mornë` (line 32, stretch-only). Eldamo gives **zero** `<element>` analysis for
this line — no structural fix applies; needs a hand-authored override (`ondo` + partitive-plural +
locative, `morna` + plural, per the line's prose "Decomposition" note) if the full poem is ever
taught. Low priority: outside the core-12 milestone, two words, already have a `guess` field in the
UI. The core milestone (session 35) itself is now fully resolved, zero ambiguous.

## 6. Rule-derived forms
Only 727 late-Quenya words have attested inflected forms, and some features have tiny pools
(1st-pl-inclusive possessive: 2). Add a paradigm engine that derives missing forms, tagged
`reconstructed`, and drop any exercise it cannot derive reliably.

## 7. Generated audio in the Reader  *(requested)* — done
Tap-to-play pronunciation for Reader phrases and words. Doesn't touch session/curriculum math, so it
ranks low on "changes the plan" — but it's its own item because of the data-pipeline work involved.

Spike findings (both halves checked before committing to the build):
- **Eldamo's phonetic data is not a usable shortcut.** `phoneme` (810 entries), `phonetic-group`
  (266), `phonetic-rule` (878) — the categories `build_data.py:22-23` excludes — turned out to be
  Eldamo's historical/etymological apparatus: proto-sound changes across the whole Eldarin family
  over millennia (e.g. `[ɣ] became [h] after voiceless consonants`), not a synchronic "how is
  written Quenya pronounced" table. The one entry that might have held that, Quenya's own
  `phonetics` essay (`speech=phonetics`, `l=q`), is an empty stub — citation refs, no text.
  **Conclusion: the spelling→phoneme/stress ruleset has to be hand-derived from Appendix E**, same
  status as `skeleton.json` — the one legitimately hand-authored input in an otherwise generated
  pipeline, not mined from Eldamo.
- **eSpeak NG works for this.** Installed (`brew install espeak-ng`, 1.52.0, 26MB) and confirmed it
  accepts raw phoneme input directly (`espeak-ng -x "[[k'i:rja]]" -w out.wav`), so it doesn't need to
  "support Quenya" as a language — it's driven purely as a phoneme-to-waveform engine, with every
  transcription rule supplied by us. Produced a real ~32KB WAV for *cirya* ("ship"). Confirmed
  explicit control over stress placement via eSpeak's `'` marker, which matters because Quenya
  stress (never on the final syllable) differs from eSpeak's own default heuristic — must be applied
  explicitly per word, not left to eSpeak to guess. Opus encoding tooling (`ffmpeg`/`opusenc`) isn't
  installed yet but is a standard, low-risk step, not spiked further.

Implementation:
- `tools/quenya_phonetics.py`: Quenya spelling → eSpeak phoneme string with explicit stress
  (penultimate if heavy/long, else antepenultimate, never final). Validated against known-correct
  stress in *Eärendil* (-REN-), *andúnë* (-DÚ-) and *ancalima* (-CA-) before trusting it on the real
  84-word Reader vocabulary. That full-corpus run caught two real bugs a small hand-picked test set
  would have missed: `x` (= /ks/ in Quenya) wasn't in the consonant table and was silently dropped
  (`axor` → `aor`), and `ry` was wrongly treated as one palatalized digraph like `ty`/`hy`/`ny`/`ly` —
  it isn't; Appendix E doesn't list it, and *cirya* is `cir-ya`, not a palatal r. Both fixed; the
  corpus now round-trips with zero errors (an unrecognised letter now raises loudly, not skips).
- **Opus → AAC, a correction to the original plan.** Android's `MediaPlayer` decodes Opus natively
  (API 21+), but iOS's `AVAudioPlayer` does not support raw/Ogg Opus without a `.caf` repackage or a
  third-party decoder. Switched to AAC/`.m4a`, which both platforms play natively with zero extra
  libraries. Cost is small given the content is already tiny: 123 clips (84 words + 39 lines) at
  519 KiB total, vs. 333 KiB the one time it was tried with Opus.
- `tools/generate_audio.py`: espeak-ng → ffmpeg(AAC) per clip, manifest `audio.json` (`words`/`lines`,
  keyed by the same `skey()` used elsewhere) into `composeResources/files/audio/`.
- Kotlin: `AudioManifest` model + `DataLoader`/`CourseData.audioForWord`/`audioForLine`; `AudioPlayer`
  interface in `composeApp` commonMain with `AndroidAudioPlayer` (writes to a cache file, `MediaPlayer`)
  and `IosAudioPlayer` (`NSData.create` + `AVAudioPlayer`, copies the bytes so it outlives `memScoped`).
  Constructed once per platform entry point (`MainActivity.kt`, `IosEntry.kt`) and threaded through
  `AppModel`, same pattern as the SQLDelight driver.
  UI: a "▶ Play line" button on `PhraseView` and a "▶ Play word" button on `TokenDetail`, both reading
  the clip via `Res.readBytes` and only showing when a clip exists for that text.
- Verified mechanically: `core:jvmTest`, `androidApp:assembleDebug`, and
  `composeApp:compileKotlinIosSimulatorArm64` all pass; generated clips decode and play via `afplay`.
  Confirmed on-device (emulator) by the user: plays correctly, sounds like classic eSpeak formant
  synthesis ("Microsoft Sam"-ish) — expected for a phoneme-driven synthesizer, not a defect. The one
  bug found during device testing was environmental, not code: a long-running emulator session had a
  stuck audio backend producing total silence with zero app-level errors; a plain relaunch fixed it.
  Confirmed by pulling the exact clip file the app wrote to its cache dir mid-session and replaying it
  independently — valid audio throughout, so the Kotlin pipeline was never the problem.
- Inherited item 5's gaps, now mostly moot: the core-12 milestone has zero unresolved/ambiguous
  tokens as of item 5's fix, so audio for that content has clean lemma data to work from. Only
  `ondolissë`/`mornë` (line 32, stretch-only) still can't be transcribed, same as item 5 left it.

**Follow-up — eSpeak replaced with Piper, done.** The formant voice above was always meant to be
temporary (see `docs/specs/neural-reader-audio.md`). An Azure neural-voice draft was written and
then dropped — no cloud account/key for a personal study build — in favor of a local Piper voice
(`en_GB-cori-high`, VITS/ONNX, GPL-3.0, a build tool only, nothing linked into the app). Implemented
by Grok on worktree `piper-race` (adapter `tools/piper_utterance.py`, `generate_audio.py` rewrite),
while an independent Claude implementation on worktree `piper-claude` reached the same design (same
syllabification/stress rule, same `en_GB-cori-high` choice) but lost the race and was kept aside,
uncommitted, rather than merged. Merged to `main` as `010a3d9` (generator/adapter/tests/spec/README)
+ `f4e6a75` (the 123 regenerated clips). A shared venv (`claudeslop/.venv-piper`) and voice-model
cache (`claudeslop/.piper-voices`, env-overridable via `PIPER_VOICES_DIR`) outside both worktrees
meant the ~114MB voice model and the `piper-tts` install only happened once between the two agents.
Clips are now ~1.0 MiB total for the same 123 keys (up from ~520 KiB — `length_scale=1.15` makes
every clip ~15% longer, plus the neural waveform compresses slightly less than formant synthesis at
the same bitrate). Listened to and accepted by the user.

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
- Install and run on a physical handset. Emulator playback is already confirmed.
- Run the GitHub Actions workflow on a hosted runner. It calls `./gradlew`. It has not been executed on a hosted runner.
- Read the "Decomposition" notes of phrase entries to improve token analysis.
- Lesson summaries that ended on a colon. — done. `summarize()` drops the sentence that only introduces the next example, and `check()` rejects a summary that still ends on `:`.
- Streak day is local. — done. `finishSession` adds `utcOffsetMs` (from the platform clock) to `nowMs` before dividing by `DAY_MS`. Review intervals stay absolute milliseconds.
- Optional: FSRS interval fuzzing and parameter fitting from your own review history.
- Consonant gemination (`ll`, `nn`, …) is a doubled consonant in `tools/quenya_phonetics.py`. Model true length if a listen shows the simplification matters.
- Custom Elvish-themed launcher icon. — done, `a48db63`.
- Reader screen: visual indicator (fade/arrow) showing there's more content below the fold.
- Lesson "Read full entry" font. — done. The body uses the same default style as the summary.
- More than one learner. Review state is a single SQLDelight store; there is no profile switch.
