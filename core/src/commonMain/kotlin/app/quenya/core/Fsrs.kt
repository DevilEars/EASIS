package app.quenya.core

import kotlin.math.E
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * FSRS-6 scheduler, ported from py-fsrs 6.3.2 (open-spaced-repetition/py-fsrs).
 * Differences from the reference: no interval fuzzing (deterministic), and time
 * is epoch milliseconds (UTC). Behaviour is checked against reference traces in
 * FsrsReferenceTest.
 */
enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

enum class CardState(val value: Int) {
    Learning(1), Review(2), Relearning(3);

    companion object {
        fun of(v: Int) = entries.first { it.value == v }
    }
}

data class FsrsCard(
    val state: CardState = CardState.Learning,
    val step: Int? = 0,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueMs: Long = 0L,
    val lastReviewMs: Long? = null,
)

class FsrsScheduler(
    private val parameters: List<Double> = DEFAULT_PARAMETERS,
    private val desiredRetention: Double = 0.9,
    private val learningStepsMs: List<Long> = listOf(MINUTE, 10 * MINUTE),
    private val relearningStepsMs: List<Long> = listOf(10 * MINUTE),
    private val maximumIntervalDays: Int = 36500,
) {
    private val decay = -parameters[20]
    private val factor = 0.9.pow(1.0 / decay) - 1.0

    init {
        require(parameters.size == 21) { "FSRS-6 needs 21 parameters" }
    }

    fun retrievability(card: FsrsCard, nowMs: Long): Double {
        val last = card.lastReviewMs ?: return 0.0
        val s = card.stability ?: return 0.0
        val elapsedDays = max(0L, floorDays(nowMs - last))
        return (1.0 + factor * elapsedDays / s).pow(decay)
    }

    fun review(card: FsrsCard, rating: Rating, nowMs: Long): FsrsCard {
        val daysSince = card.lastReviewMs?.let { floorDays(nowMs - it) }
        var stability = card.stability
        var difficulty = card.difficulty
        var state = card.state
        var step = card.step
        val intervalMs: Long

        fun longTerm(): Double = nextStability(difficulty!!, stability!!, retrievability(card, nowMs), rating)

        when (card.state) {
            CardState.Learning -> {
                if (stability == null || difficulty == null) {
                    stability = initialStability(rating)
                    difficulty = initialDifficulty(rating, clamp = true)
                } else if (daysSince != null && daysSince < 1) {
                    stability = shortTermStability(stability, rating)
                    difficulty = nextDifficulty(difficulty, rating)
                } else {
                    stability = longTerm()
                    difficulty = nextDifficulty(difficulty, rating)
                }
                val steps = learningStepsMs
                if (steps.isEmpty() || (step!! >= steps.size && rating != Rating.Again)) {
                    state = CardState.Review; step = null
                    intervalMs = daysToMs(nextIntervalDays(stability))
                } else {
                    when (rating) {
                        Rating.Again -> { step = 0; intervalMs = steps[0] }
                        Rating.Hard -> intervalMs = when {
                            step == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
                            step == 0 && steps.size >= 2 -> (steps[0] + steps[1]) / 2
                            else -> steps[step]
                        }
                        Rating.Good -> if (step + 1 == steps.size) {
                            state = CardState.Review; step = null
                            intervalMs = daysToMs(nextIntervalDays(stability))
                        } else {
                            step += 1; intervalMs = steps[step]
                        }
                        Rating.Easy -> {
                            state = CardState.Review; step = null
                            intervalMs = daysToMs(nextIntervalDays(stability))
                        }
                    }
                }
            }

            CardState.Review -> {
                stability = if (daysSince != null && daysSince < 1) shortTermStability(stability!!, rating)
                else longTerm()
                difficulty = nextDifficulty(difficulty!!, rating)
                intervalMs = if (rating == Rating.Again) {
                    if (relearningStepsMs.isEmpty()) daysToMs(nextIntervalDays(stability))
                    else { state = CardState.Relearning; step = 0; relearningStepsMs[0] }
                } else daysToMs(nextIntervalDays(stability))
            }

            CardState.Relearning -> {
                if (daysSince != null && daysSince < 1) {
                    stability = shortTermStability(stability!!, rating)
                    difficulty = nextDifficulty(difficulty!!, rating)
                } else {
                    stability = longTerm()
                    difficulty = nextDifficulty(difficulty!!, rating)
                }
                val steps = relearningStepsMs
                if (steps.isEmpty() || (step!! >= steps.size && rating != Rating.Again)) {
                    state = CardState.Review; step = null
                    intervalMs = daysToMs(nextIntervalDays(stability))
                } else {
                    when (rating) {
                        Rating.Again -> { step = 0; intervalMs = steps[0] }
                        Rating.Hard -> intervalMs = when {
                            step == 0 && steps.size == 1 -> (steps[0] * 1.5).toLong()
                            step == 0 && steps.size >= 2 -> (steps[0] + steps[1]) / 2
                            else -> steps[step]
                        }
                        Rating.Good -> if (step + 1 == steps.size) {
                            state = CardState.Review; step = null
                            intervalMs = daysToMs(nextIntervalDays(stability))
                        } else {
                            step += 1; intervalMs = steps[step]
                        }
                        Rating.Easy -> {
                            state = CardState.Review; step = null
                            intervalMs = daysToMs(nextIntervalDays(stability))
                        }
                    }
                }
            }
        }
        return FsrsCard(state, step, stability, difficulty, nowMs + intervalMs, nowMs)
    }

    // ---- model -----------------------------------------------------------
    private fun clampD(d: Double) = min(max(d, MIN_DIFFICULTY), MAX_DIFFICULTY)
    private fun clampS(s: Double) = max(s, STABILITY_MIN)

    private fun initialStability(r: Rating) = clampS(parameters[r.value - 1])

    private fun initialDifficulty(r: Rating, clamp: Boolean): Double {
        val d = parameters[4] - E.pow(parameters[5] * (r.value - 1)) + 1
        return if (clamp) clampD(d) else d
    }

    private fun nextIntervalDays(stability: Double): Int {
        val raw = (stability / factor) * (desiredRetention.pow(1.0 / decay) - 1)
        return min(max(round(raw).toInt(), 1), maximumIntervalDays)
    }

    private fun shortTermStability(stability: Double, r: Rating): Double {
        var inc = E.pow(parameters[17] * (r.value - 3 + parameters[18])) * stability.pow(-parameters[19])
        if (r != Rating.Again) inc = max(inc, 1.0)
        return clampS(stability * inc)
    }

    private fun nextDifficulty(d: Double, r: Rating): Double {
        val arg1 = initialDifficulty(Rating.Easy, clamp = false)
        val delta = -(parameters[6] * (r.value - 3))
        val arg2 = d + (10.0 - d) * delta / 9.0
        return clampD(parameters[7] * arg1 + (1 - parameters[7]) * arg2)
    }

    private fun nextStability(d: Double, s: Double, rv: Double, r: Rating): Double {
        val n = if (r == Rating.Again) nextForgetStability(d, s, rv) else nextRecallStability(d, s, rv, r)
        return clampS(n)
    }

    private fun nextForgetStability(d: Double, s: Double, rv: Double): Double {
        val longTerm = parameters[11] * d.pow(-parameters[12]) *
            ((s + 1).pow(parameters[13]) - 1) * E.pow((1 - rv) * parameters[14])
        val shortTerm = s / E.pow(parameters[17] * parameters[18])
        return min(longTerm, shortTerm)
    }

    private fun nextRecallStability(d: Double, s: Double, rv: Double, r: Rating): Double {
        val hard = if (r == Rating.Hard) parameters[15] else 1.0
        val easy = if (r == Rating.Easy) parameters[16] else 1.0
        return s * (1 + E.pow(parameters[8]) * (11 - d) * s.pow(-parameters[9]) *
            (E.pow((1 - rv) * parameters[10]) - 1) * hard * easy)
    }

    companion object {
        const val MINUTE = 60_000L
        const val DAY_MS = 86_400_000L
        private const val STABILITY_MIN = 0.001
        private const val MIN_DIFFICULTY = 1.0
        private const val MAX_DIFFICULTY = 10.0

        val DEFAULT_PARAMETERS = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        private fun daysToMs(days: Int) = days * DAY_MS
        /** Python's timedelta.days: floor of whole days. */
        private fun floorDays(ms: Long): Long = if (ms >= 0) ms / DAY_MS else -((-ms + DAY_MS - 1) / DAY_MS)
    }
}
