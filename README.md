# Eäsis
### Quenya self-study app

*Eäsis* — "Elvish as She is Spoke." Android-first (Kotlin Multiplatform + Compose Multiplatform,
iOS-ready) course that teaches Late Quenya
from [Eldamo](https://eldamo.org) data. Nothing is hand-authored: lexicon, attested forms, phrases,
lesson text and the lesson order are all generated from Eldamo. Goal: read the opening of *Markirya*
(first 12 lines) with tap-to-translate help.

```
Data © 2008–2026 Paul Strack, Eldamo, CC BY 4.0.** 

The app's About screen credits it.
Tolkien's texts remain under copyright: this is a personal-use build.
```

## What has been verified, and what has not

| Part | Status |
|---|---|
| `tools/build_data.py` (Eldamo → JSON, curriculum) | Run against Eldamo 0.8.13; invariants checked (every lemma resolves, no word read before it is taught, session numbers contiguous) |
| `core/` logic (FSRS, exercises, checker, planner, study engine) | **16 tests pass** against real data. FSRS matches py-fsrs 6.3.2 on 1,200 reference reviews |
| `core/.../io/DataLoader.kt` (kotlinx.serialization) | Compiles against real Gradle build now |
| `composeApp/`, `androidApp/`, Gradle files, SQLDelight schema | **Builds.** `gradle :androidApp:assembleDebug` succeeds; debug APK installs. Versions in `gradle/libs.versions.toml` are confirmed working (Kotlin 2.4.10 / AGP 9.1.0 / Compose 1.12.1 / SQLDelight 2.3.2) |
| iOS target (`compileKotlinIosSimulatorArm64`) | **Compiles.** No Xcode wrapper project yet (see `BACKLOG.md` item 10) |
| Reader audio (`tools/generate_audio.py`, tap-to-play) | **Generated and builds.** 123 synthesized AAC clips (~520 KiB) for every phrase/word in `phrases.json`. Decoded clips, not naturalness, verified so far — give one a listen |
| CI | Not yet run on a real machine |

## Layout

```
tools/            build_data.py  skeleton.json  eldamo_report.py  fsrs_reference.py
                  make_kotlin_fixtures.py  run_core_tests.sh  quenya_phonetics.py  generate_audio.py
core/             pure logic (commonMain) + tests (commonTest, run on real data)
composeApp/       Compose UI, SQLDelight store, generated JSON in composeResources/files/
androidApp/       Android application module (AGP 9 needs the app separate from KMP code)
```

## Regenerate the data

```bash
curl -L -o tools/eldamo-data.xml https://raw.githubusercontent.com/pfstrack/eldamo/master/src/data/eldamo-data.xml
python tools/build_data.py tools/eldamo-data.xml    # writes composeApp/.../composeResources/files/*.json
python tools/make_kotlin_fixtures.py                # refresh the test fixtures from the new data
python tools/generate_audio.py                      # synthesizes Reader audio clips (needs espeak-ng, ffmpeg)
```
`espeak-ng` and `ffmpeg` (`brew install espeak-ng ffmpeg` on macOS) are only needed for the audio step —
everything else is pure Python. `tools/skeleton.json` is the only hand-edited input besides the Quenya
phoneme/stress rules in `tools/quenya_phonetics.py` (Appendix E conventions; Eldamo's own phonetic data
turned out to be historical sound-change records, not a synchronic pronunciation table — see BACKLOG
item 7). Neither holds lesson content.
The script prints the milestone sessions and exits non-zero if any invariant fails.
Current output: 49 sessions; milestones at session 13 (*Elen síla*), 18 (*Aiya Eärendil*),
**35 (*Markirya*, 12 lines)** and 49 (whole poem, stretch).

## Run the logic tests

Without Gradle (needs `kotlinc` and JUnit 4 only):
```bash
KOTLINC_HOME=/path/to/kotlinc ./tools/run_core_tests.sh
```
With Gradle: `./gradlew :core:jvmTest`.

## Build the Android app

Open the folder in Android Studio and run it from there, or from a terminal — with an emulator or
device already running — rebuild and reinstall over any existing install with:

```bash
./gradlew :androidApp:installDebug
```

Sideloading `androidApp/build/outputs/apk/debug/*.apk` works too, if you'd rather not install via Gradle.

## iOS

`composeApp` exposes `MainViewController()` (in `iosMain`). Create the Xcode wrapper from the KMP wizard's
`iosApp` template and call it. CI compiles the iOS target on every push.

## How a session works (15 minutes)

Reviews due (FSRS, up to 12) → the session's new material from `curriculum.json`
(a lesson, new words, practice, or a reading) → generated exercises → one or two guided-production prompts.
Every exercise stores its answer key and the Eldamo source it came from. The production checker never
says a sentence is "ungrammatical": it reports whether your answer matches the key and explains each word
against the lexicon and the attested forms.
