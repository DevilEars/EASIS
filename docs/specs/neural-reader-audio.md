# Neural Reader audio

## Problem Statement

Reader pronunciation is eSpeak NG formant synthesis. It is the Microsoft Sam / DECtalk family: intelligible, flat, and mechanical. The learner hears that voice on every word and every line.

The phonetics are already right. A hand-derived Appendix E module turns Quenya spelling into an eSpeak phoneme string with explicit stress, and the known-correct stresses hold for *Eärendil* (-REN-), *andúnë* (-DÚ-), and *ancalima* (-CA-). The player, the manifest, and the clip set are fine. The waveform engine is the defect.

A cloud neural voice is not the remedy. There is no speech subscription and there will be no token spend.

## Solution

Replace the formant waveform with one local Piper voice, generated on the machine that builds the clips and shipped as the same offline AAC files.

The learner still taps play, still hears audio with no network, and still hears one speaker. That speaker is a British woman, a little slower than the voice's own pace. Words are careful and isolated. Lines are one utterance in the same voice, with the module's stress left intact and a breath where the line has a comma or a final question mark.

The default voice is `en_GB-cori-high`, the only high-quality single-speaker en-GB voice in the official Piper catalog. An environment override may select another catalog voice. The same-speaker smaller model is `en_GB-cori-medium`.

Piper never sees Quenya spelling. The phonetics module stays the pronunciation authority. Its ASCII phoneme string is translated into the IPA characters this voice was trained on, then handed to Piper as a raw phoneme block.

## User Stories

1. As a learner, I want a word clip to sound like a person speaking, so that pronunciation practice is bearable for a whole session.
2. As a learner, I want every word and every line in the same voice, so that the course has one speaker.
3. As a learner, I want that speaker to be an adult British woman, speaking a little slowly, so that the reading feels like spoken Quenya rather than a GPS prompt.
4. As a learner, I want *cirya* stressed on the first syllable, so that a two-syllable word follows Appendix E.
5. As a learner, I want *Eärendil* stressed on *-ren-*, so that a heavy penult still wins.
6. As a learner, I want *andúnë* stressed on the long *-dú-*, so that vowel length still places stress.
7. As a learner, I want *ancalima* stressed on *-ca-*, so that a light penult still throws stress to the antepenult.
8. As a learner, I want *né* to keep a long *é*, so that vowel length is still audible.
9. As a learner, I want a line played as one phrase, so that I hear the words join instead of a list of dictionary entries.
10. As a learner, I want a question line to finish as a question, so that *man cenuva fána cirya?* does not run into the silence as a statement blob.
11. As a learner, I want a comma in a line to be a short pause, so that the phrasing of the poem survives synthesis.
12. As a learner, I want play to work with the device offline, so that study does not depend on a network at the moment I tap.
13. As a learner, I want the same play buttons and the same presence rule (a button only when a clip exists), so that the Reader does not change shape.
14. As a learner on Android, I want existing playback to decode the new clips, so that I do not take a player change to hear them.
15. As a learner on iOS, I want the same, so that the shared AAC clips still play.
16. As the person regenerating audio, I want one command and no account, key, or region, so that a data regen can be followed by an audio regen.
17. As the person regenerating audio, I want a Python that is not the shared virtual environment to stop immediately and name `source` of that environment, and a missing Piper install there to name `pip install piper-tts`, so that I do not discover the gap at clip 80.
18. As the person regenerating audio, I want a missing voice file to be fetched into the shared voice directory before any clip is touched, and a failed fetch to stop with the download command, so that a half-written library is not how I learn the voice is absent. Both worktrees share that directory, so the download happens once.
19. As the person regenerating audio, I want a failed run to leave the previous clips and the previous manifest in place, so that a bad voice or a bad phone does not wipe a working library.
20. As the person regenerating audio, I want an unchanged clip left untouched, so that a second run does not rebuild speech I already committed.
21. As the person regenerating audio, I want a changed pronunciation, voice, or pace to rebuild only the clips whose request changed, so that the cache tracks the utterance rather than the filename.
22. As the person regenerating audio, I want one probe to succeed before the corpus starts, so that a phone the voice cannot speak fails once, not on clip 80.
23. As the person regenerating audio, I want a word the phonetics module cannot transcribe to fail the run and be reported, so that a gap stays visible.
24. As the person regenerating audio, I want the app-facing manifest to keep today's keys (surface-form key for words, phrase id for lines), so that the app keeps finding clips.
25. As the person regenerating audio, I want no speech secret anywhere in the run, so that there is nothing to leak into the repo, the clips, or the app.
26. As the person regenerating audio, I want to point the run at another catalog voice with `QUENYA_PIPER_VOICE`, so that I can listen to Cori medium, Jenny Dioco, or Alba without a code change.
27. As the person regenerating audio, I want the pace to default to a length scale of 1.15, and to `QUENYA_PIPER_LENGTH` when I set it, so that words and lines share one unhurried tempo.
28. As the person regenerating audio, I want a dry run to print the listen-list utterances and do nothing else, so that I can read the pronunciation before a model loads.
29. As the person accepting the voice, I want a fixed listen list printed at the end of a successful run, so that I judge timbre with my ears after the tests pass.
30. As a later agent, I want the listen list treated as a human gate, so that I do not declare the voice acceptable because the AAC decodes.

## Implementation Decisions

### Seam

One pure adapter sits between the phonetics module and Piper. Input: the phoneme string the phonetics module already returns, plus the original spelling when the clip is a line so punctuation can be seen. Output: one Piper utterance. Tests hit that string. The speech client is the only component that loads the ONNX model. Playback, the app-facing manifest shape, and the phonetics module's stress rules stay as they are.

A line is split on whitespace before phonemicization. Each word is phonemicized alone. The phonetics module drops spaces, so a line fed through it whole becomes one run-on string with one stress mark. The adapter must not do that.

### What Piper is given

Piper's `[[ ... ]]` block is raw phoneme characters. Each character inside the brackets is one phoneme id. Piper does not run its text frontend on that span. A character missing from the loaded voice's phoneme map is skipped by Piper with a log line. This generator treats a missing character as a failed run and names the character, so a clip cannot be silently short a phone.

The English voices were trained on espeak IPA (`ɑ`, `ɛ`, `ɪ`, `ˈ`, `ː`, and the rest of that inventory). The phonetics module's string is a different alphabet: ASCII letters, an apostrophe for stress, a colon for length. That ASCII string is not a Piper utterance. `[[ear'endil]]` would be looked up as the ASCII phones, including the apostrophe, and would not be the stress and vowels the voice learned.

The period character is the punctuation phone this model learned at the ends of sentences. A dot between syllables is that same character. Syllable dots are not emitted. Stress is the character `ˈ`, placed before the onset of the stressed syllable.

Syllables, so the stress mark lands on the vowel the phonetics module chose:

- Consonants before the first vowel open the first syllable.
- Between two vowels, the last consonant opens the next syllable. Any consonants before it close the current syllable. That is what keeps *cirya* as kir-ya and *Eärendil* stressed on *-ren-* once the *r* has moved to the onset.
- Consonants after the last vowel close the last syllable.
- The cluster that made a penult heavy still counts for the stress decision already made by the phonetics module. Moving the second consonant into the next onset does not move which vowel is stressed.

### Phone map

| eSpeak piece | Piper characters | Why |
|---|---|---|
| `a` / `a:` | `ɑ` / `ɑː` | Open vowel, short and long. |
| `e` / `e:` | `ɛ` / `ɛː` | *é* keeps its length. Collapsing it was an Azure phone-set limit. |
| `i` / `i:` | `ɪ` / `iː` | |
| `o` / `o:` | `ɒ` / `ɔː` | Short *o* is LOT. It is not the diphthong `əʊ`. |
| `u` / `u:` | `ʊ` / `uː` | |
| `ai` `au` `oi` `ui` `eu` `iu` | `aɪ` `aʊ` `ɔɪ` `ʊɪ` `ɛʊ` `ɪʊ` | Two characters each. Piper's bracket splitter does not join multi-character diphthong keys. |
| `r` | `ɹ` | This voice learned the English approximant. |
| `p t k b d g f v s h m n l w j` | themselves | Includes `j` for *y* and for the palatal digraphs the phonetics module already emits. `qu` arrives as `k` then `w`. |
| stress `'` | `ˈ` before the stressed syllable's onset | One mark. The vowel it covers is the vowel the phonetics module marked. |

Word clip: one block, statement mark inside the brackets.

| Spelling | Utterance |
|---|---|
| cirya | `[[ˈkɪɹjɑ.]]` |
| Eärendil | `[[ɛɑˈɹɛndɪl.]]` |
| andúnë | `[[ɑnˈduːnɛ.]]` |
| ancalima | `[[ɑnˈkɑlɪmɑ.]]` |
| né | `[[ˈnɛː.]]` |

Line clip: one block. Words separated by a space. A comma, semicolon, or colon on a word becomes a comma character and a space before the next word. A final `.`, `?`, or `!` is that character at the end of the block. A line with no final mark ends in `.`. There is no millisecond break and no silence spliced into the wav. Piper's pause is the punctuation phone it was trained on.

### Engine

- Package: `piper-tts` (OHF-Voice piper1-gpl). One `PiperVoice` loaded for the whole run. The CLI reloads the model per process; 123 clips share the loaded model.
- Default voice name: `en_GB-cori-high`. Override: `QUENYA_PIPER_VOICE`.
- Pace: `SynthesisConfig.length_scale` of `1.15` for words and lines. `1.0` is the voice's own pace; above 1 is slower. Override: `QUENYA_PIPER_LENGTH`.
- Python for every run, including the dry run and the unit tests, is the shared virtual environment `claudeslop/.venv-piper`. It sits outside both worktrees. `piper-tts` is installed there once. A run whose interpreter is not that environment stops and prints `source` of its `bin/activate`.
- Voice files: the `.onnx` and its `.onnx.json`, in `claudeslop/.piper-voices`, also outside both worktrees. Override: `PIPER_VOICES_DIR`. Absent files are downloaded with `python -m piper.download_voices <name> --data-dir <that directory>` before the library is touched. A file that is already present and non-empty is not downloaded again. The ONNX file is not committed. Piper is GPL-3.0 and stays a build tool. The app ships AAC only.
- Output of Piper is a PCM wav at the rate in the voice JSON. The existing ffmpeg AAC step stays: 32 kbps `.m4a`. The sample rate is not hardcoded.
- Other single-speaker female catalog names a later listen may try: `en_GB-jenny_dioco-medium`, `en_GB-alba-medium`. `en_GB-vctk-medium` and `en_GB-semaine-medium` are not defaults. VCTK is 109 numbered speakers. Semaine is four acted characters.

### Library behavior

- Inventory stays the generator's current one: every unique token surface in the phrase file, and every phrase line.
- Filename rule stays: content hash of the lookup key, word clips and line clips distinguished as they are now.
- Cache record stays inside the worktree that owns the clips, in gitignored `tools/.piper`. It does not live next to the shared voice files, so one worktree cannot skip synthesis on the strength of the other worktree's record. It stores, per filename, the voice name, the length scale, and the exact utterance. The app never reads it. A matching record whose clip is present skips synthesis. A different record replaces that clip.
- The app-facing manifest still has only the word map and the line map. Write it after every requested clip is present. A failure writes neither a partial manifest nor a deletion of the previous set. New wavs are encoded beside the library and replace a clip only after ffmpeg can read them. A phonetics error is reported before any download and before any clip is replaced.
- Check ffmpeg and the Piper install, resolve the voice, load it, then probe, then touch the library. The probe checks that every character inside the four fixture utterances *cirya*, *Eärendil*, *andúnë*, and *ancalima* is in the loaded phoneme map, and that the space, comma, period, question mark, and exclamation mark are too. It then synthesizes *cirya* and confirms the wav decodes. A miss stops the run and names the character or the voice.
- A phonetics rejection is still a failed run with the offending spelling listed. No silent omission.

### What the agent runs

Stdlib unit tests for the adapter. No new test framework. No Gradle run for this change, because the app and the player are untouched. A full generation is a manual command the human runs. It downloads about 114 MB on first use. The agent does not need a network to prove the utterances.

## Testing Decisions

A good test asserts the utterance string. It does not load ONNX, does not snapshot a waveform, and does not listen. Neural audio is not sample-stable. The committed clips are the reproducible artifact, and they stay the previous eSpeak set until a human runs generation and listens.

Test the adapter, at the seam above:

- The five spellings in the table produce those five utterances.
- A two-word line with no punctuation is one block, the two words separated by a single space, ending in `.`
- A comma between words is a comma character plus a space, and no other pause mark. A semicolon or a colon in that position is the same comma-and-space.
- A line ending in `?` ends in `?` and contains no `.`
- The stressed vowel of each fixture is the vowel the phonetics module marks, read back from the vowel that follows `ˈ` once its onset consonants are skipped. *né* reads back as `ɛː`.

Prior art: the phonetics module has a manual check and no test suite. The core module's Kotlin tests do not generate audio. New tests live beside the adapter and run with Python's standard library.

Done for the agent means those tests pass, and a dry run prints the listen-list utterances without importing Piper or writing clips. Done for the voice means the human has heard the listen list. The agent stops before that gate and says so.

Listen list, printed as utterances by the dry run and as file paths at the end of a real generation:

- cirya
- Eärendil
- andúnë
- ancalima
- the line *aiya Eärendil elenion ancalima*

## Out of Scope

- Any change to playback, the play buttons, or the app-facing manifest fields.
- On-device synthesis.
- Azure, ElevenLabs, OpenAI, or any cloud engine. No speech key, no region, no token.
- Kokoro, Matcha, or a voice trained for Quenya.
- Letting Piper's text frontend see Quenya spelling.
- A recorded human session.
- Cloning a performer's voice, or conditioning on film audio.
- Reinterpreting `hl` / `hr` as voiceless liquids. They stay `h` plus `l` or `r`, then `h` plus `ɹ`.
- Committing the ONNX file, or declaring Cori acceptable because a wav decodes. Cori is a neural British woman. She is the voice to listen to. She is not a substitute claim for a studio recording.

## Further Notes

eSpeak remains the phoneme alphabet the phonetics module emits. Piper is the waveform. The clips in the app are the Piper AAC set (`en_GB-cori-high`). A geminate consonant is the doubled consonant that module already emits. ffmpeg stays.

The English approximant for *r*, and the lax English vowels for short *i*, *o*, and *u*, are deliberate. They are the phones this voice can actually say. A trill is in the phoneme map and was not what the model learned.

Rejected so they are not rebuilt as improvements: a cloud HD voice, spelling-driven TTS, on-device models, and a celebrity clone.

## Runbook

From the app directory, after this spec is implemented:

```bash
source /Users/devilliers.neethling/code/persoonlik/Quenya/claudeslop/.venv-piper/bin/activate
python -m unittest discover -s tools -p 'test_*.py'
python tools/generate_audio.py --dry-utterance
python tools/generate_audio.py
```

`piper-tts` is already installed in that environment. If the import fails, `pip install piper-tts` while the environment is active. `--dry-utterance` prints the listen-list utterances. It does not import Piper, read a model, or write clips. A real run uses `en_GB-cori-high` unless `QUENYA_PIPER_VOICE` is set, length scale `1.15` unless `QUENYA_PIPER_LENGTH` is set, and voice files in `claudeslop/.piper-voices` unless `PIPER_VOICES_DIR` is set. ffmpeg stays a requirement. The generator does not shell out to `espeak-ng`.
