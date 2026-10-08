# Reader audio

Reader clips are AAC files built on the machine that generates the course data and shipped with the app. The phone plays those files. `tools/generate_audio.py` is the synthesis step: Quenya spelling goes through `tools/quenya_phonetics.py` and `tools/piper_utterance.py`, then Piper, then ffmpeg.

The voice is Piper `en_GB-cori-high` at length scale 1.15. A word is one utterance. A line is one utterance in the same voice, with that module's stress, a comma where the line has a comma, semicolon, or colon, and `.`, `?`, or `!` at the end.

The library shipped with the current data is 126 word clips and 39 line clips, 979.2 KiB. A later regeneration prints its own counts.

## Playback

`composeResources/files/audio.json` is an `AudioManifest`. `words` maps `Norm.skey` of a token's text to a filename. `lines` maps a phrase id to a filename. `CourseData.audioForWord` and `audioForLine` are those lookups. `DataLoader` reads the file with the other course JSON.

`PhraseView` is both the Reader and the session Reading step. It shows **▶ Play line** when the line has a clip, and **▶ Play word** on the open token when that word has a clip. The button reads `files/audio/<filename>` and calls `AudioPlayer.play`. A new call replaces the clip already playing.

Android writes the bytes to `reader_clip.m4a` in the cache directory and plays that file with `MediaPlayer`. iOS copies the bytes into `NSData` and plays them with `AVAudioPlayer`.

## Inventory and filenames

The generator reads `phrases.json`, `curriculum.json`, and `lexicon.json` from its output directory. The default is `composeApp/src/commonMain/composeResources/files`.

Each word clip is the first token text for a `skey`. `skey` matches `tools/build_data.py` and `Norm.skey`: NFC, lower case, superscripts and punctuation removed, `k` folded to `c`, accents kept. The filename is the first 10 hex characters of the SHA-1 of that key, plus `.m4a`.

Every lemma in `curriculum.json` also gets a word clip for its headword, under the headword's `skey`, unless a token already has that key.

Each line clip is one phrase. The filename is `l_` plus the first 10 hex characters of the SHA-1 of the phrase id, plus `.m4a`.

`audio.json` is compact JSON with `words` and `lines` only. It is written after every planned clip file is present. An `.m4a` that has left the inventory stays in `audio/` until removed by hand.

## Pronunciation

`tools/quenya_phonetics.py` chooses the segments and the stressed vowel. Acute marks a long vowel. Diaeresis keeps a vowel in its own syllable. The diphthongs are `ai`, `oi`, `ui`, `au`, `eu`, `iu`. The digraphs `qu`, `ty`, `hy`, `ny`, `ly`, and `hw` are one consonant segment (`qu` as `kw`, the palatals as consonant plus `j`, `hw` as `hw`). `ry` is two consonants, so *cirya* is kir-ya. `x` is `k` then `s`. A geminate is two of the same consonant segment. `hl` and `hr` are `h` plus `l`, or `h` plus `r`. An unrecognised letter raises.

`to_phonemes()` still prints an ASCII `[[...]]` string with `'` before the stressed vowel, for a manual check of that module. `tools/piper_utterance.py` does not read it. A stressed diphthong and two adjacent vowels collapse to the same characters in that string, so the adapter calls `_segments` and `_stress_index` and remaps those segments into the IPA characters this voice was trained on.

Stress is the phonetics module's decision: never the last syllable; two syllables stress the first; three or more stress the penult when it is heavy (long vowel, diphthong, or two or more following consonants), otherwise the antepenult. The adapter then groups syllables and writes `ˈ` before the onset of the stressed one:

- Leading consonants open the first syllable.
- Between vowels, the last consonant opens the next syllable. Any consonants before it close the current syllable. That keeps the *r* of *Eärendil* on *-ren-*.
- Trailing consonants close the last syllable.
- The stressed vowel is already chosen. Moving a consonant into the next onset leaves that vowel stressed.

Each character inside Piper's `[[ ... ]]` is one phoneme id, and each of those characters has to be in the loaded voice's phoneme map. Syllable dots are not written. `.` inside the brackets is the statement mark. A word clip is one block ending in `.`. A line is one block: words separated by a space; a comma, semicolon, or colon on a word becomes a comma and a space before the next word; a final `.`, `?`, or `!` is that character; a line with no final mark ends in `.`. The wav has no inserted silence. An empty line, or a line with no words left after the marks are removed, fails the run.

A spelling the phonetics module rejects, or a segment the adapter cannot map, fails the run and names the spelling. That happens before the voice download and before any clip is replaced.

### Phone map

| Segment | Piper characters |
|---|---|
| `a` / `a:` | `ɑ` / `ɑː` |
| `e` / `e:` | `ɛ` / `ɛː` |
| `i` / `i:` | `ɪ` / `iː` |
| `o` / `o:` | `ɒ` / `ɔː` |
| `u` / `u:` | `ʊ` / `uː` |
| `ai` `au` `oi` `ui` `eu` `iu` | `aɪ` `aʊ` `ɔɪ` `ʊɪ` `ɛʊ` `ɪʊ` |
| `r` | `ɹ` |
| `p t k b d g f v s h m n l w j` | themselves |

Short *o* is LOT (`ɒ`), not the diphthong `əʊ`. *r* is the English approximant `ɹ`. Diphthongs are two characters. `j` is *y* and the palatal digraphs already expanded by the phonetics module. `qu` arrives as `k` then `w`. `x` arrives as `k` then `s`. `hw` arrives as `h` then `w`. `hl` / `hr` arrive as `h` plus `l` or `ɹ`.

### Utterances

`tools/test_piper_utterance.py` pins these strings. They are not derived in the test.

| Spelling | Utterance |
|---|---|
| cirya | `[[ˈkɪɹjɑ.]]` |
| Eärendil | `[[ɛɑˈɹɛndɪl.]]` |
| andúnë | `[[ɑnˈduːnɛ.]]` |
| ancalima | `[[ɑnˈkɑlɪmɑ.]]` |
| né | `[[ˈnɛː.]]` |

The stressed vowel, read from the vowel after `ˈ` once its onset consonants are skipped, length mark kept: *cirya* `ɪ`, *Eärendil* `ɛ`, *andúnë* `uː`, *ancalima* `ɑ`, *né* `ɛː`.

| Line | Utterance |
|---|---|
| cirya né | `[[ˈkɪɹjɑ ˈnɛː.]]` |
| cirya, né — also `cirya; né` and `cirya: né` | `[[ˈkɪɹjɑ, ˈnɛː.]]` |
| cirya né? | `[[ˈkɪɹjɑ ˈnɛː?]]` |
| aiya Eärendil elenion ancalima | `[[ˈaɪjɑ ɛɑˈɹɛndɪl ɛˈlɛnɪɒn ɑnˈkɑlɪmɑ.]]` |

## Generator

`piper-tts` loads one `PiperVoice` for the run. `SynthesisConfig.length_scale` is 1.15 for every clip. Above 1 is slower than the voice's own pace.

Python, including the dry run and the dry-run test, is the shared virtual environment `../.venv-piper`, in the parent directory of this repo. Another interpreter stops and prints `source` of that environment's `bin/activate`. A missing `piper-tts` install names `pip install piper-tts`.

Voice files are `<name>.onnx` and `<name>.onnx.json` in `../.piper-voices`, or in `PIPER_VOICES_DIR` when that is set. After the phonetics plan succeeds, a missing or empty file is fetched with `python -m piper.download_voices <name> --data-dir <directory>`. A file that is already present and non-empty stays. The ONNX file is not committed. Piper is GPL-3.0 and stays a build tool. The app ships the AAC clips.

`QUENYA_PIPER_VOICE` selects another voice name. `QUENYA_PIPER_LENGTH` selects another length scale and has to be a number greater than 0.

Piper writes a PCM wav at the rate in the voice JSON. ffmpeg encodes it to AAC at 32 kbps. The new file is a `.partial.m4a` beside the clip; ffmpeg decodes that partial, and the clip is replaced only after the decode succeeds. ffmpeg has to be on `PATH`. The generator does not call `espeak-ng`.

### Order of a run

1. Require the shared virtual environment.
2. `--dry-utterance` prints the listen-list utterances and returns before Piper is imported. It does not read a model or write clips.
3. Require ffmpeg and `piper-tts`. Read the length scale and the voice name.
4. Plan every clip. A phonetics error prints the spellings and exits.
5. Fetch the voice if needed, and load it.
6. Every character inside every planned utterance must be in the loaded phoneme map. A miss names the character and the spelling or line, and exits.
7. Probe *cirya*, *Eärendil*, *andúnë*, and *ancalima* the same way, and require space, comma, `.`, `?`, and `!` in the map. Then synthesize *cirya* and confirm ffmpeg can decode the wav.
8. Create `audio/` and synthesize each cache miss.
9. If any planned file is absent, exit and leave `audio.json` unwritten.
10. Write `audio.json`. Print the clip count, the size of `audio/`, and the listen-list paths.

### Cache

`tools/.piper/cache.json` is gitignored and belongs to this worktree. Each record stores the voice name, the length scale, and the exact utterance for one filename. A record whose clip is present, and whose three fields match, skips synthesis. Any other record replaces that clip. The record is saved after each new clip. Playback reads `audio.json` and the `.m4a` files.

Sibling worktrees share the virtual environment and the voice directory. Each worktree keeps its own cache, so one tree's record cannot skip synthesis in another.

A run that exits in steps 4–7 leaves the clips and `audio.json` as they were. From step 8, a clip is replaced only after its new AAC decodes. Clips not yet reached stay. `audio.json` stays the previous file until step 10.

### Listen list

`--dry-utterance` prints these as utterances. A full run prints their paths under `audio/`:

- cirya
- Eärendil
- andúnë
- ancalima
- aiya Eärendil elenion ancalima

A label absent from this library prints `(not in this library)`. Accept a regenerated voice by listening to this list.

## Tests

From the shared virtual environment:

```bash
python -m unittest discover -s tools -p 'test_*.py'
```

`tools/test_piper_utterance.py` compares utterance strings. It does not load ONNX and does not snapshot a waveform. The dry-run test invokes that environment's Python on `tools/generate_audio.py --dry-utterance`, with a fake `piper` module first on `PYTHONPATH`.

## Runbook

From the app directory:

```bash
source ../.venv-piper/bin/activate
python -m unittest discover -s tools -p 'test_*.py'
python tools/generate_audio.py --dry-utterance
python tools/generate_audio.py
```

`--out DIR` writes `audio/` and `audio.json` into another directory that already contains `phrases.json`. The default voice, length, and voice directory are `en_GB-cori-high`, `1.15`, and `../.piper-voices`, overridden by `QUENYA_PIPER_VOICE`, `QUENYA_PIPER_LENGTH`, and `PIPER_VOICES_DIR`.
