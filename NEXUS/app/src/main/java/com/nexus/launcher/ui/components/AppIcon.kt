package com.nexus.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexus.launcher.core.AppInfo
import com.nexus.launcher.core.IconSpec
import com.nexus.launcher.nexus
import com.nexus.launcher.ui.theme.IconShapeOutline
import com.nexus.launcher.ui.theme.LocalNexus

@Composable
fun rememberIconSpec(sizeDp: Float): IconSpec {
    val nx = LocalNexus.current
    val density = LocalDensity.current
    val px = with(density) { sizeDp.dp.roundToPx() }.coerceAtLeast(16)
    return remember(nx, px) {
        IconSpec(
            style = nx.config.iconStyle,
            shape = nx.config.iconShape,
            sizePx = px,
            accent = nx.palette.accent.toArgb(),
            accent2 = nx.palette.accent2.toArgb(),
            panel = nx.palette.panel.copy(alpha = 1f).toArgb(),
            glow = nx.glow,
            outline = nx.spec.outline,
            tint = nx.config.iconTint,
            pack = nx.config.iconPack,
        )
    }
}

@Composable
fun AppIconImage(app: AppInfo, sizeDp: Float, modifier: Modifier = Modifier) {
    val engine = LocalContext.current.nexus.icons
    val spec = rememberIconSpec(sizeDp)
    val overrideUri = LocalNexus.current.config.iconOverrides[app.key.id]
    val bitmap: ImageBitmap? by produceState(engine.cached(app, spec, overrideUri), app.key, spec, overrideUri) {
        value = engine.load(app, spec, overrideUri)
    }
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = modifier.size(sizeDp.dp))
    } else {
        val p = LocalNexus.current.palette
        Canvas(modifier.size(sizeDp.dp)) {
            drawCircle(p.accent, size.minDimension * 0.36f, alpha = 0.25f, style = Stroke(width = 1.5f))
        }
    }
}

/** Icon + optional label + notification badge. Pure presentation; gestures are attached by callers. */
@Composable
fun AppIcon(
    app: AppInfo,
    label: String,
    sizeDp: Float,
    showLabel: Boolean,
    badge: Int,
    modifier: Modifier = Modifier,
) {
    val nx = LocalNexus.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AppIconImage(app, sizeDp)
            if (badge > 0 && nx.config.badges && nx.config.badgeStyle != com.nexus.launcher.core.BadgeStyle.NONE) {
                if (nx.config.badgeStyle == com.nexus.launcher.core.BadgeStyle.COUNT) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .clip(CircleShape)
                            .background(nx.palette.accent2)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (badge > 99) "99+" else "$badge", style = nx.body(9.5f, weight = 700, color = androidx.compose.ui.graphics.Color.White)) }
                } else {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(nx.palette.accent2)
                            .border(1.5.dp, nx.palette.bg, CircleShape)
                    )
                }
            }
            if (app.isWork) {
                Text(
                    "W", style = nx.label(8f, nx.palette.bg, weight = 700),
                    modifier = Modifier.align(Alignment.BottomEnd).clip(CircleShape).background(nx.palette.accent).padding(horizontal = 3.dp),
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = nx.body(nx.config.labelSize, color = nx.palette.textDim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width((sizeDp + 22).dp),
            )
        }
    }
}

/** Folder: user-shape plate with up to four mini icons. */
@Composable
fun FolderIcon(apps: List<AppInfo>, title: String, sizeDp: Float, showLabel: Boolean, modifier: Modifier = Modifier) {
    val nx = LocalNexus.current
    val p = nx.palette
    val shape = remember(nx.config.iconShape) { IconShapeOutline(nx.config.iconShape) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(sizeDp.dp)
                .padding((sizeDp * 0.06f).dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(p.accent.copy(alpha = 0.16f), p.panel)))
                .border(1.2.dp, p.accent.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center,
        ) {
            val mini = sizeDp * 0.30f
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                for (row in 0 until 2) {
                    androidx.compose.foundation.layout.Row {
                        for (col in 0 until 2) {
                            val a = apps.getOrNull(row * 2 + col)
                            Box(Modifier.size(mini.dp).padding(1.dp), contentAlignment = Alignment.Center) {
                                if (a != null) AppIconImage(a, mini - 2f)
                            }
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                title, style = nx.body(nx.config.labelSize, color = p.textDim), maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width((sizeDp + 22).dp),
            )
        }
    }
}

/** Radial halo used behind the "focused" icon (drag, launch). */
@Composable
fun Halo(sizeDp: Float, alpha: Float, modifier: Modifier = Modifier) {
    val p = LocalNexus.current.palette
    Canvas(modifier.size(sizeDp.dp)) {
        drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.35f * alpha), Color.Transparent), center, size.minDimension / 2f), size.minDimension / 2f, center)
        drawCircle(p.accent, size.minDimension * 0.47f, center, alpha = 0.5f * alpha, style = Stroke(width = 1.2f))
        drawCircle(p.accent2, 2.5f, Offset(center.x, 3f), alpha = alpha)
    }
}
