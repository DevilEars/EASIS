package app.quenya.ui

import app.cash.sqldelight.db.SqlDriver
import app.quenya.core.CardState
import app.quenya.core.FsrsCard
import app.quenya.core.InProgressSession
import app.quenya.core.ReviewStore
import app.quenya.core.Streak
import app.quenya.db.QuenyaDb

class SqlReviewStore(driver: SqlDriver) : ReviewStore {
    private val q = QuenyaDb(driver).reviewQueries

    override suspend fun cards(): Map<String, FsrsCard> = q.selectCards().executeAsList().associate {
        it.id to FsrsCard(
            state = CardState.of(it.state.toInt()),
            step = it.step?.toInt(),
            stability = it.stability,
            difficulty = it.difficulty,
            dueMs = it.due,
            lastReviewMs = it.last_review,
        )
    }

    override suspend fun saveCard(id: String, card: FsrsCard) {
        q.upsertCard(id, card.state.value.toLong(), card.step?.toLong(), card.stability, card.difficulty,
            card.dueMs, card.lastReviewMs)
    }

    override suspend fun completedSessions(): Int = q.getMeta("completed").executeAsOneOrNull()?.toInt() ?: 0
    override suspend fun setCompletedSessions(n: Int) { q.setMeta("completed", n.toString()) }

    override suspend fun streak(): Streak {
        val parts = q.getMeta("streak").executeAsOneOrNull()?.split(":") ?: return Streak()
        return Streak(parts[0].toInt(), parts[1].toLong())
    }
    override suspend fun setStreak(s: Streak) { q.setMeta("streak", "${s.count}:${s.lastDay}") }

    override suspend fun inProgressSession(): InProgressSession? {
        val raw = q.getMeta("inprogress").executeAsOneOrNull() ?: return null
        val parts = raw.split(":", limit = 4)
        if (parts.size < 4) return null
        val ids = if (parts[3].isEmpty()) emptyList() else parts[3].split(",")
        return InProgressSession(parts[0].toInt(), parts[1].toLong(), parts[2].toInt(), ids)
    }

    override suspend fun saveInProgressSession(s: InProgressSession) {
        q.setMeta("inprogress", "${s.sessionN}:${s.seed}:${s.stepIndex}:${s.reviewLemmaIds.joinToString(",")}")
    }

    override suspend fun clearInProgressSession() { q.deleteMeta("inprogress") }

    override suspend fun courseVersion(): String? = q.getMeta("course_version").executeAsOneOrNull()
    override suspend fun setCourseVersion(v: String) { q.setMeta("course_version", v) }

    override suspend fun clearAll() {
        q.transaction {
            q.deleteAllCards()
            listOf("completed", "streak", "inprogress").forEach { q.deleteMeta(it) }
        }
    }
}
