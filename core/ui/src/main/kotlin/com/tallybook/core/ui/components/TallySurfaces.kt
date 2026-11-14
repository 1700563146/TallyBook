package com.tallybook.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
        if (style == UiStyle.GLASS) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.15f),
                                Color.Transparent,
                                colors.tertiary.copy(alpha = 0.12f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .offset(x = (-170).dp, y = (-80).dp)
                    .size(520.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.primary.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            radius = 520.dp.value * 0.5f,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .offset(x = 190.dp, y = 330.dp)
                    .size(460.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                colors.tertiary.copy(alpha = 0.20f),
                                Color.Transparent,
                            ),
                            radius = 460.dp.value * 0.5f,
                        ),
                    ),
            )
        } else if (style == UiStyle.SIMPLE) {
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
        content()
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
        UiStyle.GLASS -> base.copy(alpha = if (base == colors.surface) 0.72f else 0.82f)
        UiStyle.SIMPLE -> base
        UiStyle.FLAT -> accentContainer?.copy(alpha = 0.72f) ?: colors.surfaceVariant.copy(alpha = 0.52f)
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
            UiStyle.GLASS -> 10.dp
            UiStyle.SIMPLE -> 3.dp
            UiStyle.FLAT -> 0.dp
        },
    )
}

@Composable
fun Modifier.tallyCardDecoration(
    shape: Shape? = null,
): Modifier {
    val style = LocalTallyBookStyle.current
    val colors = MaterialTheme.colorScheme
    val resolvedShape = shape ?: MaterialTheme.shapes.large
    return when (style) {
        UiStyle.GLASS -> border(
            width = 1.dp,
            color = if (colors.surface.luminance() > 0.5f) {
                Color.White.copy(alpha = 0.72f)
            } else {
                colors.outline.copy(alpha = 0.42f)
            },
            shape = resolvedShape,
        )

        UiStyle.SIMPLE -> this
        UiStyle.FLAT -> border(1.dp, colors.outlineVariant.copy(alpha = 0.72f), resolvedShape)
    }
}

@Composable
fun tallyNavigationBarColor(): Color {
    val style = LocalTallyBookStyle.current
    val colors = MaterialTheme.colorScheme
    return when (style) {
        UiStyle.GLASS -> colors.surface.copy(alpha = 0.76f)
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
