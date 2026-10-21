package com.nexus.launcher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.HomeItem
import com.nexus.launcher.ui.components.AppIcon
import com.nexus.launcher.ui.components.GlassPanel
import com.nexus.launcher.ui.components.nexusItemGestures
import com.nexus.launcher.ui.components.rememberBounds
import com.nexus.launcher.ui.components.trackBounds
import com.nexus.launcher.ui.theme.LocalNexus

/** Folder expands out of its icon with a spring, holo panel, rename inline. */
@Composable
fun FolderOverlay(
    folder: HomeItem,
    origin: Rect,
    apps: Map<String, AppInfo>,
    badges: Map<String, Int>,
    labelOf: (AppInfo) -> String,
    onLaunch: (AppInfo, Rect) -> Unit,
    onAppMenu: (AppInfo, Rect) -> Unit,
    onRename: (String) -> Unit,
    onClose: () -> Unit,
) {
    val nx = LocalNexus.current
    val p = nx.palette
    val scale = remember(folder.id) { Animatable(0.2f) }
    val focusManager = LocalFocusManager.current
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    LaunchedEffect(folder.id) { scale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (title != folder.title) onRename(title)
                onClose()
            },
        contentAlignment = Alignment.Center,
    ) {
        val w = with(LocalDensity.current) { maxWidth.toPx() }
        val h = with(LocalDensity.current) { maxHeight.toPx() }
        val pivot = if (origin == Rect.Zero) TransformOrigin.Center
        else TransformOrigin((origin.center.x / w).coerceIn(0f, 1f), (origin.center.y / h).coerceIn(0f, 1f))
        val items = folder.apps.mapNotNull { apps[it.id] }

        GlassPanel(
            Modifier
                .widthIn(max = 340.dp)
                .padding(24.dp)
                .graphicsLayer {
                    val s = scale.value
                    scaleX = s; scaleY = s; alpha = ((s - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    transformOrigin = pivot
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) { },
            padding = 18.dp,
            highlight = true,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(if (nx.tech) "// CLUSTER · ${items.size}" else "${items.size} apps", style = nx.label(9f, p.textFaint))
                Spacer(Modifier.height(4.dp))
                BasicTextField(
                    value = title,
                    onValueChange = { title = it.take(32) },
                    singleLine = true,
                    textStyle = nx.display(22f, p.text, weight = 300).copy(textAlign = TextAlign.Start),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(title); focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                items.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            val bounds = rememberBounds()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .trackBounds(bounds)
                                    .nexusItemGestures(
                                        key = app.key.id,
                                        onTap = { onLaunch(app, bounds.rect) },
                                        onLongPress = { onAppMenu(app, bounds.rect) },
                                    )
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(app, labelOf(app), nx.config.iconSize * 0.85f, true, badges[app.component.packageName] ?: 0)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (items.isEmpty()) Text("Empty cluster", style = nx.body(13f, color = p.textDim))
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
