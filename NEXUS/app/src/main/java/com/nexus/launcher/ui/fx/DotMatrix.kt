package com.nexus.launcher.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexus.launcher.ui.theme.LocalNexus

/**
 * Dot-matrix typography — a nod to Nothing's hardware language, rendered live with glowing
 * dots, a faint unlit grid and a per-dot "re-materialise" morph whenever characters change.
 */
object DotFont {
    const val ROWS = 7

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        ':' to arrayOf(".", ".", "#", ".", "#", ".", "."),
        '.' to arrayOf(".", ".", ".", ".", ".", ".", "#"),
        '-' to arrayOf("...", "...", "...", "###", "...", "...", "..."),
        ' ' to arrayOf("..", "..", "..", "..", "..", "..", ".."),
        '%' to arrayOf("##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##"),
        '°' to arrayOf("###", "#.#", "###", "...", "...", "...", "..."),
        '/' to arrayOf("....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."),
        '+' to arrayOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to arrayOf("###..", "#..#.", "#...#", "#...#", "#...#", "#..#.", "###.."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
    )

    class Raster(val cols: Int, val lit: BooleanArray) {
        operator fun get(row: Int, col: Int): Boolean = lit[row * cols + col]
    }

    fun columns(text: String, gap: Int = 1): Int {
        var c = 0
        text.uppercase().forEachIndexed { i, ch ->
            c += (glyphs[ch] ?: glyphs[' ']!!)[0].length
            if (i < text.length - 1) c += gap
        }
        return c
    }

    fun raster(text: String, gap: Int = 1): Raster {
        val up = text.uppercase()
        val cols = columns(up, gap).coerceAtLeast(1)
        val lit = BooleanArray(cols * ROWS)
        var x = 0
        up.forEachIndexed { i, ch ->
            val g = glyphs[ch] ?: glyphs[' ']!!
            val w = g[0].length
            for (r in 0 until ROWS) for (c in 0 until w) if (g[r][c] == '#') lit[r * cols + x + c] = true
            x += w + (if (i < up.length - 1) gap else 0)
        }
        return Raster(cols, lit)
    }
}

@Composable
fun DotMatrixText(
    text: String,
    pitch: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    unlitAlpha: Float = 0.07f,
    glow: Float = LocalNexus.current.glow,
    morphMs: Int = 420,
) {
    var shown by remember { mutableStateOf(text) }
    var previous by remember { mutableStateOf(text) }
    val morph = remember { Animatable(1f) }

    LaunchedEffect(text) {
        if (text != shown) {
            previous = shown
            shown = text
            morph.snapTo(0f)
            morph.animateTo(1f, tween(morphMs))
        }
    }

    val now = remember(shown) { DotFont.raster(shown) }
    val before = remember(previous) { DotFont.raster(previous) }
    val cols = now.cols

    Canvas(
        modifier
            .size(pitch * cols, pitch * DotFont.ROWS)
            .semantics { contentDescription = text }
    ) {
        val p = size.width / cols
        val r = p * 0.36f
        val progress = morph.value
        val canMorph = before.cols == cols && progress < 1f
        for (row in 0 until DotFont.ROWS) for (col in 0 until cols) {
            val center = Offset(col * p + p / 2f, row * p + p / 2f)
            val litNow = now[row, col]
            val alpha = if (!canMorph) {
                if (litNow) 1f else unlitAlpha
            } else {
                val litBefore = before[row, col]
                // staggered per-dot timing gives a "digital re-materialisation" look
                val phase = ((row * 7 + col * 13) % 10) / 20f
                val local = ((progress - phase) / 0.5f).coerceIn(0f, 1f)
                when {
                    litNow && litBefore -> 1f
                    litNow -> unlitAlpha + (1f - unlitAlpha) * local
                    litBefore -> 1f - (1f - unlitAlpha) * local
                    else -> unlitAlpha
                }
            }
            if (glow > 0.01f && alpha > 0.5f) {
                drawCircle(color, radius = r * 2.1f, center = center, alpha = 0.10f * glow * alpha)
            }
            drawCircle(color, radius = r, center = center, alpha = alpha)
        }
    }
}

/** Convenience: pitch that makes [text] fit [maxWidth]. */
fun dotPitchFor(text: String, maxWidth: Dp, maxPitch: Dp = 12.dp): Dp {
    val cols = DotFont.columns(text).coerceAtLeast(1)
    return minOf(maxPitch, maxWidth / cols)
}
