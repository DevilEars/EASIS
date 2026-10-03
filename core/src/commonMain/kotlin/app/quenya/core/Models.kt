package app.quenya.core

import kotlinx.serialization.Serializable

/** Data classes mirror the JSON written by tools/build_data.py. */

@Serializable
data class Gloss(val en: String = "")

@Serializable
data class LexiconEntry(
    val id: String,
    val lemma: String,
    val pos: String,
    val gloss: Gloss,
    val attestations: Int,
    val mark: String? = null,       // raw Eldamo mark; meaning not interpreted by the app
    val deprecated: Boolean = false,
    val confidence: String,         // "attested" | "unverified"
)

@Serializable
data class FormEntry(
    val lemma: String,
    val surface: String,
    val features: List<String>,
    val source: String,
    val clean: Boolean,
)

@Serializable
data class Token(
    val text: String,
    val punct: String = "",
    val lemma: String? = null,
    val features: List<String> = emptyList(),
    val gloss: String = "",
    val resolution: String,
    val guess: String? = null,
)

@Serializable
data class Variant(val text: String, val source: String)

@Serializable
data class Phrase(
    val id: String,
    val order: Int,
    val text: String,
    val gloss: String,
    val tokens: List<Token>,
    val variants: List<Variant>,
    val textId: String,
    val line: Int,
    val note: String? = null,
)

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val entry: String? = null,
    val generated: Boolean,
    val summary: String,
    val body: List<String> = emptyList(),
    val source: String,
)

/** Reader audio manifest (tools/generate_audio.py): lookup key -> clip filename under files/audio/. */
@Serializable
data class AudioManifest(
    val words: Map<String, String> = emptyMap(),  // Norm.skey(token text) -> filename
    val lines: Map<String, String> = emptyMap(),  // phrase id -> filename
)

@Serializable
data class Slot(
    val kind: String,               // lesson | practice | vocab | reading
    val title: String? = null,
    val lesson: String? = null,
    val feature: String? = null,
    val lemmas: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
)

@Serializable
data class Session(
    val n: Int,
    val slots: List<Slot>,
    val milestone: String? = null,
    val stretch: Boolean = false,
)
