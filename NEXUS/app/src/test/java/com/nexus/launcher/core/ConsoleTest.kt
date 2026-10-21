package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleTest {
    @Test fun parsesCommands() {
        assertEquals(Cmd.Open("spotify"), ConsoleParser.parse("open spotify"))
        assertEquals(Cmd.Flash(true), ConsoleParser.parse("flash on"))
        assertEquals(Cmd.Bright(40), ConsoleParser.parse("bright 40%"))
        assertEquals(Cmd.Mode(InterfaceMode.ARCHITECT), ConsoleParser.parse("mode Architect"))
        assertEquals(Cmd.Timer(330), ConsoleParser.parse("timer 5m30s"))
        assertEquals(Cmd.Alarm(7, 30), ConsoleParser.parse("alarm 07:30"))
        assertEquals(Cmd.Help, ConsoleParser.parse("HELP"))
        assertTrue(ConsoleParser.parse("frobnicate") is Cmd.Unknown)
        assertTrue(ConsoleParser.parse("2+2") is Cmd.Calc)
    }

    @Test fun durations() {
        assertEquals(90, ConsoleParser.duration("90s"))
        assertEquals(5400, ConsoleParser.duration("1h30m"))
        assertEquals(300, ConsoleParser.duration("5"))
        assertEquals(null, ConsoleParser.duration("soon"))
    }

    @Test fun calculator() {
        assertEquals(14.0, Calc.eval("2+3*4"), 1e-9)
        assertEquals(20.0, Calc.eval("(2+3)*4"), 1e-9)
        assertEquals(256.0, Calc.eval("2^2^3"), 1e-9)
        assertEquals(-3.0, Calc.eval("-(1+2)"), 1e-9)
        assertEquals(3.0, Calc.eval("sqrt9"), 1e-9)
        assertEquals("0.5", Calc.format(Calc.eval("1/2")))
        assertEquals("1024", Calc.format(Calc.eval("2^10")))
    }

    @Test fun suggestions() {
        assertTrue(ConsoleParser.suggestions("fl").contains("flash"))
        assertTrue(ConsoleParser.suggestions("open sp").isEmpty())
    }

    @Test fun predictorPrefersCurrentHour() {
        var u = emptyMap<String, List<Int>>()
        repeat(5) { u = Predictor.record(u, "music", 8) }
        repeat(5) { u = Predictor.record(u, "mail", 20) }
        assertEquals("music", Predictor.rank(u, 8, 2).first())
        assertEquals("mail", Predictor.rank(u, 20, 2).first())
    }
}
