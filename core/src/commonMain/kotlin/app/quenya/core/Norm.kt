package app.quenya.core

/** Text normalisation shared by the checker and the exercise generator. */
object Norm {
    private val accentMap: Map<Char, Char> = buildMap {
        fun add(base: Char, vararg accented: Char) = accented.forEach { put(it, base) }
        add('a', 'á', 'à', 'â', 'ä', 'ā', 'ă')
        add('e', 'é', 'è', 'ê', 'ë', 'ē', 'ĕ')
        add('i', 'í', 'ì', 'î', 'ï', 'ī', 'ĭ')
        add('o', 'ó', 'ò', 'ô', 'ö', 'ō', 'ŏ')
        add('u', 'ú', 'ù', 'û', 'ü', 'ū', 'ŭ')
        add('y', 'ý', 'ÿ')
    }
    private val combining = mapOf('\u0301' to "áéíóúý", '\u0308' to "äëïöüÿ", '\u0302' to "âêîôû", '\u0304' to "āēīōū")
    private val plain = mapOf('\u0301' to "aeiouy", '\u0308' to "aeiouy", '\u0302' to "aeiou", '\u0304' to "aeiou")
    private const val DROP = "’‘'`´-?.,;:!\"“”()[]…¹²³⁴⁵⁶⁷⁸⁹⁰"

    /** Compose a few common combining sequences so typed text matches the (precomposed) data. */
    fun nfc(s: String): String {
        val out = StringBuilder()
        for (c in s) {
            val marks = plain[c]
            if (marks != null && out.isNotEmpty()) {
                val idx = plain.getValue(c).indexOf(out.last().lowercaseChar())
                if (idx >= 0) {
                    val composed = combining.getValue(c)[idx]
                    val wasUpper = out.last().isUpperCase()
                    out.setLength(out.length - 1)
                    out.append(if (wasUpper) composed.uppercaseChar() else composed)
                    continue
                }
            }
            out.append(c)
        }
        return out.toString()
    }

    /** Strict key: case/punctuation-insensitive, k == c, accents KEPT. */
    fun skey(s: String): String =
        nfc(s).lowercase().filter { it !in DROP }.replace('k', 'c')

    /** Loose key: also folds accents. */
    fun key(s: String): String = skey(s).map { accentMap[it] ?: it }.joinToString("")

    fun words(s: String): List<String> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun featureLabel(features: List<String>) = features.joinToString(" + ") { it.replace('-', ' ') }
}
