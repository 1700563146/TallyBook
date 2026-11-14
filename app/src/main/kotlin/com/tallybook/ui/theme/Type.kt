package com.tallybook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography notes that matter for a ledger:
 * - Amounts use tabular figures (`tnum`) so digits line up column-to-column. Proportional figures
 *   make a list of numbers read as noise.
 * - The amount is one step larger than its label: the number is the content, the label metadata.
 */
val TallyBookTypography: Typography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontFeatureSettings = "tnum"),
        titleMedium = base.titleMedium.copy(fontFeatureSettings = "tnum"),
        titleLarge = base.titleLarge.copy(fontFeatureSettings = "tnum"),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            fontFeatureSettings = "tnum",
        ),
    )
}
