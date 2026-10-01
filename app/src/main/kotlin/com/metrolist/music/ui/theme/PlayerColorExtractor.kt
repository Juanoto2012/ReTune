/**
 * ReTune Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        val primaryColor = Color(palette.bestSwatch(fallbackColor))
        return listOf(
            primaryColor,
            primaryColor.copy(
                red = primaryColor.red * 0.6f,
                green = primaryColor.green * 0.6f,
                blue = primaryColor.blue * 0.6f,
            ),
            Color.Black,
        )
    }

    /**
     * Picks the swatch that best represents the artwork, preferring vivid mid-lightness colours and
     * using population only as a tiebreak.
     *
     * This used to delegate to materialKolor's `Score.score`, which was the sole remaining reason
     * the app shipped that library. The player is the only place that still derives a colour from
     * artwork, and the ranking rule is short enough to own outright, which removes a dependency
     * from the release APK.
     */
    private fun Palette.bestSwatch(fallbackColor: Int): Int {
        var bestColor = fallbackColor
        var bestScore = Float.NEGATIVE_INFINITY
        for (swatch in swatches) {
            val rgb = swatch.rgb
            val red = (rgb shr 16 and 0xFF) / 255f
            val green = (rgb shr 8 and 0xFF) / 255f
            val blue = (rgb and 0xFF) / 255f
            val maxChannel = max(red, max(green, blue))
            val minChannel = min(red, min(green, blue))
            val saturation = if (maxChannel == 0f) 0f else (maxChannel - minChannel) / maxChannel
            val lightness = (maxChannel + minChannel) / 2f
            val score = (1f - abs(saturation - 0.6f) * 2f) * 0.55f + (1f - abs(lightness - 0.5f) * 2f) * 0.45f
            if (score > bestScore) {
                bestScore = score
                bestColor = rgb
            }
        }
        return bestColor
    }
}
