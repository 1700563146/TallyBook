package com.tallybook.feature.transactions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Category
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps a persisted `iconKey` to a vector.
 *
 * The database stores the key (a String), not a resource id: resource ids are unstable across builds
 * and would break after any asset change. An unknown key falls back to a generic icon rather than
 * crashing, which is what keeps old rows renderable after a category icon is renamed.
 */
fun categoryIcon(iconKey: String?): ImageVector = when (iconKey) {
    "restaurant" -> Icons.Filled.Restaurant
    "directions_bus" -> Icons.Filled.DirectionsBus
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "home" -> Icons.Filled.Home
    "celebration" -> Icons.Filled.Celebration
    "local_hospital" -> Icons.Filled.LocalHospital
    "school" -> Icons.Filled.School
    "redeem" -> Icons.Filled.Redeem
    "phone" -> Icons.Filled.Phone
    "payments" -> Icons.Filled.Payments
    "emoji_events" -> Icons.Filled.EmojiEvents
    "trending_up" -> Icons.Filled.TrendingUp
    else -> Icons.Filled.Category
}
