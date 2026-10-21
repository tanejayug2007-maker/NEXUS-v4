package com.nexus.launcher.apps.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalcInputTest {

    private fun type(keys: String, start: CalcInput = CalcInput()): CalcInput = keys.fold(start) { s, c ->
        when {
            c.isDigit() -> s.digit(c)
            c == '.' -> s.dot()
            c in CalcInput.OPERATORS -> s.operator(c)
            c == '%' || c == '!' -> s.postfix(c)
            c == '(' -> s.open()
            c == ')' -> s.close()
            c == '<' -> s.backspace()
            c == '~' -> s.toggleSign()
            else -> error("no key for $c")
        }
    }

    @Test fun leadingZeroAndDecimals() {
        assertEquals("7", type("007").text)
        assertEquals("0.5", type(".5").text)
        assertEquals("1.25", type("1.2.5").text)
        assertEquals("3+0.", type("3+.").text)
        assertEquals("10.05", type("10.05").text)
    }

    @Test fun operatorReplacementAndNegatives() {
        assertEquals("5×", type("5+×").text)
        assertEquals("5×−", type("5×−").text)
        assertEquals("5+", type("5×−+").text)
        assertEquals("5−", type("5+−").text)
        assertEquals("−", type("−").text)
        assertEquals("", type("×").text)
        assertEquals("(−", type("(−").text)
        assertEquals("(", type("(×").text)
    }

    @Test fun postfixOnlyAfterOperand() {
        assertEquals("5!", type("5!").text)
        assertEquals("5+", type("5+%").text)
    }

    @Test fun parentheses() {
        assertEquals("(2+3)", type("(2+3)").text)
        assertEquals("2", type("2)").text) // nothing to close
        assertEquals("(2)", CalcInput("(2").paren().text)
        assertEquals("2+(", CalcInput("2+").paren().text)
        assertEquals(1, CalcInput("((2)").openParens())
    }

    @Test fun backspaceRemovesWholeTokens() {
        assertEquals("2+", CalcInput("2+sin(").backspace().text)
        assertEquals("2×", CalcInput("2×Ans").backspace().text)
        assertEquals("2+", CalcInput("2+asin(").backspace().text)
        assertEquals("1", type("12<").text)
        assertEquals("", CalcInput().backspace().text)
    }

    @Test fun toggleSign() {
        assertEquals("−5", type("5~").text)
        assertEquals("5", type("5~~").text)
        assertEquals("3−5", type("3+5~").text)
        assertEquals("3+5", type("3−5~").text)
        assertEquals("3×−5", type("3×5~").text)
        assertEquals("3×5", type("3×5~~").text)
        assertEquals("−sin(30)", CalcInput("sin(30)").toggleSign().text)
    }

    @Test fun afterEquals() {
        val done = CalcInput("42", fresh = true)
        assertEquals("7", done.digit('7').text)
        assertFalse(done.digit('7').fresh)
        assertEquals("42+", done.operator('+', BigDecimal(42)).text)
        assertEquals("0.", done.dot().text)
        assertEquals("sin(", done.function("sin").text)
        assertEquals("Ans+", CalcInput("x", fresh = true).operator('+', BigDecimal("1E40")).text)
    }

    @Test fun functionsGlueToNumbers() {
        assertEquals("5×10^(", CalcInput("5").function("10^").text)
        assertEquals("5e^(", CalcInput("5").function("e^").text)
        assertEquals("2+√(", CalcInput("2+").function("√").text)
    }

    @Test fun livePreviewTrigger() {
        assertFalse(CalcInput("123").hasOperation())
        assertTrue(CalcInput("1+2").hasOperation())
        assertTrue(CalcInput("2π").hasOperation())
    }

    @Test fun everyTypedExpressionEvaluates() {
        listOf("12×(3−1)" to "24", "0.1+0.2" to "0.3", "2^10÷4" to "256").forEach { (keys, expected) ->
            val r = CalcEngine.evaluate(type(keys).text) as CalcResult.Value
            assertEquals(expected, CalcEngine.format(r.value))
        }
    }
}
