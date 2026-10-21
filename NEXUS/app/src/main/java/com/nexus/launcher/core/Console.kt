package com.nexus.launcher.core

import java.util.Locale

/** NEXUS//SHELL commands. Parsing is pure (unit tested); execution lives in the UI layer. */
sealed interface Cmd {
    data class Open(val query: String) : Cmd
    data class Apps(val filter: String) : Cmd
    data class Web(val query: String) : Cmd
    data class Flash(val on: Boolean?) : Cmd
    data class Dnd(val on: Boolean?) : Cmd
    data class Bright(val percent: Int?) : Cmd
    data class Vol(val percent: Int?) : Cmd
    data class Panel(val which: String) : Cmd
    data class Theme(val name: String) : Cmd
    data class Mode(val mode: InterfaceMode?) : Cmd
    data class Fx(val name: String) : Cmd
    data class Calc(val expr: String) : Cmd
    data class Timer(val seconds: Int) : Cmd
    data class Alarm(val hour: Int, val minute: Int) : Cmd
    data class Echo(val text: String) : Cmd
    data class Unknown(val raw: String) : Cmd
    data class Bad(val message: String) : Cmd
    data object Time : Cmd
    data object Battery : Cmd
    data object Sys : Cmd
    data object Help : Cmd
    data object Clear : Cmd
    data object Glyph : Cmd
    data object Train : Cmd
    data object Studio : Cmd
    data object Lock : Cmd
    data object Shade : Cmd
    data object Qs : Cmd
    data object Drawer : Cmd
    data object Matrix : Cmd
    data object Exit : Cmd
}

object ConsoleParser {

    /** name to one-line help, shown by `help` and used for autocomplete. */
    val commands: List<Pair<String, String>> = listOf(
        "open" to "open <app>        launch best match",
        "apps" to "apps [filter]     list installed modules",
        "web" to "web <query>       search the web",
        "calc" to "calc <expr>       + - * / ^ % ( ) sqrt pi",
        "flash" to "flash [on|off]    torch",
        "dnd" to "dnd [on|off]      do not disturb",
        "bright" to "bright <0-100>    screen brightness",
        "vol" to "vol <0-100>       media volume",
        "wifi" to "wifi              wi-fi panel",
        "net" to "net               internet panel",
        "bt" to "bt                bluetooth settings",
        "nfc" to "nfc               nfc panel",
        "theme" to "theme <name>      apply a preset",
        "mode" to "mode <civilian|operator|architect>",
        "fx" to "fx <launch preset>",
        "timer" to "timer <5m|90s|1h30m>",
        "alarm" to "alarm <hh:mm>",
        "time" to "time              clock + date",
        "battery" to "battery           power cell telemetry",
        "sys" to "sys               device diagnostics",
        "glyph" to "glyph             open glyph pad",
        "train" to "train             teach a new glyph",
        "drawer" to "drawer            app index",
        "studio" to "studio            customization studio",
        "lock" to "lock              lock screen (helper)",
        "shade" to "shade             system notifications",
        "qs" to "qs                system quick settings",
        "matrix" to "matrix            ...",
        "echo" to "echo <text>",
        "clear" to "clear             wipe log",
        "exit" to "exit              close shell",
        "help" to "help              this list",
    )

    fun parse(input: String): Cmd {
        val line = input.trim()
        if (line.isEmpty()) return Cmd.Bad("empty input")
        val head = line.substringBefore(' ').lowercase(Locale.ROOT)
        val rest = line.substringAfter(' ', "").trim()
        if (head.first().isDigit() || head.first() == '(' || head.first() == '-' || head.startsWith("sqrt")) return Cmd.Calc(line)
        return when (head) {
            "open", "launch", "run" -> if (rest.isEmpty()) Cmd.Bad("usage: open <app>") else Cmd.Open(rest)
            "apps", "ls" -> Cmd.Apps(rest)
            "web", "search", "google" -> if (rest.isEmpty()) Cmd.Bad("usage: web <query>") else Cmd.Web(rest)
            "calc", "=" -> if (rest.isEmpty()) Cmd.Bad("usage: calc <expr>") else Cmd.Calc(rest)
            "flash", "torch" -> Cmd.Flash(onOff(rest))
            "dnd" -> Cmd.Dnd(onOff(rest))
            "bright", "brightness" -> Cmd.Bright(percent(rest))
            "vol", "volume" -> Cmd.Vol(percent(rest))
            "wifi", "net", "bt", "bluetooth", "nfc", "data" -> Cmd.Panel(head)
            "theme" -> if (rest.isEmpty()) Cmd.Bad("usage: theme <name>") else Cmd.Theme(rest)
            "mode" -> Cmd.Mode(InterfaceMode.entries.firstOrNull { it.name.equals(rest, ignoreCase = true) })
            "fx" -> Cmd.Fx(rest)
            "timer" -> duration(rest)?.let { Cmd.Timer(it) } ?: Cmd.Bad("usage: timer 5m | 90s | 1h30m")
            "alarm" -> alarm(rest) ?: Cmd.Bad("usage: alarm 07:30")
            "time", "date", "clock" -> Cmd.Time
            "battery", "bat", "power" -> Cmd.Battery
            "sys", "sysinfo", "diag" -> Cmd.Sys
            "help", "?" -> Cmd.Help
            "clear", "cls" -> Cmd.Clear
            "glyph", "glyphs" -> Cmd.Glyph
            "train" -> Cmd.Train
            "studio", "settings", "config" -> Cmd.Studio
            "lock" -> Cmd.Lock
            "shade", "notifications" -> Cmd.Shade
            "qs" -> Cmd.Qs
            "drawer", "index" -> Cmd.Drawer
            "matrix" -> Cmd.Matrix
            "echo" -> Cmd.Echo(rest)
            "exit", "quit", "close" -> Cmd.Exit
            else -> Cmd.Unknown(line)
        }
    }

    fun suggestions(input: String): List<String> {
        val t = input.trimStart().lowercase(Locale.ROOT)
        if (t.isEmpty() || t.contains(' ')) return emptyList()
        return commands.map { it.first }.filter { it.startsWith(t) && it != t }.take(6)
    }

    private fun onOff(s: String): Boolean? = when (s.lowercase(Locale.ROOT)) {
        "on", "1", "true", "enable" -> true
        "off", "0", "false", "disable" -> false
        else -> null
    }

    private fun percent(s: String): Int? = s.removeSuffix("%").trim().toIntOrNull()?.coerceIn(0, 100)

    /** "5m", "90s", "1h30m", "2h", "45" (minutes). */
    fun duration(s: String): Int? {
        val t = s.lowercase(Locale.ROOT).replace(" ", "")
        if (t.isEmpty()) return null
        t.toIntOrNull()?.let { return it * 60 }
        val m = Regex("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$").find(t) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val mi = m.groupValues[2].toIntOrNull() ?: 0
        val se = m.groupValues[3].toIntOrNull() ?: 0
        val total = h * 3600 + mi * 60 + se
        return if (total in 1..86_400) total else null
    }

    private fun alarm(s: String): Cmd.Alarm? {
        val m = Regex("^(\\d{1,2})[:.](\\d{2})$").find(s.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = m.groupValues[2].toInt()
        return if (h in 0..23 && mi in 0..59) Cmd.Alarm(h, mi) else null
    }
}

/** Small recursive-descent calculator: + - * / % ^, parentheses, unary minus, sqrt, pi, e. */
object Calc {
    fun eval(expr: String): Double = Parser(expr.lowercase(Locale.ROOT).replace(" ", "").replace("×", "*").replace("÷", "/")).run()

    fun format(v: Double): String = when {
        v.isNaN() || v.isInfinite() -> "undefined"
        v == Math.rint(v) && kotlin.math.abs(v) < 1e15 -> v.toLong().toString()
        else -> "%.10f".format(Locale.ROOT, v).trimEnd('0').trimEnd('.')
    }

    private class Parser(val s: String) {
        var i = 0

        fun run(): Double {
            val v = expr()
            require(i == s.length) { "unexpected '${s.getOrNull(i)}'" }
            return v
        }

        fun expr(): Double {
            var v = term()
            while (i < s.length) {
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
            return v
        }

        fun term(): Double {
            var v = power()
            while (i < s.length) {
                when (s[i]) {
                    '*' -> { i++; v *= power() }
                    '/' -> { i++; v /= power() }
                    '%' -> { i++; v %= power() }
                    else -> return v
                }
            }
            return v
        }

        fun power(): Double {
            val base = unary()
            if (i < s.length && s[i] == '^') { i++; return Math.pow(base, power()) }
            return base
        }

        fun unary(): Double {
            if (i < s.length && s[i] == '-') { i++; return -unary() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            return atom()
        }

        fun atom(): Double {
            require(i < s.length) { "incomplete expression" }
            if (s.startsWith("sqrt", i)) { i += 4; return Math.sqrt(atom()) }
            if (s.startsWith("pi", i)) { i += 2; return Math.PI }
            if (s[i] == 'e' && (i + 1 >= s.length || !s[i + 1].isLetter())) { i++; return Math.E }
            if (s[i] == '(') {
                i++
                val v = expr()
                require(i < s.length && s[i] == ')') { "missing ')'" }
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            require(i > start) { "unexpected '${s[i]}'" }
            return s.substring(start, i).toDouble()
        }
    }
}

/** Time-of-day launch prediction from local, on-device counts. */
object Predictor {
    /** usage: app id → 24 hourly counts. */
    fun rank(usage: Map<String, List<Int>>, hour: Int, n: Int): List<String> =
        usage.entries.map { (id, counts) ->
            fun c(h: Int) = counts.getOrElse(((h % 24) + 24) % 24) { 0 }.toDouble()
            id to (c(hour) * 3.0 + (c(hour - 1) + c(hour + 1)) * 1.5 + counts.sum() * 0.08)
        }.filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(n)
            .map { it.first }

    fun record(usage: Map<String, List<Int>>, id: String, hour: Int): Map<String, List<Int>> {
        val counts = (usage[id] ?: List(24) { 0 }).toMutableList()
        while (counts.size < 24) counts += 0
        counts[hour.coerceIn(0, 23)] = (counts[hour.coerceIn(0, 23)] + 1).coerceAtMost(10_000)
        val next = usage + (id to counts.toList())
        // keep the store small: at most 200 apps, drop the least used
        return if (next.size <= 200) next else next.entries.sortedByDescending { it.value.sum() }.take(200).associate { it.key to it.value }
    }
}
