package app.quenya.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/** Letters the course spells with that phone keyboards hide behind a long-press. */
private const val ACCENTS = "áéíóúäëïöü"

/** Replaces the selection (or inserts at the cursor) and puts the cursor after the new text. */
fun insertAtCursor(value: TextFieldValue, s: String): TextFieldValue {
    val start = value.selection.min
    val text = value.text.replaceRange(start, value.selection.max, s)
    return TextFieldValue(text, TextRange(start + s.length))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentRow(onInsert: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ACCENTS.forEach { c ->
            OutlinedButton({ onInsert(c.toString()) }, Modifier.widthIn(min = 44.dp), contentPadding = PaddingValues(0.dp)) {
                Text(c.toString())
            }
        }
    }
}
