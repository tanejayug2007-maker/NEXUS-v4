package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class GlyphsTest {
    private fun stroke(vararg p: Pair<Float, Float>, steps: Int = 12): List<Glyphs.P> {
        val out = mutableListOf<Glyphs.P>()
        for (i in 1 until p.size) for (k in 0 until steps) {
            val t = k / steps.toFloat()
            out += Glyphs.P(p[i - 1].first + (p[i].first - p[i - 1].first) * t, p[i - 1].second + (p[i].second - p[i - 1].second) * t)
        }
        out += Glyphs.P(p.last().first, p.last().second)
        return out
    }

    @Test fun recognisesBuiltInCircleAtAnotherSizeAndPosition() {
        val pts = (0..60).map { i ->
            val a = -Math.PI / 2 + i / 60.0 * 2 * Math.PI
            Glyphs.P((500 + 40 * cos(a)).toFloat(), (300 + 40 * sin(a)).toFloat())
        }
        val hit = Glyphs.recognize(Glyphs.normalize(pts)!!, Glyphs.builtIns())
        assertNotNull(hit)
        assertEquals("builtin:circle", hit!!.first.id)
    }

    @Test fun caretAndVeeAreDistinguished() {
        val caret = Glyphs.normalize(stroke(0f to 300f, 150f to 0f, 300f to 300f))!!
        val vee = Glyphs.normalize(stroke(0f to 0f, 150f to 300f, 300f to 0f))!!
        assertEquals("builtin:caret", Glyphs.recognize(caret, Glyphs.builtIns())!!.first.id)
        assertEquals("builtin:vee", Glyphs.recognize(vee, Glyphs.builtIns())!!.first.id)
    }

    @Test fun userTemplateWins() {
        val s = stroke(0f to 0f, 200f to 0f, 200f to 200f, 0f to 200f)   // "C" reversed / box
        val tpl = GlyphBinding("u1", "Mail", "app:x", Glyphs.normalize(s)!!)
        val again = Glyphs.normalize(stroke(10f to 12f, 205f to 5f, 210f to 190f, 5f to 210f))!!
        assertEquals("u1", Glyphs.recognize(again, listOf(tpl) + Glyphs.builtIns())!!.first.id)
    }

    @Test fun tinyStrokeRejected() {
        assertNull(Glyphs.normalize(stroke(0f to 0f, 3f to 3f)))
    }
}
