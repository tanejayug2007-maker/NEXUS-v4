package com.nexus.launcher.ui.home

import android.content.pm.ShortcutInfo
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nexus.launcher.LauncherActions
import com.nexus.launcher.LauncherViewModel
import com.nexus.launcher.MenuTarget
import com.nexus.launcher.Overlay
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.HomeLayout
import com.nexus.launcher.core.ItemType
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.HoloChip
import com.nexus.launcher.ui.components.NxButton
import com.nexus.launcher.ui.theme.LocalNexus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class Entry(val label: String, val icon: ImageVector, val action: () -> Unit)

private enum class Mode { LIST, RENAME, RESIZE, PAGES, DATA }

/** Long-press menu for icons, folders, widgets and empty space — anchored next to the target. */
@Composable
fun ItemMenu(
    target: MenuTarget,
    anchor: Rect,
    layout: HomeLayout,
    apps: Map<String, AppInfo>,
    vm: LauncherViewModel,
    actions: LauncherActions,
    labelOf: (AppInfo) -> String,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val ctx = LocalContext.current
    var mode by remember(target) { mutableStateOf(Mode.LIST) }
    val appear = remember(target) { Animatable(0.85f) }
    LaunchedEffect(target) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
    val cfg = nx.config

    val app: AppInfo? = when (target) {
        is MenuTarget.Item -> target.item.app?.let { apps[it.id] }.takeIf { target.item.type == ItemType.APP }
        is MenuTarget.DrawerApp -> target.app
        is MenuTarget.DockApp -> target.app
        is MenuTarget.FolderApp -> target.app
        is MenuTarget.Empty -> null
    }

    val shortcuts by produceState<List<Pair<ShortcutInfo, ImageBitmap?>>>(emptyList(), app?.key) {
        val a = app ?: return@produceState
        value = withContext(Dispatchers.IO) {
            actions.shortcutsFor(a).map { s -> s to ctx.nexus.icons.plain(ctx.nexus.apps.shortcutIcon(s), 64) }
        }
    }

    val entries = buildList {
        fun appEntries(a: AppInfo, inDock: Boolean) {
            add(Entry("Rename", Icons.Rounded.Edit) { mode = Mode.RENAME })
            add(Entry("Change icon", Icons.Rounded.Image) { actions.pickIcon(a); vm.close() })
            if (cfg.orbitEnabled) add(Entry(if (a.key.id in cfg.orbitPinned) "Unpin from orbit" else "Pin to orbit", Icons.Rounded.PushPin) { vm.toggleOrbitPin(a) })
            if (cfg.radialMenu) add(Entry(if (a.key.id in cfg.radialApps) "Remove from radial" else "Add to radial", Icons.Rounded.Add) { vm.toggleRadial(a) })
            if (cfg.iconOverrides.containsKey(a.key.id)) add(Entry("Reset icon", Icons.Rounded.RestartAlt) { vm.setIconOverride(a.key.id, null); vm.close() })
            add(Entry(if (inDock) "Unpin from dock" else "Pin to dock", Icons.Rounded.PushPin) { vm.toggleDock(a) })
            add(Entry("App info", Icons.Rounded.Info) { actions.appInfo(a); vm.close() })
            add(Entry("Uninstall", Icons.Rounded.DeleteOutline) { actions.uninstall(a); vm.close() })
        }
        when (target) {
            is MenuTarget.Item -> {
                val item = target.item
                when (item.type) {
                    ItemType.APP -> {
                        add(Entry("Move", Icons.Rounded.OpenWith) { vm.startPlacing(item.id) })
                        add(Entry("Send to page", Icons.Rounded.ViewCarousel) { mode = Mode.PAGES })
                        add(Entry("Remove from home", Icons.Rounded.Close) { vm.removeItem(item.id) { actions.deleteWidget(it) } })
                        app?.let { appEntries(it, it.key in layout.dock) }
                    }
                    ItemType.FOLDER -> {
                        add(Entry("Open", Icons.Rounded.FolderOpen) { vm.open(Overlay.Folder(item.id, anchor)) })
                        add(Entry("Rename", Icons.Rounded.Edit) { mode = Mode.RENAME })
                        add(Entry("Move", Icons.Rounded.OpenWith) { vm.startPlacing(item.id) })
                        add(Entry("Send to page", Icons.Rounded.ViewCarousel) { mode = Mode.PAGES })
                        add(Entry("Remove folder", Icons.Rounded.Delete) { vm.removeItem(item.id) { } })
                    }
                    ItemType.NEXUS -> {
                        val kind = runCatching { com.nexus.launcher.core.NexusWidgetKind.valueOf(item.provider) }.getOrNull()
                        val n = kind?.styles ?: 6
                        val st = item.title.substringBefore('|').toIntOrNull() ?: 0
                        val fr = item.title.substringAfter('|', "").toIntOrNull() ?: cfg.widgetFrame.ordinal
                        val frames = com.nexus.launcher.core.WidgetFrame.entries
                        add(Entry("Next style (${st + 1}/$n)", Icons.Rounded.Tune) { vm.setNexusStyle(item.id, (st + 1) % n) })
                        add(Entry("Previous style", Icons.Rounded.Tune) { vm.setNexusStyle(item.id, (st + n - 1) % n) })
                        add(Entry("Frame: ${frames[fr % frames.size].name.lowercase()} → next", Icons.Rounded.AspectRatio) { vm.setNexusFrame(item.id, (fr + 1) % frames.size) })
                        if (kind == com.nexus.launcher.core.NexusWidgetKind.NOTE) add(Entry("Edit note", Icons.Rounded.Edit) { mode = Mode.DATA })
                        if (kind == com.nexus.launcher.core.NexusWidgetKind.WORLD_CLOCK) add(Entry("Edit cities (time zones)", Icons.Rounded.Edit) { mode = Mode.DATA })
                        if (kind == com.nexus.launcher.core.NexusWidgetKind.PHOTO) add(Entry("Choose photo", Icons.Rounded.Image) { actions.pickWidgetPhoto(item.id); vm.close() })
                        add(Entry("Move", Icons.Rounded.OpenWith) { vm.startPlacing(item.id) })
                        add(Entry("Resize", Icons.Rounded.AspectRatio) { mode = Mode.RESIZE })
                        add(Entry("Send to page", Icons.Rounded.ViewCarousel) { mode = Mode.PAGES })
                        add(Entry("Remove module", Icons.Rounded.Delete) { vm.removeItem(item.id) { } })
                    }
                    ItemType.WIDGET -> {
                        add(Entry("Move", Icons.Rounded.OpenWith) { vm.startPlacing(item.id) })
                        add(Entry("Resize", Icons.Rounded.AspectRatio) { mode = Mode.RESIZE })
                        add(Entry("Send to page", Icons.Rounded.ViewCarousel) { mode = Mode.PAGES })
                        add(Entry("Remove widget", Icons.Rounded.Delete) { vm.removeItem(item.id) { actions.deleteWidget(it) } })
                    }
                }
            }
            is MenuTarget.DrawerApp -> {
                add(Entry("Add to home", Icons.Rounded.Add) { vm.addToHome(target.app) })
                val hidden = target.app.key.id in cfg.hiddenApps
                add(Entry(if (hidden) "Show in drawer" else "Hide from drawer", if (hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff) { vm.hideApp(target.app, !hidden) })
                appEntries(target.app, target.app.key in layout.dock)
            }
            is MenuTarget.DockApp -> appEntries(target.app, true)
            is MenuTarget.FolderApp -> {
                add(Entry("Remove from folder", Icons.Rounded.Remove) { vm.removeFromFolder(target.folderId, target.app.key) })
                add(Entry("App info", Icons.Rounded.Info) { actions.appInfo(target.app); vm.close() })
                add(Entry("Uninstall", Icons.Rounded.DeleteOutline) { actions.uninstall(target.app); vm.close() })
            }
            is MenuTarget.Empty -> {
                add(Entry("Add widget", Icons.Rounded.Widgets) { vm.open(Overlay.WidgetPicker) })
                add(Entry("Wallpaper", Icons.Rounded.Wallpaper) { actions.openWallpaperPicker(); vm.close() })
                add(Entry("NEXUS Studio", Icons.Rounded.Tune) { actions.openStudio(); vm.close() })
                add(Entry("Add page", Icons.Rounded.Add) { vm.addPage() })
                add(Entry("Remove empty pages", Icons.Rounded.Remove) { vm.removeEmptyPages() })
                add(Entry(if (cfg.lockLayout) "Unlock layout" else "Lock layout", if (cfg.lockLayout) Icons.Rounded.LockOpen else Icons.Rounded.Lock) {
                    vm.updateConfig { it.copy(lockLayout = !it.lockLayout) }; vm.close()
                })
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(remember { MutableInteractionSource() }, indication = null) { vm.close() }
    ) {
        GlassPanel(
            Modifier
                .layout { measurable, constraints ->
                    val pl = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val margin = 16.dp.roundToPx()
                    val gap = 8.dp.roundToPx()
                    val maxW = constraints.maxWidth
                    val maxH = constraints.maxHeight
                    val x = if (anchor == Rect.Zero) (maxW - pl.width) / 2
                    else (anchor.center.x.toInt() - pl.width / 2).coerceIn(margin, (maxW - pl.width - margin).coerceAtLeast(margin))
                    val y = when {
                        anchor == Rect.Zero -> (maxH - pl.height) / 2
                        anchor.bottom + gap + pl.height < maxH - margin -> anchor.bottom.toInt() + gap
                        else -> (anchor.top.toInt() - gap - pl.height).coerceAtLeast(margin)
                    }
                    layout(maxW, maxH) { pl.place(x, y) }
                }
                .width(250.dp)
                .graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = ((appear.value - 0.85f) / 0.15f).coerceIn(0f, 1f) }
                .clickable(remember { MutableInteractionSource() }, indication = null) { },
            padding = 8.dp,
            highlight = true,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val title = when (target) {
                    is MenuTarget.Item -> when (target.item.type) {
                        ItemType.APP -> app?.let(labelOf) ?: "App"
                        ItemType.FOLDER -> target.item.title
                        ItemType.WIDGET -> "Widget"
                        ItemType.NEXUS -> "Module · " + target.item.provider
                    }
                    is MenuTarget.Empty -> "Home"
                    else -> app?.let(labelOf) ?: ""
                }
                Text(if (nx.tech) "// ${title.uppercase()}" else title, style = nx.label(9.5f, p.textFaint), modifier = Modifier.padding(8.dp))
                when (mode) {
                    Mode.LIST -> {
                        shortcuts.forEach { (s, icon) ->
                            MenuRow(s.shortLabel?.toString() ?: "Shortcut", null, icon) {
                                actions.launchShortcut(s, anchor); vm.close()
                            }
                        }
                        if (shortcuts.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(p.border))
                        entries.forEach { e -> MenuRow(e.label, e.icon, null, e.action) }
                    }
                    Mode.DATA -> {
                        val item = (target as? MenuTarget.Item)?.item
                        if (item != null) RenameBox(
                            initial = item.data.ifEmpty { if (item.provider == "WORLD_CLOCK") "Europe/London, America/New_York, Asia/Tokyo" else "" },
                            onDone = { text -> vm.setItemData(item.id, text); vm.close() },
                            maxLen = 600, multiLine = item.provider == "NOTE",
                        )
                    }
                    Mode.RENAME -> RenameBox(
                        initial = when {
                            target is MenuTarget.Item && target.item.type == ItemType.FOLDER -> target.item.title
                            app != null -> labelOf(app)
                            else -> ""
                        },
                        onDone = { text ->
                            if (target is MenuTarget.Item && target.item.type == ItemType.FOLDER) vm.renameFolder(target.item.id, text)
                            else if (app != null) vm.setLabel(app, text)
                            vm.close()
                        },
                    )
                    Mode.RESIZE -> {
                        val item = (target as? MenuTarget.Item)?.item
                        if (item != null) {
                            val live = layout.pages.flatMap { it.items }.firstOrNull { it.id == item.id } ?: item
                            Stepper("WIDTH", live.w, 1, cfg.gridCols) { vm.resize(item.id, it, live.h) }
                            Stepper("HEIGHT", live.h, 1, cfg.gridRows) { vm.resize(item.id, live.w, it) }
                            Spacer(Modifier.height(6.dp))
                            NxButton("Done", { vm.close() }, Modifier.fillMaxWidth(), primary = true)
                        }
                    }
                    Mode.PAGES -> {
                        val item = (target as? MenuTarget.Item)?.item
                        if (item != null) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                layout.pages.indices.forEach { i ->
                                    HoloChip("${i + 1}", (target as? MenuTarget.Item)?.page == i, { vm.sendToPage(item.id, i) }, Modifier.padding(end = 6.dp))
                                }
                                HoloChip("+", false, { vm.sendToPage(item.id, layout.pages.size) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, icon: ImageVector?, bitmap: ImageBitmap?, onClick: () -> Unit) {
    val nx = LocalNexus.current
    val p = nx.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            bitmap != null -> Image(bitmap, null, Modifier.size(20.dp))
            icon != null -> Icon(icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
            else -> Spacer(Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = nx.body(14f), maxLines = 1)
    }
}

@Composable
private fun RenameBox(initial: String, onDone: (String) -> Unit, maxLen: Int = 40, multiLine: Boolean = false) {
    val nx = LocalNexus.current
    val p = nx.palette
    var text by remember { mutableStateOf(initial) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.padding(8.dp)) {
        BasicTextField(
            value = text, onValueChange = { text = it.take(maxLen) }, singleLine = !multiLine, maxLines = if (multiLine) 6 else 1,
            textStyle = nx.body(16f), cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone(text) }),
            modifier = Modifier
                .fillMaxWidth()
                .background(p.accent.copy(alpha = 0.06f))
                .padding(10.dp)
                .focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        NxButton("Save", { onDone(text) }, Modifier.fillMaxWidth(), primary = true)
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val nx = LocalNexus.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = nx.label(10f), modifier = Modifier.weight(1f))
        HoloChip("−", false, { if (value > min) onChange(value - 1) })
        Text("$value", style = nx.display(18f), modifier = Modifier.padding(horizontal = 12.dp))
        HoloChip("+", false, { if (value < max) onChange(value + 1) })
    }
}
