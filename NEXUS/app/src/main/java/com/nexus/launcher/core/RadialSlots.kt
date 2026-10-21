package com.nexus.launcher.core

/**
 * Pinned radial-launcher slots. Stored as `NexusConfig.radialApps`: one app id per slot in order,
 * "" for an empty slot. Positions are permanent: an app that is uninstalled or moved to the Private
 * Space leaves its slot empty instead of shifting every other app round the ring.
 * Pure functions (unit-tested); the UI and the home screen only call these.
 */
object RadialSlots {

    /** Number of slots the user can pin: the ring minus the "Edit" slot when that is on. */
    fun pinnable(slots: Int, editSlot: Boolean): Int = (slots.coerceIn(4, 10) - if (editSlot) 1 else 0).coerceAtLeast(1)

    /** Exactly [count] entries: extra ones dropped, missing ones empty. */
    fun normalize(slots: List<String>, count: Int): List<String> =
        List(count) { i -> slots.getOrNull(i).orEmpty() }

    /** Puts [id] into slot [index]; if it was pinned elsewhere that slot is emptied (no duplicates). */
    fun assign(slots: List<String>, count: Int, index: Int, id: String): List<String> {
        val s = normalize(slots, count).map { if (it == id) "" else it }.toMutableList()
        if (index in s.indices) s[index] = id
        return s
    }

    fun clear(slots: List<String>, count: Int, index: Int): List<String> =
        normalize(slots, count).toMutableList().also { if (index in it.indices) it[index] = "" }

    /** Swaps slot [from] with its neighbour in direction [delta] (-1 = earlier, +1 = later). */
    fun move(slots: List<String>, count: Int, from: Int, delta: Int): List<String> {
        val s = normalize(slots, count).toMutableList()
        val to = from + delta
        if (from !in s.indices || to !in s.indices) return s
        val t = s[from]; s[from] = s[to]; s[to] = t
        return s
    }

    /** Adds [id] to the first empty slot (no-op if already pinned or the ring is full). */
    fun add(slots: List<String>, count: Int, id: String): List<String> {
        val s = normalize(slots, count)
        if (id in s) return s
        val free = s.indexOf("")
        return if (free < 0) s else assign(s, count, free, id)
    }

    fun remove(slots: List<String>, count: Int, id: String): List<String> =
        normalize(slots, count).map { if (it == id) "" else it }

    /** Defaults: dock apps first, then the given fallback order (e.g. most used), no duplicates. */
    fun defaults(dock: List<String>, fallback: List<String>, count: Int): List<String> =
        normalize((dock + fallback).filter { it.isNotEmpty() }.distinct(), count)

    /**
     * What the ring shows, one entry per ring position. Position 0 is the "Edit" slot when [editSlot]
     * (always null). Pinned slots resolve through [lookup]; an empty or unresolvable slot is null,
     * or, with [fillEmpty], the next unused app from [fill].
     */
    fun <T> resolve(
        slots: List<String>,
        ringSize: Int,
        editSlot: Boolean,
        lookup: (String) -> T?,
        fill: List<T> = emptyList(),
        fillEmpty: Boolean = false,
        keyOf: (T) -> String,
    ): List<T?> {
        val n = pinnable(ringSize, editSlot)
        val pinned = normalize(slots, n).map { id -> if (id.isEmpty()) null else lookup(id) }
        val used = pinned.filterNotNull().map(keyOf).toMutableSet()
        val spare = if (fillEmpty) fill.filter { keyOf(it) !in used }.iterator() else emptyList<T>().iterator()
        val ring = pinned.map { it ?: if (spare.hasNext()) spare.next() else null }
        return if (editSlot) listOf<T?>(null) + ring else ring
    }
}
