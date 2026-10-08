package app.quenya.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.quenya.core.*
import app.quenya.ui.resources.Res
import kotlinx.coroutines.launch

/** Reads a generated clip from resources and plays it; nothing when the clip is absent. Plays only on tap. */
@Composable
internal fun PlayButton(audioPlayer: AudioPlayer, filename: String?, label: String) {
    if (filename == null) return
    val scope = rememberCoroutineScope()
    TextButton({ scope.launch { audioPlayer.play(Res.readBytes("files/audio/$filename")) } }) { Text("▶ $label") }
}

/** Lines under headings: warm-ups first, then each verse of the goal text. */
internal fun LazyListScope.versedLines(phrases: List<Phrase>, course: CourseData, audioPlayer: AudioPlayer) {
    val groups = phrases.groupBy { course.sessionByPhrase[it.id]?.verse }
    groups[null]?.let { warmups ->
        item(key = "h-warmups") { Text("Warm-ups", style = MaterialTheme.typography.titleMedium) }
        items(warmups, key = { it.id }) { PhraseView(it, course, audioPlayer) }
    }
    groups.keys.filterNotNull().sorted().forEach { v ->
        item(key = "h-verse-$v") { Text("Verse $v", style = MaterialTheme.typography.titleMedium) }
        items(groups.getValue(v), key = { it.id }) { PhraseView(it, course, audioPlayer) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhraseView(p: Phrase, course: CourseData, audioPlayer: AudioPlayer) {
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
