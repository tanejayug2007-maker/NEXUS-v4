package com.nexus.launcher.apps.calculator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

enum class AngleMode { DEG, RAD }

enum class CalcError(val message: String) {
    SYNTAX("Invalid expression"),
    DIVIDE_BY_ZERO("Can't divide by zero"),
    DOMAIN("Not defined for this input"),
    OVERFLOW("Number too large"),
}

sealed interface CalcResult {
    data class Value(val value: BigDecimal) : CalcResult
    data class Error(val error: CalcError) : CalcResult
}

/**
 * Exact calculator engine used by NEXUS Calculator.
 *
 * Arithmetic (+ − × ÷, integer powers, %, !) is done in [BigDecimal] with 34 significant digits,
 * so 0.1 + 0.2 is exactly 0.3. Transcendental functions go through Double and are rounded to 15
 * significant digits, which turns sin(180°) = 1.2e-16 into a clean 0.
 *
 * Grammar (highest binding last):
 *   expr    := term (('+' | '−') term)*         a + b%  means  a + a·b/100
 *   term    := unary (('×' | '÷') unary | implicit-× unary)*
 *   unary   := ('−' | '+') unary | power
 *   power   := postfix ('^' unary)?              right-associative, −2^2 = −4
 *   postfix := primary ('!' | '%')*
 *   primary := number | π | e | ans | func '(' expr ')' | '√' unary | '(' expr ')'
 * Missing closing parentheses at the end are closed automatically, like most calculators.
 */
object CalcEngine {
    val MC: MathContext = MathContext.DECIMAL128
    private val DOUBLE_MC = MathContext(15, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)
    private const val MAX_FACTORIAL = 3000
    private const val MAX_EXPONENT = 9999

    /** Function names the parser understands, longest first so "asin" wins over "sin". */
    val FUNCTIONS = listOf("asin", "acos", "atan", "sqrt", "sin", "cos", "tan", "abs", "ln", "log")

    fun evaluate(expr: String, angle: AngleMode = AngleMode.DEG, ans: BigDecimal? = null): CalcResult {
        if (expr.isBlank()) return CalcResult.Error(CalcError.SYNTAX)
        return try {
            val tokens = tokenize(expr) ?: return CalcResult.Error(CalcError.SYNTAX)
            val v = Parser(tokens, angle, ans).run()
            if (v.abs() > BigDecimal.ONE.scaleByPowerOfTen(MAX_EXPONENT)) CalcResult.Error(CalcError.OVERFLOW)
            else CalcResult.Value(normalize(v))
        } catch (e: CalcException) {
            CalcResult.Error(e.error)
        } catch (e: ArithmeticException) {
            CalcResult.Error(CalcError.OVERFLOW)
        }
    }

    /**
     * Human-readable result: up to [maxDigits] significant digits, thousands grouping and
     * scientific notation ("1.5E+20") for very large or very small magnitudes.
     */
    fun format(v: BigDecimal, maxDigits: Int = 14, group: Boolean = true): String {
        if (v.signum() == 0) return "0"
        val r = v.round(MathContext(maxDigits, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val exponent = r.precision() - r.scale() - 1 // position of the leading digit
        if (exponent >= maxDigits || exponent < -7) {
            val unscaled = r.unscaledValue().abs().toString()
            val mantissa = if (unscaled.length == 1) unscaled else unscaled[0] + "." + unscaled.substring(1)
            return (if (r.signum() < 0) "−" else "") + mantissa + "E" + (if (exponent >= 0) "+" else "−") + abs(exponent)
        }
        val plain = r.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = plain.substringAfter('.', "")
        val grouped = if (group) intPart.reversed().chunked(3).joinToString(",").reversed() else intPart
        return (if (r.signum() < 0) "−" else "") + grouped + (if (frac.isEmpty()) "" else ".$frac")
    }

    /** Plain, re-parsable form of a value (no grouping, no scientific notation). */
    fun plain(v: BigDecimal): String {
        val r = v.round(MathContext(16, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        val s = r.abs().toPlainString()
        return (if (r.signum() < 0) "−" else "") + s
    }

    private fun normalize(v: BigDecimal): BigDecimal {
        val r = v.round(MC)
        return if (r.signum() == 0) BigDecimal.ZERO else r.stripTrailingZeros()
    }

    // ---- tokens ---------------------------------------------------------------------------

    internal sealed interface Tok {
        data class Num(val v: BigDecimal) : Tok
        data class Op(val c: Char) : Tok          // + - * / ^ % ! ( )
        data class Fn(val name: String) : Tok     // sin cos … sqrt; "√" maps to sqrt
        data class Const(val name: String) : Tok  // pi e ans
    }

    internal fun tokenize(src: String): List<Tok>? {
        val s = src.lowercase()
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() || c == ',' -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    var dots = 0
                    // grouping commas belong to the number: "1,234.5"
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) { if (s[i] == '.') dots++; i++ }
                    val text = s.substring(start, i).replace(",", "")
                    if (dots > 1 || text == ".") return null
                    out += Tok.Num(BigDecimal(if (text.endsWith('.')) text.dropLast(1) else text))
                }
                c == '+' -> { out += Tok.Op('+'); i++ }
                c == '-' || c == '−' || c == '–' -> { out += Tok.Op('-'); i++ }
                c == '*' || c == '×' || c == '·' -> { out += Tok.Op('*'); i++ }
                c == '/' || c == '÷' -> { out += Tok.Op('/'); i++ }
                c == '^' || c == '%' || c == '!' || c == '(' || c == ')' -> { out += Tok.Op(c); i++ }
                c == '√' -> { out += Tok.Fn("sqrt"); i++ }
                c == 'π' -> { out += Tok.Const("pi"); i++ }
                s.startsWith("pi", i) -> { out += Tok.Const("pi"); i += 2 }
                s.startsWith("ans", i) -> { out += Tok.Const("ans"); i += 3 }
                else -> {
                    val fn = FUNCTIONS.firstOrNull { s.startsWith(it, i) }
                    when {
                        fn != null -> { out += Tok.Fn(fn); i += fn.length }
                        c == 'e' -> { out += Tok.Const("e"); i++ }
                        else -> return null
                    }
                }
            }
        }
        return out
    }

    // ---- parser ---------------------------------------------------------------------------

    private class CalcException(val error: CalcError) : RuntimeException(error.message)

    /** A parsed operand; [pct] is true when it is a bare "n%" (affects a + b%). */
    private class Num(val v: BigDecimal, val pct: Boolean = false)

    private class Parser(val t: List<Tok>, val angle: AngleMode, val ans: BigDecimal?) {
        var i = 0

        fun fail(e: CalcError = CalcError.SYNTAX): Nothing = throw CalcException(e)
        fun peek(): Tok? = t.getOrNull(i)
        fun isOp(c: Char) = (peek() as? Tok.Op)?.c == c

        fun run(): BigDecimal {
            val v = expr().v
            if (i != t.size) fail()
            return v
        }

        fun expr(): Num {
            var acc = term()
            while (true) {
                val plus = isOp('+')
                if (!plus && !isOp('-')) return acc
                i++
                val rhs = term()
                // 200 + 10% = 220 (percentage of the left operand), like a desk calculator
                val amount = if (rhs.pct) acc.v.multiply(rhs.v, MC) else rhs.v
                acc = Num(if (plus) acc.v.add(amount, MC) else acc.v.subtract(amount, MC))
            }
        }

        fun term(): Num {
            var acc = unary()
            while (true) {
                acc = when {
                    isOp('*') -> { i++; Num(acc.v.multiply(unary().v, MC)) }
                    isOp('/') -> {
                        i++
                        val d = unary().v
                        if (d.signum() == 0) fail(CalcError.DIVIDE_BY_ZERO)
                        Num(acc.v.divide(d, MC))
                    }
                    startsPrimary() -> Num(acc.v.multiply(unary().v, MC)) // implicit: 2π, 3(4), 2sin30
                    else -> return acc
                }
            }
        }

        fun startsPrimary(): Boolean = when (val p = peek()) {
            is Tok.Num, is Tok.Fn, is Tok.Const -> true
            is Tok.Op -> p.c == '('
            null -> false
        }

        fun unary(): Num = when {
            isOp('-') -> { i++; val n = unary(); Num(n.v.negate(), n.pct) }
            isOp('+') -> { i++; unary() }
            else -> power()
        }

        fun power(): Num {
            val base = postfix()
            if (!isOp('^')) return base
            i++
            return Num(pow(base.v, unary().v))
        }

        fun postfix(): Num {
            var n = primary()
            while (true) {
                n = when {
                    isOp('!') -> { i++; Num(factorial(n.v)) }
                    isOp('%') -> { i++; Num(n.v.divide(HUNDRED, MC), pct = true) }
                    else -> return n
                }
            }
        }

        fun primary(): Num {
            return when (val tok = peek() ?: fail()) {
                is Tok.Num -> { i++; Num(tok.v) }
                is Tok.Const -> {
                    i++
                    Num(
                        when (tok.name) {
                            "pi" -> BigDecimal("3.141592653589793238462643383279503")
                            "e" -> BigDecimal("2.718281828459045235360287471352662")
                            else -> ans ?: BigDecimal.ZERO
                        }
                    )
                }
                is Tok.Fn -> {
                    i++
                    val arg = if (tok.name == "sqrt" && !isOp('(')) unary().v else group()
                    Num(apply(tok.name, arg))
                }
                is Tok.Op -> if (tok.c == '(') Num(group()) else fail()
            }
        }

        /** "(" expr ")" — a closing parenthesis missing at the very end is implied. */
        fun group(): BigDecimal {
            if (!isOp('(')) fail()
            i++
            val v = expr().v
            if (isOp(')')) i++ else if (i != t.size) fail()
            return v
        }

        fun apply(fn: String, x: BigDecimal): BigDecimal = when (fn) {
            "sqrt" -> sqrt(x)
            "abs" -> x.abs()
            "ln" -> { if (x.signum() <= 0) fail(CalcError.DOMAIN); viaDouble(kotlin.math.ln(x.toDouble())) }
            "log" -> { if (x.signum() <= 0) fail(CalcError.DOMAIN); viaDouble(kotlin.math.log10(x.toDouble())) }
            "sin" -> viaDouble(kotlin.math.sin(toRad(x)))
            "cos" -> viaDouble(kotlin.math.cos(toRad(x)))
            "tan" -> {
                // tan is undefined at odd multiples of 90°; exact check in degrees, epsilon in radians
                if (angle == AngleMode.DEG) {
                    if (x.remainder(BigDecimal(180)).abs().compareTo(BigDecimal(90)) == 0) fail(CalcError.DOMAIN)
                } else if (abs(kotlin.math.cos(x.toDouble())) < 1e-15) fail(CalcError.DOMAIN)
                viaDouble(kotlin.math.tan(toRad(x)))
            }
            "asin" -> { if (x.abs() > BigDecimal.ONE) fail(CalcError.DOMAIN); fromRad(kotlin.math.asin(x.toDouble())) }
            "acos" -> { if (x.abs() > BigDecimal.ONE) fail(CalcError.DOMAIN); fromRad(kotlin.math.acos(x.toDouble())) }
            "atan" -> fromRad(kotlin.math.atan(x.toDouble()))
            else -> fail()
        }

        fun toRad(x: BigDecimal): Double = if (angle == AngleMode.DEG) Math.toRadians(x.toDouble()) else x.toDouble()
        fun fromRad(r: Double): BigDecimal = viaDouble(if (angle == AngleMode.DEG) Math.toDegrees(r) else r)

        fun viaDouble(d: Double): BigDecimal {
            if (d.isNaN()) fail(CalcError.DOMAIN)
            if (d.isInfinite()) fail(CalcError.OVERFLOW)
            if (abs(d) < 1e-14) return BigDecimal.ZERO
            return BigDecimal(d).round(DOUBLE_MC)
        }

        fun sqrt(x: BigDecimal): BigDecimal {
            if (x.signum() < 0) fail(CalcError.DOMAIN)
            if (x.signum() == 0) return BigDecimal.ZERO
            // Newton's method from a double-precision guess (BigDecimal.sqrt needs API 33)
            var g = BigDecimal(kotlin.math.sqrt(x.toDouble()), MC)
            if (g.signum() == 0) g = BigDecimal.ONE
            val two = BigDecimal(2)
            repeat(6) { g = g.add(x.divide(g, MC), MC).divide(two, MC) }
            return g
        }

        fun pow(base: BigDecimal, exp: BigDecimal): BigDecimal {
            val integral = exp.signum() == 0 || exp.stripTrailingZeros().scale() <= 0
            if (integral && exp.abs() <= BigDecimal(MAX_EXPONENT)) {
                val n = exp.toInt()
                if (base.signum() == 0 && n < 0) fail(CalcError.DIVIDE_BY_ZERO)
                return base.pow(n, MC)
            }
            if (base.signum() < 0) fail(CalcError.DOMAIN) // e.g. (−8)^(1/3): not real in general
            val d = Math.pow(base.toDouble(), exp.toDouble())
            return viaDouble(d)
        }

        fun factorial(x: BigDecimal): BigDecimal {
            if (x.signum() < 0 || x.stripTrailingZeros().scale() > 0) fail(CalcError.DOMAIN)
            if (x > BigDecimal(MAX_FACTORIAL)) fail(CalcError.OVERFLOW)
            var r = BigInteger.ONE
            for (k in 2..x.toInt()) r = r.multiply(BigInteger.valueOf(k.toLong()))
            return BigDecimal(r)
        }
    }
}
