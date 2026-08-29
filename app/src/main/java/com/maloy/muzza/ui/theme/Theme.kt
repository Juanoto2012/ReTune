package com.maloy.muzza.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MuzzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        remember(darkTheme, pureBlack, themeColor) {
            if (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) {
                    dynamicDarkColorScheme(context).pureBlack(pureBlack)
                } else {
                    dynamicLightColorScheme(context)
                }
            } else {
                SchemeTonalSpot(Hct.fromInt(themeColor.toArgb()), darkTheme, 0.0)
                    .toColorScheme()
                    .pureBlack(darkTheme && pureBlack)
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this).maximumColorCount(16).generate()
    val swatch = palette.vibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
    
    return swatch?.let { Color(it.rgb) } ?: DefaultThemeColor
}

fun Bitmap.extractGradientColors(): List<Color> {
    val palette = Palette.from(this)
        .maximumColorCount(32)
        .generate()
    
    val color1 = (palette.vibrantSwatch ?: palette.dominantSwatch)?.let { Color(it.rgb) } ?: Color(0xFF595959)
    val color2 = (palette.darkVibrantSwatch ?: palette.mutedSwatch)?.let { Color(it.rgb) } ?: Color(0xFF0D0D0D)
    
    return listOf(color1, color2)
}

fun DynamicScheme.toColorScheme() =
    ColorScheme(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(background),
        onBackground = Color(onBackground),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(primary),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = Color(error),
        onError = Color(onError),
        errorContainer = Color(errorContainer),
        onErrorContainer = Color(onErrorContainer),
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(scrim),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(surfaceContainer),
        surfaceContainerHigh = Color(surfaceContainerHigh),
        surfaceContainerHighest = Color(surfaceContainerHighest),
        surfaceContainerLow = Color(surfaceContainerLow),
        surfaceContainerLowest = Color(surfaceContainerLowest),
        primaryFixed = Color(primaryContainer),
        onPrimaryFixed = Color(onPrimaryContainer),
        primaryFixedDim = Color(primaryContainer),
        onPrimaryFixedVariant = Color(onPrimaryContainer),
        secondaryFixed = Color(secondaryContainer),
        onSecondaryFixed = Color(onSecondaryContainer),
        secondaryFixedDim = Color(secondaryContainer),
        onSecondaryFixedVariant = Color(onSecondaryContainer),
        tertiaryFixed = Color(tertiaryContainer),
        onTertiaryFixed = Color(onTertiaryContainer),
        tertiaryFixedDim = Color(tertiaryContainer),
        onTertiaryFixedVariant = Color(onTertiaryContainer),
    )

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}