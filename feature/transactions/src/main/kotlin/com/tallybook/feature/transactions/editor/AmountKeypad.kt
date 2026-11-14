package com.tallybook.feature.transactions.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tallybook.core.model.money.AmountKey

/**
 * Calculator-style keypad.
 *
 * Layout follows the calculator everyone already knows: clear/backspace on top, digits in a 3x3
 * block, operators in the right column, `=` at the end of the numeric block and a wide `0` beneath.
 * Matching the physical layout of a calculator means the muscle memory transfers — a bookkeeping
 * keypad that rearranges these keys makes every entry a conscious decision.
 *
 * Buttons are **rectangular with a small radius**, not pills: a grid of pills reads as a set of
 * unrelated round buttons, while a rectangle grid reads as a keypad (and packs tighter, leaving more
 * room for the form above).
 */
@Composable
fun AmountKeypad(
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    ColumnKeypad(onKey = onKey, modifier = modifier)
}

@Composable
private fun ColumnKeypad(
    onKey: (AmountKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KeyRow {
            Key("AC", KeyKind.FUNCTION, Modifier.weight(1f)) { onKey(AmountKey.CLEAR) }
            Key("⌫", KeyKind.FUNCTION, Modifier.weight(1f)) { onKey(AmountKey.BACKSPACE) }
            Key("÷", KeyKind.OPERATOR, Modifier.weight(1f)) { onKey(AmountKey.DIVIDE) }
            Key("×", KeyKind.OPERATOR, Modifier.weight(1f)) { onKey(AmountKey.MULTIPLY) }
        }
        KeyRow {
            Key("7", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D7) }
            Key("8", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D8) }
            Key("9", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D9) }
            Key("−", KeyKind.OPERATOR, Modifier.weight(1f)) { onKey(AmountKey.MINUS) }
        }
        KeyRow {
            Key("4", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D4) }
            Key("5", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D5) }
            Key("6", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D6) }
            Key("+", KeyKind.OPERATOR, Modifier.weight(1f)) { onKey(AmountKey.PLUS) }
        }
        KeyRow {
            Key("1", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D1) }
            Key("2", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D2) }
            Key("3", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.D3) }
            Key("=", KeyKind.EQUALS, Modifier.weight(1f)) { onKey(AmountKey.EQUALS) }
        }
        KeyRow {
            // A wide 0 mirrors a physical calculator; the thumb finds it without looking.
            Key("0", KeyKind.DIGIT, Modifier.weight(3f)) { onKey(AmountKey.D0) }
            Key(".", KeyKind.DIGIT, Modifier.weight(1f)) { onKey(AmountKey.DOT) }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

private enum class KeyKind { DIGIT, OPERATOR, FUNCTION, EQUALS }

@Composable
private fun Key(
    label: String,
    kind: KeyKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // One shared shape for every key: a consistent radius is what makes the grid read as a keypad.
    val shape = RoundedCornerShape(10.dp)
    when (kind) {
        KeyKind.DIGIT -> Button(
            onClick = onClick,
            shape = shape,
            modifier = modifier.height(54.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) { Text(label, style = MaterialTheme.typography.titleLarge) }

        KeyKind.EQUALS -> Button(
            onClick = onClick,
            shape = shape,
            modifier = modifier.height(54.dp),
            contentPadding = PaddingValues(0.dp),
        ) { Text(label, style = MaterialTheme.typography.titleLarge) }

        KeyKind.OPERATOR, KeyKind.FUNCTION -> OutlinedButton(
            onClick = onClick,
            shape = shape,
            modifier = modifier.height(54.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
        ) { Text(label, style = MaterialTheme.typography.titleMedium) }
    }
}
