package com.tallybook.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tallybook.core.model.settings.ThemeMode
import com.tallybook.core.model.settings.UiStyle
import com.tallybook.core.ui.theme.LocalTallyBookStyle

private val GlassLight = lightColorScheme(
    primary = Color(0xFF315E8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7FA),
    onPrimaryContainer = Color(0xFF102B46),
    secondary = Color(0xFF526D89),
    secondaryContainer = Color(0xFFE1ECF8),
    onSecondaryContainer = Color(0xFF172A3D),
    tertiary = Color(0xFF6D63A7),
    background = Color(0xFFEDF3FA),
    onBackground = Color(0xFF18212D),
    surface = Color(0xFFF9FBFE),
    onSurface = Color(0xFF18212D),
    surfaceVariant = Color(0xFFDCE5F0),
    onSurfaceVariant = Color(0xFF465565),
    outline = Color(0xFF8897A8),
    outlineVariant = Color(0xFFC5D1DF),
)

private val GlassDark = darkColorScheme(
    primary = Color(0xFFA8CCF5),
    onPrimary = Color(0xFF0F2C48),
    primaryContainer = Color(0xFF294F75),
    onPrimaryContainer = Color(0xFFDCECFF),
    secondary = Color(0xFFB4C9DF),
    secondaryContainer = Color(0xFF263B50),
    onSecondaryContainer = Color(0xFFDCECFF),
    tertiary = Color(0xFFC7BDFF),
    background = Color(0xFF0D1622),
    onBackground = Color(0xFFE5ECF4),
    surface = Color(0xFF152131),
    onSurface = Color(0xFFE5ECF4),
    surfaceVariant = Color(0xFF263649),
    onSurfaceVariant = Color(0xFFBBC8D7),
    outline = Color(0xFF8798AA),
    outlineVariant = Color(0xFF405266),
)

private val SimpleLight = lightColorScheme(
    primary = Color(0xFF2E6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8ECE5),
    onPrimaryContainer = Color(0xFF0D332B),
    secondary = Color(0xFF5F7469),
    secondaryContainer = Color(0xFFE5ECE6),
    onSecondaryContainer = Color(0xFF1F2D26),
    tertiary = Color(0xFF8C6B48),
    background = Color(0xFFF7F4EE),
    onBackground = Color(0xFF24221E),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF24221E),
    surfaceVariant = Color(0xFFECE8E0),
    onSurfaceVariant = Color(0xFF605E58),
    outline = Color(0xFF8C8981),
    outlineVariant = Color(0xFFD6D1C7),
)

private val SimpleDark = darkColorScheme(
    primary = Color(0xFF8DD6C2),
    onPrimary = Color(0xFF0B3930),
    primaryContainer = Color(0xFF24584D),
    onPrimaryContainer = Color(0xFFC5F1E4),
    secondary = Color(0xFFB8CDC0),
    secondaryContainer = Color(0xFF33483D),
    onSecondaryContainer = Color(0xFFD4E9DC),
    tertiary = Color(0xFFE0C29E),
    background = Color(0xFF171816),
    onBackground = Color(0xFFEAE7E1),
    surface = Color(0xFF22231F),
    onSurface = Color(0xFFEAE7E1),
    surfaceVariant = Color(0xFF30312D),
    onSurfaceVariant = Color(0xFFC5C2BB),
    outline = Color(0xFF908E87),
    outlineVariant = Color(0xFF494943),
)

private val FlatLight = lightColorScheme(
    primary = Color(0xFF30363B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E5E8),
    onPrimaryContainer = Color(0xFF1B2024),
    secondary = Color(0xFF62686D),
    secondaryContainer = Color(0xFFECEEF0),
    onSecondaryContainer = Color(0xFF2B3034),
    tertiary = Color(0xFF71675C),
    background = Color(0xFFF0F1F2),
    onBackground = Color(0xFF1D2023),
    surface = Color(0xFFF7F8F8),
    onSurface = Color(0xFF1D2023),
    surfaceVariant = Color(0xFFE3E5E7),
    onSurfaceVariant = Color(0xFF5C6267),
    outline = Color(0xFF8B9094),
    outlineVariant = Color(0xFFCBCFD2),
)

private val FlatDark = darkColorScheme(
    primary = Color(0xFFD5D9DD),
    onPrimary = Color(0xFF202428),
    primaryContainer = Color(0xFF393F44),
    onPrimaryContainer = Color(0xFFE6E9EC),
    secondary = Color(0xFFBCC1C5),
    secondaryContainer = Color(0xFF30353A),
    onSecondaryContainer = Color(0xFFE1E4E7),
    tertiary = Color(0xFFD7C7B7),
    background = Color(0xFF151719),
    onBackground = Color(0xFFE7E9EA),
    surface = Color(0xFF1D2022),
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF2A2E31),
    onSurfaceVariant = Color(0xFFBEC3C6),
    outline = Color(0xFF858B8F),
    outlineVariant = Color(0xFF44494D),
)

private fun shapesFor(style: UiStyle): Shapes = when (style) {
    UiStyle.GLASS -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

    UiStyle.SIMPLE -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

    UiStyle.FLAT -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )
}

@Composable
fun TallyBookTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    uiStyle: UiStyle = UiStyle.GLASS,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        uiStyle == UiStyle.SIMPLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        uiStyle == UiStyle.GLASS -> if (darkTheme) GlassDark else GlassLight
        uiStyle == UiStyle.SIMPLE -> if (darkTheme) SimpleDark else SimpleLight
        else -> if (darkTheme) FlatDark else FlatLight
    }

    CompositionLocalProvider(LocalTallyBookStyle provides uiStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TallyBookTypography,
            shapes = shapesFor(uiStyle),
            content = content,
        )
    }
}
