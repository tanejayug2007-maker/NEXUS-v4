package com.nexus.launcher.apps.calculator

import java.math.BigDecimal

/**
 * The calculator's editable expression, as shown on screen ("12×(3−1)", "sin(30)", "Ans÷2").
 * Immutable: every key returns a new state, so the editing rules are plain unit-testable logic.
 *
 * [fresh] is true right after "=": the next digit starts a new expression, the next operator
 * continues from the result.
 */
data class CalcInput(val text: String = "", val fresh: Boolean = false) {

    fun digit(d: Char): CalcInput {
        require(d.isDigit())
        if (fresh) return CalcInput(d.toString())
        val num = trailingNumber()
        // "0" followed by a digit replaces the zero, so you can't type "007"
        if (num == "0") return copy(text = text.dropLast(1) + d)
        return copy(text = text + d)
    }

    fun dot(): CalcInput {
        if (fresh) return CalcInput("0.")
        val num = trailingNumber()
        if ('.' in num) return this
        return copy(text = text + if (num.isEmpty()) "0." else ".")
    }

    /** Binary operator: one of + − × ÷ ^. */
    fun operator(op: Char, result: BigDecimal? = null): CalcInput {
        require(op in OPERATORS)
        if (fresh) return CalcInput(continueFrom(result) + op)
        if (text.isEmpty()) return if (op == MINUS) copy(text = "$MINUS") else this
        val last = text.last()
        return when {
            last == '(' -> if (op == MINUS) copy(text = text + op) else this
            last in OPERATORS -> {
                val prev = text.getOrNull(text.length - 2)
                when {
                    // "5×" + "−" → "5×−" (negative operand)
                    op == MINUS && last != MINUS && last != '+' -> copy(text = text + op)
                    // "5×−" + "+" → "5+" (replace the whole operator run)
                    last == MINUS && prev != null && prev in OPERATORS -> copy(text = text.dropLast(2) + op)
                    // a lone leading "−" can only be replaced by nothing useful
                    text.length == 1 -> this
                    else -> copy(text = text.dropLast(1) + op)
                }
            }
            else -> copy(text = text + op)
        }
    }

    /** Postfix operators: "%" and "!". Only valid directly after an operand. */
    fun postfix(op: Char, result: BigDecimal? = null): CalcInput {
        require(op == '%' || op == '!')
        if (fresh) return CalcInput(continueFrom(result) + op)
        return if (endsWithOperand()) copy(text = text + op) else this
    }

    /** "x²" and "1/x" style suffixes, e.g. "^2". */
    fun suffix(s: String, result: BigDecimal? = null): CalcInput {
        if (fresh) return CalcInput(continueFrom(result) + s)
        return if (endsWithOperand()) copy(text = text + s) else this
    }

    /** sin, cos, tan, asin, acos, atan, ln, log, abs, or "√". Opens a parenthesis. */
    fun function(name: String): CalcInput {
        val token = if (name == "√") "√(" else "$name("
        if (fresh) return CalcInput(token)
        // "5" then "10ˣ" must not become "510^(": make the multiplication explicit
        val glue = if (token.first().isDigit() && text.lastOrNull()?.let { it.isDigit() || it == '.' } == true) "×" else ""
        return copy(text = text + glue + token)
    }

    /** π, e or Ans. */
    fun constant(c: String): CalcInput = if (fresh) CalcInput(c) else copy(text = text + c)

    fun open(): CalcInput = if (fresh) CalcInput("(") else copy(text = "$text(")

    fun close(): CalcInput =
        if (!fresh && openParens() > 0 && endsWithOperand()) copy(text = "$text)") else this

    /** One "( )" key: closes when a group is open and complete, otherwise opens one. */
    fun paren(): CalcInput = if (!fresh && openParens() > 0 && endsWithOperand()) close() else open()

    /** Flips the sign of the number being typed. */
    fun toggleSign(result: BigDecimal? = null): CalcInput {
        val base = if (fresh) CalcInput(continueFrom(result)) else this
        val t = base.text
        val start = t.length - base.trailingOperand().length
        val before = t.getOrNull(start - 1)
        val beforeThat = t.getOrNull(start - 2)
        val unaryMinus = before == MINUS && (beforeThat == null || beforeThat in OPERATORS || beforeThat == '(')
        val next = when {
            unaryMinus -> t.removeRange(start - 1, start)
            before == MINUS -> t.substring(0, start - 1) + "+" + t.substring(start)
            before == '+' -> t.substring(0, start - 1) + MINUS + t.substring(start)
            else -> t.substring(0, start) + MINUS + t.substring(start)
        }
        return CalcInput(next)
    }

    /** Deletes one key press: a whole "sin(" or "Ans" goes at once. */
    fun backspace(): CalcInput {
        if (text.isEmpty()) return CalcInput()
        val unit = TOKENS.firstOrNull { text.endsWith(it) }
        return CalcInput(text.dropLast(unit?.length ?: 1))
    }

    fun clear(): CalcInput = CalcInput()

    fun openParens(): Int = text.count { it == '(' } - text.count { it == ')' }

    fun endsWithOperand(): Boolean {
        val c = text.lastOrNull() ?: return false
        return c.isDigit() || c in ".πe)!%" || text.endsWith("Ans")
    }

    /** True when there is something to calculate beyond a single number (drives the live preview). */
    fun hasOperation(): Boolean =
        text.any { it in OPERATORS || it == '%' || it == '!' || it == '(' } || text.contains('π') ||
            text.contains("Ans") || text.contains('e')

    private fun trailingNumber(): String = text.takeLastWhile { it.isDigit() || it == '.' }

    /** The last operand: a number, a constant or a whole (...) group. */
    private fun trailingOperand(): String {
        if (text.endsWith(')')) {
            var depth = 0
            for (k in text.indices.reversed()) {
                when (text[k]) {
                    ')' -> depth++
                    '(' -> { depth--; if (depth == 0) {
                        // include a function name directly before the group: "sin(30)"
                        val fnStart = TOKENS.filter { it.endsWith('(') }
                            .firstOrNull { text.startsWith(it, k - it.length + 1) && k - it.length + 1 >= 0 }
                        return text.substring(if (fnStart != null) k - fnStart.length + 1 else k)
                    } }
                }
            }
            return text
        }
        if (text.endsWith("Ans")) return "Ans"
        if (text.endsWith('π') || text.endsWith('e')) return text.takeLast(1)
        return text.takeLastWhile { it.isDigit() || it == '.' || it == '%' || it == '!' }
    }

    companion object {
        const val MINUS = '−'
        const val OPERATORS = "+−×÷^"
        /** Multi-character tokens removed as one unit by backspace. */
        val TOKENS = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "abs(", "ln(", "log(", "√(", "Ans")

        /** Text used when an operator continues from the previous result. */
        fun continueFrom(result: BigDecimal?): String {
            if (result == null) return "0"
            val p = CalcEngine.plain(result)
            return if (p.length <= 24) p else "Ans"
        }
    }
}
