package app.quenya.ui

import app.cash.sqldelight.db.SqlDriver
import app.quenya.core.CardState
import app.quenya.core.FsrsCard
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
}
