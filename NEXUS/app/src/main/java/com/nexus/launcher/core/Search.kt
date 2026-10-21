package com.nexus.launcher.core

import java.text.Normalizer

/** Ranks items by how well their label matches the query. Pure, so it is unit tested. */
object Search {

    private val diacritics = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        diacritics.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    fun score(label: String, query: String): Int {
        val l = normalize(label)
        val q = normalize(query)
        if (q.isEmpty()) return 0
        if (l == q) return 100
        if (l.startsWith(q)) return 85
        val words = l.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 70
        val initials = words.joinToString("") { it.take(1) }
        if (q.length >= 2 && initials.startsWith(q)) return 60
        if (l.contains(q)) return 45
        // subsequence match: "gml" -> "Gmail"
        var i = 0
        for (c in l) if (i < q.length && c == q[i]) i++
        return if (i == q.length && q.length >= 2) 20 else 0
    }

    fun <T> rank(items: List<T>, query: String, label: (T) -> String): List<T> {
        if (normalize(query).isEmpty()) return items
        return items.map { it to score(label(it), query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<T, Int>> { it.second }.thenBy { label(it.first).lowercase() })
            .map { it.first }
    }
}
