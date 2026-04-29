package com.assistant.android.feature

/**
 * NEW FEATURE — Pure on-device calculator. Saves a Gemini call for every "5 plus 3" / "12 percent of 250" /
 * "sqrt 144" the user asks. Supports + - * / % ^ parentheses, and natural-language operators
 * ("plus", "minus", "times", "into", "divided by", "percent of", "of").
 *
 * Returns null if the input doesn't look like math.
 */
object Calculator {

    /** Quick check — does this string look like an arithmetic question? */
    fun looksLikeMath(text: String): Boolean {
        val t = text.lowercase()
        val hasNumber = Regex("\\d").containsMatchIn(t)
        if (!hasNumber) return false
        val opPatterns = listOf(
            "+", "-", "*", "/", "%", "^",
            " plus ", " minus ", " times ", " into ", " divided by ", " divide by ",
            " percent of ", " percent ", " of ", " modulo ", " mod ", " power "
        )
        return opPatterns.any { t.contains(it) }
    }

    fun evaluate(text: String): String? {
        return try {
            val expr = normalize(text)
            if (expr.isBlank()) return null
            val v = Parser(expr).parse()
            formatNumber(v)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalize(text: String): String {
        var t = " " + text.lowercase().trim() + " "
        // word → symbol substitutions
        val map = listOf(
            " plus " to " + ", " add " to " + ",
            " minus " to " - ", " subtract " to " - ",
            " times " to " * ", " multiplied by " to " * ", " into " to " * ", " x " to " * ",
            " divided by " to " / ", " divide by " to " / ", " over " to " / ",
            " modulo " to " % ", " mod " to " % ",
            " power " to " ^ ", " to the power of " to " ^ ",
            " percent of " to " * 0.01 * ", " percent " to " * 0.01 ",
            " of " to " * "
        )
        for ((from, to) in map) t = t.replace(from, to)
        // Strip filler words & punctuation
        t = t.replace(Regex("[?!,]"), " ")
        t = t.replace("equals", " ").replace("=", " ").replace("what's", "").replace("whats", "")
            .replace("what is", "").replace("calculate", "").replace("calc", "")
            .replace("how much is", "")
        // keep digits, dot, operators, parens, whitespace
        return t.replace(Regex("[^0-9.+\\-*/%^()\\s]"), "").trim()
    }

    private fun formatNumber(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "(undefined)"
        return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.6f".format(d).trimEnd('0').trimEnd('.')
    }

    // --- Tiny shunting-yard / recursive-descent parser ---
    private class Parser(private val src: String) {
        private var pos = 0
        fun parse(): Double {
            val v = parseExpr()
            skipWs()
            if (pos < src.length) throw IllegalStateException("trailing input")
            return v
        }
        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) { skipWs()
                val c = peek() ?: break
                if (c == '+') { pos++; v += parseTerm() }
                else if (c == '-') { pos++; v -= parseTerm() }
                else break
            }
            return v
        }
        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) { skipWs()
                val c = peek() ?: break
                if (c == '*') { pos++; v *= parsePower() }
                else if (c == '/') { pos++; val d = parsePower(); v /= d }
                else if (c == '%') { pos++; val d = parsePower(); v %= d }
                else break
            }
            return v
        }
        private fun parsePower(): Double {
            val v = parseUnary()
            skipWs()
            if (peek() == '^') { pos++; return Math.pow(v, parseUnary()) }
            return v
        }
        private fun parseUnary(): Double {
            skipWs()
            if (peek() == '-') { pos++; return -parseUnary() }
            if (peek() == '+') { pos++; return parseUnary() }
            return parsePrimary()
        }
        private fun parsePrimary(): Double {
            skipWs()
            if (peek() == '(') {
                pos++; val v = parseExpr(); skipWs()
                if (peek() == ')') pos++
                return v
            }
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
            if (start == pos) throw IllegalStateException("expected number")
            return src.substring(start, pos).toDouble()
        }
        private fun peek(): Char? = if (pos < src.length) src[pos] else null
        private fun skipWs() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
    }
}
