package com.tallybook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tallybook.core.model.settings.UiStyle
import com.tallybook.core.ui.theme.LocalTallyBookStyle

/**
 * Page-level atmosphere. All root screens stay transparent so this is the only background painted.
 */
@Composable
fun TallyPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalTallyBookStyle.current
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (style == UiStyle.SIMPLE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.primaryContainer.copy(alpha = 0.22f),
                                colors.background,
                                colors.background,
                            ),
                        ),
                    ),
            )
        }
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            content()
        }
    }
}

@Composable
fun tallyCardColors(
    accentContainer: Color? = null,
): CardColors {
    val style = LocalTallyBookStyle.current
    val colors = MaterialTheme.colorScheme
    val base = accentContainer ?: colors.surface
    val container = when (style) {
        UiStyle.SIMPLE -> base
        UiStyle.FLAT -> accentContainer ?: colors.surfaceVariant
    }
    return CardDefaults.cardColors(
        containerColor = container,
        contentColor = colors.onSurface,
    )
}

@Composable
fun tallyCardElevation(): CardElevation {
    val style = LocalTallyBookStyle.current
    return CardDefaults.cardElevation(
        defaultElevation = when (style) {
            UiStyle.SIMPLE -> 0.dp
            UiStyle.FLAT -> 0.dp
        },
    )
}

@Composable
fun Modifier.tallyCardDecoration(
    shape: Shape? = null,
): Modifier {
    return this
}

@Composable
fun tallyNavigationBarColor(): Color {
    val style = LocalTallyBookStyle.current
    val colors = MaterialTheme.colorScheme
    return when (style) {
        UiStyle.SIMPLE -> colors.surface
        UiStyle.FLAT -> colors.background
    }
}

@Composable
fun TallyCard(
    modifier: Modifier = Modifier,
    colors: CardColors = tallyCardColors(),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Card(
        modifier = modifier.tallyCardDecoration(shape),
        shape = shape,
        colors = colors,
        elevation = tallyCardElevation(),
        content = content,
    )
}
