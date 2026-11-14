package com.tallybook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Colour comes from a small token palette, never from literals inside screens.
 *
 * Dynamic colour (Material You) is used on Android 12+ so the ledger matches the user's wallpaper —
 * a bookkeeping app is opened many times a day, and a jarring palette is a real cost. On older
 * versions the brand palette is the fallback.
 */
private val BrandPrimary = androidx.compose.ui.graphics.Color(0xFF2E6B5E)
private val BrandSecondary = androidx.compose.ui.graphics.Color(0xFF4F6355)
private val BrandTertiary = androidx.compose.ui.graphics.Color(0xFF3F6374)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    tertiary = BrandTertiary,
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF8DD6C2),
    secondary = androidx.compose.ui.graphics.Color(0xFFB6CCBB),
    tertiary = androidx.compose.ui.graphics.Color(0xFFA7CCDE),
)

@Composable
fun TallyBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TallyBookTypography,
        content = content,
    )
}
