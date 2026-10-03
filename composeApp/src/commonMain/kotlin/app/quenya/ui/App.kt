package app.quenya.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.db.SqlDriver
import app.quenya.core.*
import app.quenya.core.CheckResult
import app.quenya.core.io.DataLoader
import app.quenya.ui.resources.Res
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
fun App(driver: SqlDriver, nowMs: () -> Long, audioPlayer: AudioPlayer, themePreference: ThemePreference) {
    var model by remember { mutableStateOf<AppModel?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val (course, meta) = loadAssets()
            val store = SqlReviewStore(driver)
            val m = AppModel(course, StudyEngine(course, store, nowMs = nowMs), store, DataLoader.about(meta), audioPlayer, themePreference)
            m.refresh()
            model = m
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        }
    }
    EasisTheme(darkOverride = model?.darkOverride) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                val m = model
                when {
                    error != null -> Text("Could not load data: $error")
                    m == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> Root(m)
                }
            }
        }
    }
}

@Composable
private fun Root(m: AppModel) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (m.screen) {
                Screen.Home -> HomeScreen(m) { scope.launch { m.start() } }
                Screen.Study -> StudyScreen(m)
                Screen.Done -> DoneScreen(m)
                Screen.Reader -> ReaderScreen(m.course, m.audioPlayer)
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
private fun HomeScreen(m: AppModel, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Eäsis", style = MaterialTheme.typography.headlineLarge)
        Text("Quenya self-study app", style = MaterialTheme.typography.titleSmall)
        val s = m.nextSession
        if (s == null) Text("Course complete.")
        else {
            Text("Session ${s.n} of ${m.total}", style = MaterialTheme.typography.titleMedium)
            Text(s.title ?: s.kind.replaceFirstChar { it.uppercase() })
            s.milestone?.let {
                Text("Milestone: $it", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Row {
            Text("Reviews due: ${m.due}    ")
            Text("Streak: ${m.streak} day(s)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (s != null) Button(onStart) { Text("Start session") }
    }
}

@Composable
private fun StudyScreen(m: AppModel) {
    val scope = rememberCoroutineScope()
    val step = m.steps[m.index]
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(progress = { (m.index + 1f) / m.steps.size }, modifier = Modifier.fillMaxWidth())
        key(m.index) {
            val next: () -> Unit = { scope.launch { m.next() } }
            when (step) {
                is SessionStep.Review -> ReviewView(step) { r -> scope.launch { m.rate(step.lemma.id, r); m.next() } }
                is SessionStep.LessonStep -> LessonView(step, m.course, next)
                is SessionStep.Intro -> IntroView(step, m.course, next)
                is SessionStep.Reading -> ReadingView(step, m.course, m.audioPlayer, next)
                is SessionStep.Choice -> ChoiceView(step.exercise, next)
                is SessionStep.Typed -> TypedView(step, m.checker, next)
            }
        }
    }
}

@Composable
private fun ReviewView(s: SessionStep.Review, onRate: (Rating) -> Unit) {
    var shown by remember { mutableStateOf(false) }
    Text("Review", style = MaterialTheme.typography.labelLarge)
    Text(s.lemma.lemma, style = MaterialTheme.typography.displaySmall)
    if (!shown) Button({ shown = true }) { Text("Show answer") }
    else {
        Text("“${s.lemma.gloss.en}”", style = MaterialTheme.typography.titleLarge)
        s.example?.let { (p, _) -> Text("${p.text} — “${p.gloss}”") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.forEach { r -> Button({ onRate(r) }) { Text(r.name) } }
        }
    }
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
        if (full) s.lesson.body.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(s.lesson.source, style = MaterialTheme.typography.labelSmall)
    }
    Button(onNext) { Text("Continue") }
}

@Composable
private fun ColumnScope.IntroView(s: SessionStep.Intro, course: CourseData, onNext: () -> Unit) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("New words", style = MaterialTheme.typography.headlineSmall)
        s.lemmas.forEach { e ->
            Column {
                Text(e.lemma, style = MaterialTheme.typography.titleLarge)
                Text("${e.pos} — “${e.gloss.en}”")
                course.exampleFor(e.id)?.let { (p, _) -> Text("${p.text} — “${p.gloss}”", style = MaterialTheme.typography.bodySmall) }
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

/** Reads a generated clip from resources and plays it; absent for content item 7 hasn't covered. */
@Composable
private fun PlayButton(audioPlayer: AudioPlayer, filename: String?, label: String) {
    if (filename == null) return
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { audioPlayer.play(Res.readBytes("files/audio/$filename")) } }) { Text("▶ $label") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhraseView(p: Phrase, course: CourseData, audioPlayer: AudioPlayer) {
    var sel by remember(p.id) { mutableStateOf<Token?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            p.tokens.forEach { t ->
                Text(
                    t.text + t.punct,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (sel == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { sel = t }.padding(vertical = 4.dp),
                )
            }
        }
        Text("“${p.gloss}”", style = MaterialTheme.typography.bodyMedium)
        PlayButton(audioPlayer, course.audioForLine(p.id), "Play line")
        sel?.let { TokenDetail(it, course, audioPlayer) }
        p.note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun TokenDetail(t: Token, course: CourseData, audioPlayer: AudioPlayer) {
    val e = t.lemma?.let { course.lex[it] }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (e == null) {
                Text("No lemma data for “${t.text}” in Eldamo's token analysis.")
                t.guess?.let { g -> Text("Possibly related to: ${course.lex[g]?.lemma ?: g} (unverified)", style = MaterialTheme.typography.bodySmall) }
            } else {
                Text("${e.lemma} — “${e.gloss.en}”", fontWeight = FontWeight.Bold)
                Text(e.pos)
                if (t.features.isNotEmpty()) Text("form: ${Norm.featureLabel(t.features)}")
                val how = when (t.resolution) {
                    "eldamo-element" -> "word analysis from Eldamo"
                    else -> "matched by lookup (${t.resolution}), not Eldamo's own analysis"
                }
                Text("$how · ${e.confidence}" + (e.mark?.let { " · Eldamo mark $it" } ?: ""), style = MaterialTheme.typography.labelSmall)
            }
            PlayButton(audioPlayer, course.audioForWord(t.text), "Play word")
        }
    }
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
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s.exercise.prompt, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), enabled = result == null, singleLine = true)
        val r = result
        if (r == null) Button({ result = checker.check(text, s.phrase) }, enabled = text.isNotBlank()) { Text("Check") }
        else {
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
        Text("Session complete", style = MaterialTheme.typography.headlineMedium)
        Text("Streak: ${m.streak} day(s). Sessions done: ${m.completed} of ${m.total}.")
        Button({ m.go(Screen.Home) }) { Text("Home") }
    }
}

@Composable
private fun ReaderScreen(course: CourseData, audioPlayer: AudioPlayer) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Reader", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Tap a word for its lemma, gloss and form. Lines come from Eldamo's phrase entries.", style = MaterialTheme.typography.bodySmall) }
        items(course.phrases, key = { it.id }) { PhraseView(it, course, audioPlayer) }
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
