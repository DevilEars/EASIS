package app.quenya.core.io

import app.quenya.core.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses the JSON assets written by tools/build_data.py. Not exercised by the kotlinc-only test run. */
object DataLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(
        lexicon: String, forms: String, phrases: String, lessons: String, curriculum: String,
        audio: String? = null,
    ) = CourseData(
        lexicon = json.decodeFromString(ListSerializer(LexiconEntry.serializer()), lexicon),
        forms = json.decodeFromString(ListSerializer(FormEntry.serializer()), forms),
        phrases = json.decodeFromString(ListSerializer(Phrase.serializer()), phrases),
        lessons = json.decodeFromString(ListSerializer(Lesson.serializer()), lessons),
        curriculum = json.decodeFromString(ListSerializer(Session.serializer()), curriculum),
        audio = audio?.let { json.decodeFromString(AudioManifest.serializer(), it) } ?: AudioManifest(),
    )

    /** Attribution text for the About screen, from meta.json. */
    fun about(meta: String): String {
        val o = json.parseToJsonElement(meta).jsonObject
        return o.getValue("attribution").jsonPrimitive.content +
            "\nEldamo data version " + o.getValue("eldamo_version").jsonPrimitive.content
    }

    /** Hash of curriculum.json written by build_data.py; progress resets when it changes. */
    fun courseVersion(meta: String): String =
        json.parseToJsonElement(meta).jsonObject.getValue("course_version").jsonPrimitive.content
}
