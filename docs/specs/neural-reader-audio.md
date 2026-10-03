# Neural Reader audio

## Problem Statement

Reader pronunciation is eSpeak NG formant synthesis. It is the Microsoft Sam / DECtalk family: intelligible, flat, and mechanical. The learner hears that voice on every word and every line.

The phonetics are already right. A hand-derived Appendix E module turns Quenya spelling into an eSpeak phoneme string with explicit stress, and the known-correct stresses hold for *Eärendil* (-REN-), *andúnë* (-DÚ-), and *ancalima* (-CA-). The player, the manifest, and the clip set are fine. The waveform engine is the defect.

## Solution

Replace the formant waveform with one Azure neural voice, driven by IPA inside SSML, generated at build time and shipped as the same offline clips.

The learner still taps play, still hears audio with no network, and still hears one speaker. That speaker is a warm en-GB woman, unhurried, close and even: mezzo-alto, a little breath on the onsets, softened Received Pronunciation, no announcer lilt. Words are careful and isolated. Lines are one continuous utterance in the same voice, with the module's stress left intact.

Default voice: `en-GB-Ada:DragonHDLatestNeural`. An environment override may select another en-GB female neural voice of the same service when the subscription's region does not offer that HD voice. The approved fallback name is `en-GB-AdaMultilingualNeural`.

## User Stories

1. As a learner, I want a word clip to sound like a person speaking, so that pronunciation practice is bearable for a whole session.
2. As a learner, I want every word and every line in the same voice, so that the course has one speaker.
3. As a learner, I want that speaker to be a warm adult woman with an unhurried British delivery, so that the reading feels like spoken Quenya rather than a GPS prompt.
4. As a learner, I want *cirya* stressed on the first syllable, so that a two-syllable word follows Appendix E.
5. As a learner, I want *Eärendil* stressed on *-ren-*, so that a heavy penult still wins.
6. As a learner, I want *andúnë* stressed on the long *-dú-*, so that vowel length still places stress.
7. As a learner, I want *ancalima* stressed on *-ca-*, so that a light penult still throws stress to the antepenult.
8. As a learner, I want a line played as one phrase, so that I hear the words join instead of a list of dictionary entries.
9. As a learner, I want a question line to finish with a short pause after the last word, so that *man cenuva fána cirya?* does not run into the silence as a statement blob.
10. As a learner, I want a comma in a line to be a short pause, so that the phrasing of the poem survives synthesis.
11. As a learner, I want play to work with the device offline, so that study does not depend on Azure at the moment I tap.
12. As a learner, I want the same play buttons and the same presence rule (a button only when a clip exists), so that the Reader does not change shape.
13. As a learner on Android, I want existing playback to decode the new clips, so that I do not take a player change to hear them.
14. As a learner on iOS, I want the same, so that the shared AAC clips still play.
15. As the person regenerating audio, I want one command to rebuild the clip library from the current phrase file, so that a data regen can be followed by an audio regen.
16. As the person regenerating audio, I want the run to stop immediately when the speech key or region is missing, so that I do not discover an empty library at the end.
17. As the person regenerating audio, I want a failed run to leave the previous clips and the previous manifest in place, so that a bad credential or a 400 does not wipe a working library.
18. As the person regenerating audio, I want an unchanged clip left untouched, so that a second run does not re-bill speech I already committed.
19. As the person regenerating audio, I want a changed pronunciation, voice, or rate to rebuild only the clips whose request changed, so that cache keys track the SSML rather than the filename.
20. As the person regenerating audio, I want a voice-not-found response to name the override and the approved fallback voice, so that I can retarget the region without reading service docs.
21. As the person regenerating audio, I want one probe request to succeed before the corpus starts, so that an illegal phone fails once, not on clip 80.
22. As the person regenerating audio, I want a word the phonetics module cannot transcribe to fail the run and be reported, so that a gap stays visible. This is the current skip-and-exit behavior.
23. As the person regenerating audio, I want the app-facing manifest to keep today's keys (surface-form key for words, phrase id for lines), so that the app keeps finding clips.
24. As the person regenerating audio, I want the speech key only in the environment, so that it never lands in the repo, the clips, or the app.
25. As the person accepting the voice, I want a fixed listen list printed at the end of a successful run, so that I judge timbre with my ears after the tests pass.
26. As a later agent, I want the listen list treated as a human gate, so that I do not declare the voice acceptable because the AAC decodes.

## Implementation Decisions

### Seam

One new pure adapter sits between the phonetics module and the network. Input: the phoneme string the phonetics module already returns, plus the original spelling for the visible word, plus whether the clip is a word or a line. Output: one SSML document. Tests hit that output. The speech client is the only component that sends bytes to Azure. Playback, the app-facing manifest shape, and the phonetics module's stress rules stay as they are.

A line is split on whitespace before phonemicization. Each word is phonemicized alone, then the words are wrapped as sibling `phoneme` elements inside one `speak`. The phonetics module currently drops spaces, so a line fed through it whole becomes one run-on string. The adapter must not do that.

### Engine

- Service: Azure Speech text-to-speech, SSML `POST` to the regional `cognitiveservices/v1` endpoint.
- Auth headers: subscription key and the required user-agent. Output format: a PCM wav the existing AAC encode step already accepts. Container and bitrate stay whatever the generator writes today.
- Required environment: `SPEECH_KEY`, `SPEECH_REGION`.
- Optional environment: `QUENYA_SPEECH_VOICE`. Unset means `en-GB-Ada:DragonHDLatestNeural`.
- `speak` carries `xml:lang="en-GB"`. One `voice`. One `prosody` with `rate="85%"` around the whole utterance, words and lines alike. The word/line difference is isolation versus joining, not two rates.
- Each word is `<phoneme alphabet="ipa" ph="...">spelling</phoneme>`. The `ph` value is the pronunciation. The element text is the Quenya spelling, XML-escaped, for the service's alignment only.
- A comma, semicolon, or colon in the source line becomes a `break` of 200 ms between phoneme elements. A final `.`, `?`, or `!` becomes a `break` of 400 ms after the last word. Other punctuation stays stripped and adds no break.
- Word clips are a single phoneme element and no break.

### IPA, restricted to the en-GB phone set

Azure returns HTTP 400 for a phone outside the voice locale's set. The en-GB set has no cardinal Quenya vowels and no trill. The map below is the approved compromise. Phones are IPA characters, not ASCII lookalikes: primary stress is `ˈ`, length is `ː`.

| eSpeak piece | IPA | Why |
|---|---|---|
| `a` | `ɑ` | Short open vowel. Not in the published en-GB table. See the probe. |
| `a:` | `ɑː` | Legal long open vowel. |
| `e` | `ɛ` | Dress. Legal. |
| `e:` | `ɛ` | No legal `eː`. Length of *é* collapses. |
| `i` | `ɪ` | Kit. No legal short cardinal `i`. |
| `i:` | `iː` | Legal. |
| `o` | `ɒ` | Lot. No legal short cardinal `o`. Do not use the diphthong `əʊ`. |
| `o:` | `ɔː` | Legal long mid-back. |
| `u` | `ʊ` | Foot. No legal short cardinal `u`. |
| `u:` | `uː` | Legal. |
| `ai` | `aɪ` | Legal diphthong. |
| `au` | `aʊ` | Legal diphthong. |
| `oi` | `ɔɪ` | Legal diphthong. |
| `ui` | `ʊɪ` | Two legal phones. |
| `eu` | `ɛʊ` | Two legal phones. |
| `iu` | `ɪʊ` | Two legal phones. |
| `r` | `ɹ` | The en-GB rhotic. Quenya's trill is not in the set. |
| every other consonant the phonetics module emits (`p t k b d g f v s h m n l w j`, including the `j` it already uses for `y` and for palatal digraphs) | itself | Already legal, including `h`+`w` and `k`+`w`. |

Stress: the phonetics module marks exactly one segment with `'`. Move that mark to a syllable-initial `ˈ`.

Syllables: maximal onset. Put `.` between syllables. Attach the stress mark to the start of the stressed syllable, before its onset. Syllable dots must not move which vowel the phonetics module stressed. Consonant clusters that made a penult heavy stay in the stress decision even when the second consonant becomes the next onset. *Eärendil* is still stressed on *-ren-* when written `ɛ.ɑ.ˈɹɛn.dɪl`.

Fixtures the adapter must produce exactly:

| Spelling | `ph` |
|---|---|
| cirya | `ˈkɪɹ.jɑ` |
| Eärendil | `ɛ.ɑ.ˈɹɛn.dɪl` |
| andúnë | `ɑn.ˈduː.nɛ` |
| ancalima | `ɑn.ˈkɑ.lɪ.mɑ` |

Probe, before any corpus request: synthesize *cirya* with this map. A 200 and a non-empty wav means the short `ɑ` is accepted; continue. A 400 whose body points at an illegal phone means both short `a` and long `á` become `ɑː`, the four fixtures lose the `a`/`á` length contrast, and the probe is repeated once. A second failure stops the run. No other phone is substituted.

`r` stays `ɹ` even if a trill would be nicer. A 400 on `ɹ` stops the run rather than guessing another rhotic.

### Library behavior

- Inventory stays the generator's current one: every unique token surface in the phrase file, and every phrase line.
- Filename rule stays content-hash of the lookup key, word clips and line clips distinguished as they are now.
- Cache record is generator-local. It stores, per lookup key, the hash of voice name, rate, output format, and the exact SSML. The app never reads it. A matching hash skips synthesis. A different hash replaces that clip.
- The app-facing manifest still has only the word map and the line map. Write it after every requested clip is present. A failure writes neither a partial manifest nor a partial deletion of the previous set.
- Check key and region, then run the probe, then touch the library. Verify each new wav decodes before replacing the previous AAC for that key.
- A phonetics rejection is still a failed run with the offending spelling listed. No silent omission.

### What the agent runs

Stdlib unit tests for the adapter. No new test framework. No Gradle run for this change, because the app and the player are untouched. A full generation against Azure is a manual command the human runs after the key is in the environment. The agent does not invent a key.

## Testing Decisions

A good test asserts the SSML and the IPA string. It does not open a socket, does not snapshot a waveform, and does not listen. Neural audio is not sample-stable; the committed clips are the reproducible artifact.

Test the adapter, at the seam above:

- The four fixture spellings produce the four `ph` values.
- A two-word line produces one `speak`, one `voice` (the default name when none is passed), one `prosody` at `85%`, and two `phoneme` elements in order, with a space-separated utterance and no `break`.
- A line ending in `?` has a 400 ms break after the last phoneme and nowhere else.
- A comma between words has a 200 ms break between those phoneme elements.
- Spellings that contain `&`, `<`, or `>` appear escaped in the element text and do not appear raw inside `ph`.
- The stress vowel of each fixture is the vowel the phonetics module marks, read back from the `ˈ` position.
- Passing an explicit voice name puts that name on the `voice` element.

Prior art: the phonetics module has a manual `__main__` check and no test suite. The core module's Kotlin tests are the wrong seam; they do not generate audio. New tests live beside the adapter and run with Python's standard library.

Done for the agent means: those tests pass, a generation dry of the network can still print the SSML for the listen list, and the runbook at the bottom of this spec is accurate. Done for the voice means the human has heard the listen list. The agent stops before that gate and says so.

Listen list, printed as file paths at the end of a real generation:

- cirya
- Eärendil
- andúnë
- ancalima
- the line *aiya Eärendil elenion ancalima*

## Out of Scope

- Any change to playback, the play buttons, or the app-facing manifest fields.
- On-device synthesis.
- A local neural vocoder (Piper, Kokoro, Matcha).
- ElevenLabs, OpenAI, or any engine that pronounces Quenya from spelling.
- A recorded human session.
- Cloning a performer's voice, including Liv Tyler, or conditioning on film audio.
- Geminate length (`ll`, `nn`) beyond the doubled consonant the phonetics module already emits.
- Reinterpreting `hl` / `hr` as voiceless liquids. They stay `h` plus `l` or `r`, then `h` plus `ɹ`.
- Teaching Azure the tengwar or the Quenya alphabet. Orthography is display text inside `phoneme`.
- Editing `BACKLOG.md` item 7's history. A one-line pointer from the README's audio step to this spec is in scope, because that step still tells the next agent to install eSpeak as the synthesizer.

## Further Notes

eSpeak remains the phoneme alphabet the phonetics module speaks. It stops being the waveform. Removing the `espeak-ng` binary from the regen instructions is correct once nothing shells out to it. `ffmpeg` stays.

The collapsed sounds are deliberate, caused by the en-GB phone set: short and long *e* match, *r* is an English approximant, short *i* / *o* / *u* are the lax English vowels, short *a* depends on the probe. A later voice locale with cardinal vowels can replace the map without a player change. That locale is not this spec.

Rejected alternatives, so they are not rebuilt as improvements: local phoneme-to-neural (pleasant, not this timbre, and a training-data problem), a single human recording (the quality ceiling once the lines stop changing), on-device models (worth it only when the learner types arbitrary Quenya).

The speech key is a user secret. The spec does not name a file to put it in. The generator reads the process environment.

## Runbook

From `claudeslop/quenya-app`:

```bash
python -m unittest discover -s tools -p 'test_*.py'
python tools/generate_audio.py --dry-ssml
SPEECH_KEY=... SPEECH_REGION=... python tools/generate_audio.py
```

`--dry-ssml` prints the listen-list SSML and does not read the key, contact Azure, or write clips. A real run requires `SPEECH_KEY` and `SPEECH_REGION`. Set `QUENYA_SPEECH_VOICE` only to replace the default; the approved fallback is `en-GB-AdaMultilingualNeural`. `ffmpeg` stays a requirement. `espeak-ng` is not.

After a real run the command prints the listen-list paths and stops. Hearing them is the human's step. A voice-not-found response names `QUENYA_SPEECH_VOICE` and the fallback voice.

When this generator no longer shells out to eSpeak, the README regen step should point here and drop `espeak-ng` from the install list.
