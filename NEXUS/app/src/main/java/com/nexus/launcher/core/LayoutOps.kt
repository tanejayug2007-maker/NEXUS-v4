package com.nexus.launcher.core

import java.util.UUID

/**
 * Pure, side-effect-free home-layout operations. Every edit the user makes (drag, drop,
 * folder creation, widget placement, grid resize) goes through here, which is what the
 * unit tests in `src/test` exercise.
 */
object LayoutOps {

    fun newId(): String = UUID.randomUUID().toString()

    fun occupancy(page: HomePage, cols: Int, rows: Int, ignoreId: String? = null): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        for (it in page.items) {
            if (it.id == ignoreId) continue
            for (yy in it.y until (it.y + it.h)) for (xx in it.x until (it.x + it.w)) {
                if (yy in 0 until rows && xx in 0 until cols) grid[yy][xx] = true
            }
        }
        return grid
    }

    fun fits(page: HomePage, x: Int, y: Int, w: Int, h: Int, cols: Int, rows: Int, ignoreId: String? = null): Boolean {
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false
        val occ = occupancy(page, cols, rows, ignoreId)
        for (yy in y until y + h) for (xx in x until x + w) if (occ[yy][xx]) return false
        return true
    }

    fun findFree(page: HomePage, w: Int, h: Int, cols: Int, rows: Int): Pair<Int, Int>? {
        for (y in 0..(rows - h)) for (x in 0..(cols - w)) {
            if (fits(page, x, y, w, h, cols, rows)) return x to y
        }
        return null
    }

    fun itemAt(page: HomePage, x: Int, y: Int, ignoreId: String? = null): HomeItem? =
        page.items.firstOrNull { it.id != ignoreId && x >= it.x && x < it.x + it.w && y >= it.y && y < it.y + it.h }

    fun locate(layout: HomeLayout, itemId: String): Pair<Int, HomeItem>? {
        layout.pages.forEachIndexed { i, p -> p.items.firstOrNull { it.id == itemId }?.let { return i to it } }
        return null
    }

    private fun HomeLayout.updatePage(index: Int, f: (HomePage) -> HomePage): HomeLayout =
        copy(pages = pages.mapIndexed { i, p -> if (i == index) f(p) else p })

    /** Moves an item to (x,y) on [toPage] if the cells are free. Returns the layout unchanged otherwise. */
    fun move(layout: HomeLayout, itemId: String, toPage: Int, x: Int, y: Int, cols: Int, rows: Int): HomeLayout {
        val (fromPage, item) = locate(layout, itemId) ?: return layout
        if (toPage !in layout.pages.indices) return layout
        val target = layout.pages[toPage]
        val ignore = if (fromPage == toPage) itemId else null
        if (!fits(target, x, y, item.w, item.h, cols, rows, ignore)) return layout
        val moved = item.copy(x = x, y = y)
        val removed = layout.updatePage(fromPage) { p -> p.copy(items = p.items.filterNot { it.id == itemId }) }
        return removed.updatePage(toPage) { p -> p.copy(items = p.items + moved) }
    }

    /**
     * Drop semantics for a dragged item released over cell (x,y) of page [pageIndex]:
     * empty → move; app on app → new folder; app on folder → add to folder; otherwise no-op.
     */
    fun drop(layout: HomeLayout, pageIndex: Int, itemId: String, x: Int, y: Int, cols: Int, rows: Int): HomeLayout {
        val (_, dragged) = locate(layout, itemId) ?: return layout
        val page = layout.pages.getOrNull(pageIndex) ?: return layout
        val target = itemAt(page, x, y, ignoreId = itemId)
        if (target == null) return move(layout, itemId, pageIndex, x, y, cols, rows)
        if (dragged.type != ItemType.APP || dragged.app == null) return layout
        return when (target.type) {
            ItemType.APP -> {
                val a = target.app ?: return layout
                if (a == dragged.app) return remove(layout, itemId)
                val folder = HomeItem(
                    id = newId(), type = ItemType.FOLDER, x = target.x, y = target.y,
                    title = "Folder", apps = listOf(a, dragged.app),
                )
                val without = remove(remove(layout, itemId), target.id)
                without.updatePage(pageIndex) { p -> p.copy(items = p.items + folder) }
            }
            ItemType.FOLDER -> {
                if (dragged.app in target.apps) return remove(layout, itemId)
                val merged = target.copy(apps = target.apps + dragged.app)
                replace(remove(layout, itemId), merged)
            }
            ItemType.WIDGET, ItemType.NEXUS -> layout
        }
    }

    fun replace(layout: HomeLayout, item: HomeItem): HomeLayout =
        layout.copy(pages = layout.pages.map { p -> p.copy(items = p.items.map { if (it.id == item.id) item else it }) })

    fun remove(layout: HomeLayout, itemId: String): HomeLayout =
        layout.copy(pages = layout.pages.map { p -> p.copy(items = p.items.filterNot { it.id == itemId }) })

    /** Places a new app on the first free cell starting at [preferredPage]; adds a page if full. */
    fun addApp(layout: HomeLayout, app: AppKey, preferredPage: Int, cols: Int, rows: Int): HomeLayout {
        if (layout.pages.any { p -> p.items.any { it.type == ItemType.APP && it.app == app } }) return layout
        return addItem(layout, preferredPage, 1, 1, cols, rows) { x, y ->
            HomeItem(id = newId(), type = ItemType.APP, x = x, y = y, app = app)
        }
    }

    fun addWidget(layout: HomeLayout, preferredPage: Int, widgetId: Int, provider: String, w: Int, h: Int, cols: Int, rows: Int): HomeLayout {
        val ww = w.coerceIn(1, cols)
        val hh = h.coerceIn(1, rows)
        return addItem(layout, preferredPage, ww, hh, cols, rows) { x, y ->
            HomeItem(id = newId(), type = ItemType.WIDGET, x = x, y = y, w = ww, h = hh, widgetId = widgetId, provider = provider)
        }
    }

    private fun addItem(
        layout: HomeLayout, preferredPage: Int, w: Int, h: Int, cols: Int, rows: Int,
        build: (Int, Int) -> HomeItem,
    ): HomeLayout {
        val pages = if (layout.pages.isEmpty()) listOf(HomePage()) else layout.pages
        val start = preferredPage.coerceIn(0, pages.size - 1)
        val order = (start until pages.size) + (0 until start)
        for (i in order) {
            val spot = findFree(pages[i], w, h, cols, rows) ?: continue
            val item = build(spot.first, spot.second)
            return layout.copy(pages = pages.mapIndexed { idx, p -> if (idx == i) p.copy(items = p.items + item) else p })
        }
        val item = build(0, 0)
        return layout.copy(pages = pages + HomePage(listOf(item)))
    }

    /** Adds a built-in NEXUS widget; [kind] is a [NexusWidgetKind] name, style index kept in title. */
    fun addNexusWidget(layout: HomeLayout, preferredPage: Int, kind: NexusWidgetKind, style: Int, cols: Int, rows: Int): HomeLayout {
        val ww = kind.w.coerceIn(1, cols)
        val hh = kind.h.coerceIn(1, rows)
        return addItem(layout, preferredPage, ww, hh, cols, rows) { x, y ->
            HomeItem(id = newId(), type = ItemType.NEXUS, x = x, y = y, w = ww, h = hh, provider = kind.name, title = style.toString())
        }
    }

    fun resize(layout: HomeLayout, itemId: String, w: Int, h: Int, cols: Int, rows: Int): HomeLayout {
        val (pageIdx, item) = locate(layout, itemId) ?: return layout
        val ww = w.coerceIn(1, cols)
        val hh = h.coerceIn(1, rows)
        val page = layout.pages[pageIdx]
        val x = item.x.coerceAtMost(cols - ww)
        val y = item.y.coerceAtMost(rows - hh)
        if (!fits(page, x, y, ww, hh, cols, rows, itemId)) return layout
        return replace(layout, item.copy(x = x, y = y, w = ww, h = hh))
    }

    /** Takes one app out of a folder and puts it on the folder's page; dissolves 1-app folders. */
    fun removeFromFolder(layout: HomeLayout, folderId: String, app: AppKey, cols: Int, rows: Int): HomeLayout {
        val (pageIdx, folder) = locate(layout, folderId) ?: return layout
        val remaining = folder.apps.filterNot { it == app }
        var next = when (remaining.size) {
            0 -> remove(layout, folderId)
            1 -> replace(layout, HomeItem(id = folder.id, type = ItemType.APP, x = folder.x, y = folder.y, app = remaining[0]))
            else -> replace(layout, folder.copy(apps = remaining))
        }
        next = addAppIgnoringDuplicates(next, app, pageIdx, cols, rows)
        return next
    }

    private fun addAppIgnoringDuplicates(layout: HomeLayout, app: AppKey, page: Int, cols: Int, rows: Int): HomeLayout =
        addItem(layout, page, 1, 1, cols, rows) { x, y -> HomeItem(id = newId(), type = ItemType.APP, x = x, y = y, app = app) }

    /** Removes every reference to an uninstalled package (only call on a real removal event). */
    fun removePackage(layout: HomeLayout, pkg: String, user: Long): HomeLayout {
        fun gone(k: AppKey?) = k != null && k.pkg == pkg && k.user == user
        val pages = layout.pages.map { p ->
            p.copy(items = p.items.mapNotNull { item ->
                when (item.type) {
                    ItemType.APP -> if (gone(item.app)) null else item
                    ItemType.FOLDER -> {
                        val left = item.apps.filterNot { gone(it) }
                        when (left.size) {
                            0 -> null
                            1 -> HomeItem(id = item.id, type = ItemType.APP, x = item.x, y = item.y, app = left[0])
                            else -> item.copy(apps = left)
                        }
                    }
                    ItemType.WIDGET -> if (item.provider.startsWith("$pkg/")) null else item
                    ItemType.NEXUS -> item
                }
            })
        }
        return layout.copy(pages = pages, dock = layout.dock.filterNot { gone(it) })
    }

    /** After a grid-size change, re-flows any item that no longer fits. */
    fun normalize(layout: HomeLayout, cols: Int, rows: Int): HomeLayout {
        var changed = false
        val overflow = mutableListOf<HomeItem>()
        val pages = layout.pages.map { p ->
            val kept = mutableListOf<HomeItem>()
            for (item in p.items.sortedWith(compareBy({ it.y }, { it.x }))) {
                val w = item.w.coerceAtMost(cols)
                val h = item.h.coerceAtMost(rows)
                val candidate = item.copy(w = w, h = h)
                val pageSoFar = HomePage(kept)
                if (fits(pageSoFar, item.x, item.y, w, h, cols, rows)) {
                    if (candidate != item) changed = true
                    kept += candidate
                } else {
                    val spot = findFree(pageSoFar, w, h, cols, rows)
                    changed = true
                    if (spot != null) kept += candidate.copy(x = spot.first, y = spot.second) else overflow += candidate
                }
            }
            HomePage(kept)
        }
        if (!changed) return layout
        var out = layout.copy(pages = pages)
        for (item in overflow) {
            out = addItem(out, out.pages.size - 1, item.w, item.h, cols, rows) { x, y -> item.copy(x = x, y = y) }
        }
        return out
    }

    fun stripWidgets(layout: HomeLayout): HomeLayout =
        layout.copy(pages = layout.pages.map { p -> p.copy(items = p.items.filterNot { it.type == ItemType.WIDGET }) })

    fun addPage(layout: HomeLayout): HomeLayout = layout.copy(pages = layout.pages + HomePage())

    fun removeEmptyPages(layout: HomeLayout): HomeLayout {
        val kept = layout.pages.filter { it.items.isNotEmpty() }
        return layout.copy(pages = kept.ifEmpty { listOf(HomePage()) })
    }

    fun toggleDock(layout: HomeLayout, app: AppKey, maxDock: Int = 5): HomeLayout = when {
        app in layout.dock -> layout.copy(dock = layout.dock - app)
        layout.dock.size >= maxDock -> layout
        else -> layout.copy(dock = layout.dock + app)
    }

    fun widgetIds(layout: HomeLayout): List<Int> =
        layout.pages.flatMap { p -> p.items.filter { it.type == ItemType.WIDGET }.map { it.widgetId } }
}
