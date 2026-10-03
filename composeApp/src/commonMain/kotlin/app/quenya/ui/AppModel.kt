package app.quenya.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.quenya.core.*

enum class Screen { Home, Study, Done, Reader, About }

/** Thin UI state holder; all rules live in StudyEngine (tested in :core). */
class AppModel(
    val course: CourseData,
    private val engine: StudyEngine,
    private val store: ReviewStore,
    val aboutText: String,
    val audioPlayer: AudioPlayer,
    private val themePreference: ThemePreference,
) {
    val checker = Checker(course)
    var screen by mutableStateOf(Screen.Home); private set
    /** null = follow the system setting; non-null = manual Laurelin(false)/Telperion(true) override. */
    var darkOverride by mutableStateOf(themePreference.get()); private set
    var completed by mutableStateOf(0); private set
    var streak by mutableStateOf(0); private set
    var due by mutableStateOf(0); private set
    var steps by mutableStateOf<List<SessionStep>>(emptyList()); private set
    var index by mutableStateOf(0); private set
    var inProgress by mutableStateOf(false); private set
    var unlockedPhrases by mutableStateOf<List<Phrase>>(emptyList()); private set

    val total: Int get() = engine.sessionCount
    val nextSession: Session? get() = course.curriculum.getOrNull(completed)

    suspend fun refresh() {
        completed = store.completedSessions()
        streak = store.streak().count
        due = engine.dueCount()
        inProgress = store.inProgressSession() != null
        unlockedPhrases = engine.unlockedPhrases()
    }

    suspend fun start() {
        steps = engine.buildSteps(seed = completed.toLong() * 7919 + due)
        index = 0
        inProgress = steps.isNotEmpty()
        screen = if (steps.isEmpty()) Screen.Home else Screen.Study
    }

    suspend fun resume() {
        val saved = engine.resume() ?: return
        steps = saved.first
        index = saved.second
        screen = Screen.Study
    }

    suspend fun rate(lemmaId: String, rating: Rating) = engine.rate(lemmaId, rating)

    suspend fun next() {
        if (index + 1 < steps.size) { index++; engine.advance(index); return }
        engine.finishSession()
        refresh()
        screen = Screen.Done
    }

    fun exitSession() { screen = Screen.Home }

    fun go(s: Screen) { screen = s }

    fun setAppearance(dark: Boolean?) {
        darkOverride = dark
        themePreference.set(dark)
    }
}
