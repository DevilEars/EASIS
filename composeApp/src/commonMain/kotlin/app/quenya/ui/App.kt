package app.quenya.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.db.SqlDriver
import app.quenya.core.*
import app.quenya.core.CheckResult
import app.quenya.core.io.DataLoader
import app.quenya.ui.resources.Res
import app.quenya.ui.theme.BackgroundMotif
import app.quenya.ui.theme.EasisTheme
import kotlinx.coroutines.launch

private suspend fun loadAssets(): Pair<CourseData, String> {
    suspend fun read(name: String) = Res.readBytes("files/$name.json").decodeToString()
    val course = DataLoader.load(
        read("lexicon"), read("forms"), read("phrases"), read("lessons"), read("curriculum"),
        audio = read("audio"),
    )
    return course to read("meta")
}

@Composable
fun App(
    driver: SqlDriver,
    nowMs: () -> Long,
    audioPlayer: AudioPlayer,
    themePreference: ThemePreference,
    utcOffsetMs: () -> Long = { 0L },
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit = { _, _ -> },
) {
    var model by remember { mutableStateOf<AppModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val (course, meta) = loadAssets()
            val store = SqlReviewStore(driver)
            val engine = StudyEngine(course, store, utcOffsetMs = utcOffsetMs, nowMs = nowMs)
            engine.syncCourseVersion(DataLoader.courseVersion(meta))
            val m = AppModel(course, engine, store, DataLoader.about(meta), audioPlayer, themePreference)
            m.refresh()
            model = m
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        }
    }
    val dark = model?.darkOverride ?: isSystemInDarkTheme()
    EasisTheme(darkOverride = model?.darkOverride) {
        // Telperion's dark ground makes a line pop at low alpha; Laurelin's cream ground needs much
        // more alpha for the same line to register against it — same formula, different contrast math.
        val motifAlpha = if (dark) 0.14f else 0.30f
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                BackgroundMotif(
                    lineColor = MaterialTheme.colorScheme.outline.copy(alpha = motifAlpha),
                    leafColor = MaterialTheme.colorScheme.tertiary.copy(alpha = motifAlpha),
                    topLeft = !dark,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    val m = model
                    when {
                        error != null -> Text("Could not load data: $error")
                        m == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else -> Root(m, backHandler)
                    }
                }
            }
        }
    }
}

@Composable
private fun Root(m: AppModel, backHandler: @Composable (Boolean, () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    backHandler(m.screen == Screen.Study, m::exitSession)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (m.screen) {
                Screen.Home -> HomeScreen(m, onStart = { scope.launch { m.start() } }, onResume = { scope.launch { m.resume() } }, onRetake = { scope.launch { m.retake() } })
                Screen.Study -> StudyScreen(m)
                Screen.Done -> DoneScreen(m)
                Screen.Reader -> ReaderScreen(m.course, m.unlockedPhrases, m.audioPlayer)
                Screen.Markirya -> MarkiryaScreen(m.course, m.goalPhrases, m.audioPlayer)
                Screen.About -> AboutScreen(m.aboutText, m.darkOverride, m::setAppearance)
            }
        }
        if (m.screen != Screen.Study) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton({ m.go(Screen.Home) }) { Text("Home") }
                TextButton({ m.go(Screen.Reader) }) { Text("Reader") }
                TextButton({ m.go(Screen.About) }) { Text("About") }
            }
        }
    }
}

@Composable
private fun HomeScreen(m: AppModel, onStart: () -> Unit, onResume: () -> Unit, onRetake: () -> Unit) {
    var confirmRetake by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eäsis", style = MaterialTheme.typography.headlineLarge)
        Text("Elvish as She is Spoke.", style = MaterialTheme.typography.bodyMedium)
        Text(
            buildAnnotatedString {
                append("Learn to read ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Markirya") }
                append(", Tolkien’s poem of the white ship, line by line.")
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        val s = m.nextSession
        val p = s?.let { m.course.phraseById[it.phrase] }
        if (s != null && p != null) {
            Text("Next: ${s.label}", style = MaterialTheme.typography.titleMedium)
            Text(p.text, style = MaterialTheme.typography.titleLarge)
            Text("“${p.gloss}”", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                buildAnnotatedString {
                    append("You can read all of ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("Markirya") }
                    append(".")
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Button({ m.go(Screen.Markirya) }) { Text("Read Markirya") }
        }
        LinearProgressIndicator(progress = { m.linesRead.toFloat() / m.linesTotal }, modifier = Modifier.fillMaxWidth())
        Text("${m.linesRead} of ${m.linesTotal} lines read", style = MaterialTheme.typography.labelSmall)
        Row {
            Text("Reviews due: ${m.due}    ")
            Text("Streak: ${m.streak} day(s)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        when {
            m.inProgress -> Button(onResume) { Text("Resume session") }
            !m.courseDone -> Button(onStart) { Text("Start session") }
            m.due > 0 -> Button(onStart) { Text("Review (${m.due} due)") }
        }
        TextButton({ confirmRetake = true }) { Text("Retake course") }
    }
    if (confirmRetake) {
        AlertDialog(
            onDismissRequest = { confirmRetake = false },
            title = { Text("Start over?") },
            text = { Text("This clears all progress.") },
            confirmButton = { TextButton({ confirmRetake = false; onRetake() }) { Text("Start over") } },
            dismissButton = { TextButton({ confirmRetake = false }) { Text("Cancel") } },
        )
    }
}

private fun stepLabel(step: SessionStep): String = when (step) {
    is SessionStep.Review -> "Reviews"
    is SessionStep.LineIntro -> "This session's line"
    is SessionStep.Intro -> "New words"
    is SessionStep.LessonStep -> "${step.lesson.title} — grammar"
    is SessionStep.Reading -> "Read the line"
    is SessionStep.Choice -> "Questions"
    is SessionStep.Typed -> "Write it yourself"
}

@Composable
private fun StudyScreen(m: AppModel) {
    val scope = rememberCoroutineScope()
    val step = m.steps[m.index]
    val label = m.steps.firstNotNullOfOrNull { (it as? SessionStep.LineIntro)?.session?.label } ?: "Reviews"
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (m.index + 1f) / m.steps.size }, modifier = Modifier.weight(1f))
            TextButton({ m.exitSession() }) { Text("✕") }
        }
        Text(stepLabel(step), style = MaterialTheme.typography.labelLarge)
        key(m.index) {
            val next: () -> Unit = { scope.launch { m.next() } }
            when (step) {
                is SessionStep.Review -> ReviewView(step, m.course, m.audioPlayer) { r -> scope.launch { m.rate(step.lemma.id, r); m.next() } }
                is SessionStep.LineIntro -> LineIntroView(step, m.course, m.audioPlayer, next)
                is SessionStep.Intro -> IntroView(step, m.course, m.audioPlayer, next)
                is SessionStep.LessonStep -> LessonView(step, m.course, next)
                is SessionStep.Reading -> ReadingView(step, m.course, m.audioPlayer, next)
                is SessionStep.Choice -> ChoiceView(step.exercise, next)
                is SessionStep.Typed -> TypedView(step, m.checker, next)
            }
        }
    }
}

@Composable
private fun ReviewView(s: SessionStep.Review, course: CourseData, audioPlayer: AudioPlayer, onRate: (Rating) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Text(s.lemma.lemma, style = MaterialTheme.typography.displaySmall)
    PlayButton(audioPlayer, course.audioForWord(s.lemma.lemma), "Play word")
    if (!shown) Button({ shown = true }) { Text("Show answer") }
    else {
        Text("“${s.lemma.gloss.en}”", style = MaterialTheme.typography.titleLarge)
        s.example?.let { (p, _) ->
            Text("${p.text} — “${p.gloss}”")
            PlayButton(audioPlayer, course.audioForLine(p.id), "Play line")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { r -> Button({ onRate(r) }) { Text(r.name) } }
        }
    }
}

@Composable
private fun ColumnScope.LineIntroView(s: SessionStep.LineIntro, course: CourseData, audioPlayer: AudioPlayer, onNext: () -> Unit) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.session.label, style = MaterialTheme.typography.titleMedium)
        Text(s.phrase.text, style = MaterialTheme.typography.titleLarge)
        Text("“${s.phrase.gloss}”", style = MaterialTheme.typography.bodyLarge)
        PlayButton(audioPlayer, course.audioForLine(s.phrase.id), "Play line")
        Text("By the end of this session you can read this line.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onNext) { Text("Continue") }
}

@Composable
private fun ColumnScope.LessonView(s: SessionStep.LessonStep, course: CourseData, onNext: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.lesson.title, style = MaterialTheme.typography.headlineSmall)
        if (s.lesson.generated) Text("Generated from Eldamo's data (no grammar entry exists).", style = MaterialTheme.typography.labelSmall)
        Text(s.lesson.summary)
        if (s.examples.isNotEmpty()) {
            Text("Attested examples", fontWeight = FontWeight.Bold)
            s.examples.forEach { f ->
                val e = course.lex[f.lemma]
                Text("${e?.lemma} “${e?.gloss?.en}” → ${f.surface}   (${f.source})")
            }
        }
        TextButton({ full = !full }) { Text(if (full) "Hide full entry" else "Read full entry") }
        if (full) s.lesson.body.forEach { Text(it) }
        Text(s.lesson.source, style = MaterialTheme.typography.labelSmall)
    }
    Button(onNext) { Text("Continue") }
}

@Composable
private fun ColumnScope.IntroView(s: SessionStep.Intro, course: CourseData, audioPlayer: AudioPlayer, onNext: () -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New words", style = MaterialTheme.typography.headlineSmall)
        s.lemmas.forEach { e ->
            Column {
                Text(e.lemma, style = MaterialTheme.typography.titleLarge)
                Text("${e.pos} — “${e.gloss.en}”")
                PlayButton(audioPlayer, course.audioForWord(e.lemma), "Play word")
            }
        }
    }
    Button(onNext) { Text("Continue") }
}

@Composable
private fun ColumnScope.ReadingView(s: SessionStep.Reading, course: CourseData, audioPlayer: AudioPlayer, onNext: () -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Reading — tap a word", style = MaterialTheme.typography.headlineSmall)
        s.phrases.forEach { PhraseView(it, course, audioPlayer) }
    }
    Button(onNext) { Text("Continue") }
}

@Composable
private fun ChoiceView(ex: ChoiceExercise, onNext: () -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    Text(ex.prompt, style = MaterialTheme.typography.titleMedium)
    ex.options.forEachIndexed { i, o ->
        OutlinedButton({ if (picked == null) picked = i }, Modifier.fillMaxWidth()) {
            val mark = when { picked == null -> ""; i == ex.answerIndex -> "  ✓"; i == picked -> "  ✗"; else -> "" }
            Text(o + mark)
        }
    }
    picked?.let {
        val ok = it == ex.answerIndex
        Text(
            if (ok) "Correct." else "Not quite.",
            fontWeight = FontWeight.Bold,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(ex.explanation)
        Text("Source: ${ex.evidence}", style = MaterialTheme.typography.labelSmall)
        Button(onNext) { Text("Continue") }
    }
}

@Composable
private fun ColumnScope.TypedView(s: SessionStep.Typed, checker: Checker, onNext: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.exercise.prompt, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), enabled = result == null, singleLine = true)
        val r = result
        if (r == null) {
            AccentRow { value = insertAtCursor(value, it) }
            Button({ result = checker.check(value.text, s.phrase) }, enabled = value.text.isNotBlank()) { Text("Check") }
        } else {
            Text(
                when (r.verdict) {
                    Verdict.EXACT -> "Correct."
                    Verdict.ACCENTS_DIFFER -> "Correct, but check the accents."
                    Verdict.ATTESTED_VARIANT -> "Correct: an attested variant."
                    Verdict.INCORRECT -> "Not matching."
                },
                fontWeight = FontWeight.Bold,
                color = if (r.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            r.notes.forEach { Text(it.message, style = MaterialTheme.typography.bodyMedium) }
            if (!r.correct) Text("Answer: ${r.expected}")
            Text("Source: ${s.exercise.evidence}", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (result != null) Button(onNext) { Text("Continue") }
}

@Composable
private fun DoneScreen(m: AppModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = m.lastFinished
        val p = s?.let { m.course.phraseById[it.phrase] }
        when {
            s == null || p == null -> Text("Reviews complete", style = MaterialTheme.typography.headlineMedium)
            s.verse == null -> Text("${s.label} done", style = MaterialTheme.typography.headlineMedium)
            else -> Text("You can now read line ${p.line}", style = MaterialTheme.typography.headlineMedium)
        }
        if (p != null) {
            Text(p.text, style = MaterialTheme.typography.titleLarge)
            Text("“${p.gloss}”")
            PlayButton(m.audioPlayer, m.course.audioForLine(p.id), "Play line")
        }
        Text("${m.linesRead} of ${m.linesTotal} lines read. Streak: ${m.streak} day(s).")
        Button({ m.go(Screen.Home) }) { Text("Home") }
    }
}

@Composable
private fun ReaderScreen(course: CourseData, unlockedPhrases: List<Phrase>, audioPlayer: AudioPlayer) {
    val selection = remember { WordSelection() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Reader", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Lines appear here as you finish their sessions. Tap a word for its meaning and form.", style = MaterialTheme.typography.bodySmall) }
        versedLines(unlockedPhrases, course, audioPlayer, selection)
    }
}

@Composable
private fun MarkiryaScreen(course: CourseData, phrases: List<Phrase>, audioPlayer: AudioPlayer) {
    val selection = remember { WordSelection() }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Markirya", style = MaterialTheme.typography.headlineMedium) }
        versedLines(phrases, course, audioPlayer, selection)
    }
}

@Composable
private fun AboutScreen(about: String, darkOverride: Boolean?, onSetDarkOverride: (Boolean?) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("About", style = MaterialTheme.typography.headlineMedium)
        Text(about)
        Text("Licensed CC BY 4.0. Tolkien's texts remain under copyright; this is a personal-use build.")
        Text("Every word and form shows where it came from. Entries are labelled attested or unverified; Eldamo's own marks are shown unchanged and are not interpreted by this app.")
        Text("Appearance", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(darkOverride == false, { onSetDarkOverride(false) }, { Text("☀ Laurelin") })
            FilterChip(darkOverride == null, { onSetDarkOverride(null) }, { Text("Auto") })
            FilterChip(darkOverride == true, { onSetDarkOverride(true) }, { Text("☾ Telperion") })
        }
    }
}
