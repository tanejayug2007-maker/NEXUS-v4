package com.nexus.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private val a = AppKey("com.a", "A")
    private val b = AppKey("com.b", "B")
    private val c = AppKey("com.c", "C")

    private fun app(id: String, k: AppKey, x: Int, y: Int) = HomeItem(id, ItemType.APP, x, y, app = k)

    @Test fun moveIntoEmptyCell() {
        val l = HomeLayout(listOf(HomePage(listOf(app("1", a, 0, 0)))))
        val out = LayoutOps.drop(l, 0, "1", 2, 3, 4, 5)
        val item = out.pages[0].items.single()
        assertEquals(2, item.x); assertEquals(3, item.y)
    }

    @Test fun dropAppOnAppCreatesFolder() {
        val l = HomeLayout(listOf(HomePage(listOf(app("1", a, 0, 0), app("2", b, 1, 0)))))
        val out = LayoutOps.drop(l, 0, "1", 1, 0, 4, 5)
        val folder = out.pages[0].items.single()
        assertEquals(ItemType.FOLDER, folder.type)
        assertEquals(listOf(b, a), folder.apps)
        assertEquals(1, folder.x)
    }

    @Test fun dropAppOnFolderAddsIt() {
        val f = HomeItem("f", ItemType.FOLDER, 1, 1, apps = listOf(a, b))
        val l = HomeLayout(listOf(HomePage(listOf(f, app("3", c, 0, 0)))))
        val out = LayoutOps.drop(l, 0, "3", 1, 1, 4, 5)
        assertEquals(listOf(a, b, c), out.pages[0].items.single().apps)
    }

    @Test fun widgetsNeverOverlap() {
        var l = HomeLayout(listOf(HomePage(listOf(app("1", a, 0, 0)))))
        l = LayoutOps.addWidget(l, 0, 7, "com.w/W", 4, 2, 4, 5)
        val w = l.pages[0].items.first { it.type == ItemType.WIDGET }
        assertEquals(0, w.x); assertEquals(1, w.y)
        assertTrue(LayoutOps.fits(l.pages[0], 0, 3, 4, 2, 4, 5))
        assertTrue(!LayoutOps.fits(l.pages[0], 0, 0, 1, 1, 4, 5))
    }

    @Test fun fullPageSpillsToNewPage() {
        val items = (0 until 4).flatMap { y -> (0 until 4).map { x -> app("$x$y", AppKey("p$x$y", "C"), x, y) } }
        val l = HomeLayout(listOf(HomePage(items)))
        val out = LayoutOps.addApp(l, c, 0, 4, 4)
        assertEquals(2, out.pages.size)
        assertEquals(c, out.pages[1].items.single().app)
    }

    @Test fun uninstallPrunesFoldersAndDock() {
        val f = HomeItem("f", ItemType.FOLDER, 0, 0, apps = listOf(a, b))
        val l = HomeLayout(listOf(HomePage(listOf(f))), dock = listOf(a, c))
        val out = LayoutOps.removePackage(l, "com.a", 0)
        val item = out.pages[0].items.single()
        assertEquals(ItemType.APP, item.type)
        assertEquals(b, item.app)
        assertEquals(listOf(c), out.dock)
    }

    @Test fun shrinkingGridReflowsItems() {
        val l = HomeLayout(listOf(HomePage(listOf(app("1", a, 5, 6)))))
        val out = LayoutOps.normalize(l, 4, 5)
        val item = out.pages.flatMap { it.items }.single()
        assertTrue(item.x < 4 && item.y < 5)
    }

    @Test fun removeFromFolderDissolvesSingleApp() {
        val f = HomeItem("f", ItemType.FOLDER, 0, 0, apps = listOf(a, b))
        val l = HomeLayout(listOf(HomePage(listOf(f))))
        val out = LayoutOps.removeFromFolder(l, "f", a, 4, 5)
        val apps = out.pages[0].items.mapNotNull { it.app }.toSet()
        assertEquals(setOf(a, b), apps)
        assertNull(out.pages[0].items.firstOrNull { it.type == ItemType.FOLDER })
    }

    @Test fun findFreeRespectsSpan() {
        val p = HomePage(listOf(HomeItem("w", ItemType.WIDGET, 0, 0, w = 3, h = 1)))
        assertNotNull(LayoutOps.findFree(p, 1, 1, 4, 5))
        assertEquals(0 to 1, LayoutOps.findFree(p, 2, 1, 4, 5))
    }

    @Test fun nexusWidgetPlacedWithStyleAndSurvivesUninstall() {
        var l = HomeLayout(listOf(HomePage(listOf(app("1", a, 0, 0)))))
        l = LayoutOps.addNexusWidget(l, 0, NexusWidgetKind.CLOCK, 3, 4, 5)
        val w = l.pages[0].items.first { it.type == ItemType.NEXUS }
        assertEquals("CLOCK", w.provider); assertEquals("3", w.title)
        assertEquals(4, w.w); assertEquals(1, w.y)
        val out = LayoutOps.removePackage(l, "com.a", 0)
        assertEquals(1, out.pages[0].items.size)
    }
}
