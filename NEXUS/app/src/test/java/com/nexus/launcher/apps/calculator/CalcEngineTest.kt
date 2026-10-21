package com.nexus.launcher.apps.calculator

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CalcEngineTest {

    private fun eval(s: String, angle: AngleMode = AngleMode.DEG, ans: BigDecimal? = null): String =
        when (val r = CalcEngine.evaluate(s, angle, ans)) {
            is CalcResult.Value -> CalcEngine.format(r.value, group = false).replace('−', '-')
            is CalcResult.Error -> r.error.name
        }

    @Test fun exactDecimals() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.1", eval("0.3−0.2"))
        assertEquals("1.21", eval("1.1×1.1"))
        assertEquals("0.33333333333333", eval("1÷3"))
        assertEquals("1", eval("1÷3×3"))
    }

    @Test fun precedenceAndAssociativity() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("-4", eval("−2^2"))
        assertEquals("4", eval("(−2)^2"))
        assertEquals("0.5", eval("2^−1"))
        assertEquals("2", eval("8÷2÷2"))
        assertEquals("3", eval("10−5−2"))
    }

    @Test fun implicitMultiplicationAndAutoClose() {
        assertEquals("6", eval("2(3)"))
        assertEquals("6.2831853071796", eval("2π"))
        assertEquals("20", eval("(2+3)(4)"))
        assertEquals("20", eval("(2+3)×(4"))
        assertEquals("6", eval("2√9"))
    }

    @Test fun percent() {
        assertEquals("0.5", eval("50%"))
        assertEquals("220", eval("200+10%"))
        assertEquals("180", eval("200−10%"))
        assertEquals("20", eval("200×10%"))
        assertEquals("5.1", eval("10%+5"))
    }

    @Test fun scientificDegrees() {
        assertEquals("0.5", eval("sin(30)"))
        assertEquals("0", eval("sin(180)"))
        assertEquals("0", eval("cos(90)"))
        assertEquals("1", eval("tan(45)"))
        assertEquals("30", eval("asin(0.5)"))
        assertEquals("DOMAIN", eval("tan(90)"))
        assertEquals("DOMAIN", eval("asin(2)"))
    }

    @Test fun scientificRadians() {
        assertEquals("0", eval("sin(π)", AngleMode.RAD))
        assertEquals("1", eval("cos(0)", AngleMode.RAD))
        assertEquals("1.5707963267949", eval("asin(1)", AngleMode.RAD))
    }

    @Test fun logsRootsFactorials() {
        assertEquals("3", eval("log(1000)"))
        assertEquals("1", eval("ln(e)"))
        assertEquals("3", eval("√(9)"))
        assertEquals("1.4142135623731", eval("√2"))
        assertEquals("120", eval("5!"))
        assertEquals("1", eval("0!"))
        assertEquals("3628800", eval("10!"))
        assertEquals("1024", eval("2^10"))
        assertEquals("1000", eval("10^(3"))
    }

    @Test fun errors() {
        assertEquals("DIVIDE_BY_ZERO", eval("5÷0"))
        assertEquals("DIVIDE_BY_ZERO", eval("0^−1"))
        assertEquals("DOMAIN", eval("√(−4)"))
        assertEquals("DOMAIN", eval("ln(0)"))
        assertEquals("DOMAIN", eval("2.5!"))
        assertEquals("DOMAIN", eval("(−8)^(1÷3)"))
        assertEquals("OVERFLOW", eval("5000!"))
        assertEquals("OVERFLOW", eval("10^99999"))
        assertEquals("SYNTAX", eval("1..2"))
        assertEquals("SYNTAX", eval("2+"))
        assertEquals("SYNTAX", eval("×3"))
        assertEquals("SYNTAX", eval(")"))
        assertEquals("SYNTAX", eval("abc"))
        assertEquals("SYNTAX", eval(""))
    }

    @Test fun answerConstant() {
        assertEquals("84", eval("Ans×2", ans = BigDecimal(42)))
        assertEquals("0", eval("Ans"))
    }

    @Test fun formatting() {
        assertEquals("1,234,567.89", CalcEngine.format(BigDecimal("1234567.89")))
        assertEquals("−1,000", CalcEngine.format(BigDecimal("-1000")))
        assertEquals("1.5E+20", CalcEngine.format(BigDecimal("1.5E20")))
        assertEquals("2.5E−8", CalcEngine.format(BigDecimal("0.000000025")))
        assertEquals("0.0000025", CalcEngine.format(BigDecimal("0.0000025")))
        assertEquals("0", CalcEngine.format(BigDecimal("0.000")))
        assertEquals("-12.5", CalcEngine.plain(BigDecimal("-12.50")).replace('−', '-'))
    }

    @Test fun formattedResultParsesBack() {
        // grouped output must be re-parsable (commas are ignored by the tokenizer)
        assertEquals("1234567.89", eval("1,234,567.89"))
    }
}
