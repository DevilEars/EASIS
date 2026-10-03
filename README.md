# Quenya self-study app

Android-first (Kotlin Multiplatform + Compose Multiplatform, iOS-ready) course that teaches Late Quenya
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
| CI | Not yet run on a real machine |

## Layout

```
tools/            build_data.py  skeleton.json  eldamo_report.py  fsrs_reference.py
                  make_kotlin_fixtures.py  run_core_tests.sh
core/             pure logic (commonMain) + tests (commonTest, run on real data)
composeApp/       Compose UI, SQLDelight store, generated JSON in composeResources/files/
androidApp/       Android application module (AGP 9 needs the app separate from KMP code)
```

## Regenerate the data

```bash
curl -L -o eldamo-data.xml https://raw.githubusercontent.com/pfstrack/eldamo/master/src/data/eldamo-data.xml
python tools/build_data.py eldamo-data.xml          # writes composeApp/.../composeResources/files/*.json
python tools/make_kotlin_fixtures.py                # refresh the test fixtures from the new data
```
`tools/skeleton.json` is the only hand-edited input. It holds topic order and pacing, never lesson content.
The script prints the milestone sessions and exits non-zero if any invariant fails.
Current output: 49 sessions; milestones at session 13 (*Elen síla*), 18 (*Aiya Eärendil*),
**35 (*Markirya*, 12 lines)** and 49 (whole poem, stretch).

## Run the logic tests

Without Gradle (needs `kotlinc` and JUnit 4 only):
```bash
KOTLINC_HOME=/path/to/kotlinc ./tools/run_core_tests.sh
```
With Gradle: `gradle :core:jvmTest`.

## Build the Android app

1. Open the folder in Android Studio (or run `gradle wrapper` once to create the wrapper).
2. If sync fails, fix versions in `gradle/libs.versions.toml` first (see backlog item 3).
3. `gradle :androidApp:installDebug`, or sideload `androidApp/build/outputs/apk/debug/*.apk`.

## iOS

`composeApp` exposes `MainViewController()` (in `iosMain`). Create the Xcode wrapper from the KMP wizard's
`iosApp` template and call it. CI compiles the iOS target on every push.

## How a session works (15 minutes)

Reviews due (FSRS, up to 12) → the session's new material from `curriculum.json`
(a lesson, new words, practice, or a reading) → generated exercises → one or two guided-production prompts.
Every exercise stores its answer key and the Eldamo source it came from. The production checker never
says a sentence is "ungrammatical": it reports whether your answer matches the key and explains each word
against the lexicon and the attested forms.
