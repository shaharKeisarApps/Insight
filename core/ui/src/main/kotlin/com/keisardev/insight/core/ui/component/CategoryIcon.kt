package com.keisardev.insight.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.keisardev.insight.core.model.Category
import com.keisardev.insight.core.model.IncomeCategory

/**
 * Resolves a category icon name to its Material icon.
 */
fun getCategoryIcon(iconName: String): ImageVector {
    return when (iconName) {
        // Expense category icons
        "restaurant" -> Icons.Default.Restaurant
        "directions_car" -> Icons.Default.DirectionsCar
        "movie" -> Icons.Default.Movie
        "shopping_bag" -> Icons.Default.ShoppingBag
        "receipt" -> Icons.Default.Receipt
        "medical_services" -> Icons.Default.HealthAndSafety
        // Income category icons
        "payments" -> Icons.Default.Payments
        "work" -> Icons.Default.Work
        "trending_up" -> Icons.Default.TrendingUp
        "home" -> Icons.Default.Home
        "card_giftcard" -> Icons.Default.CardGiftcard
        "stars" -> Icons.Default.Star
        else -> Icons.Default.MoreHoriz
    }
}

/**
 * Converts a color hex value (ARGB format like 0xFFE57373) to Compose Color.
 */
fun Long.toComposeColor(): Color = Color(this.toInt())

/**
 * Gets the Compose Color from a Category.
 */
val Category.color: Color
    get() = colorHex.toComposeColor()

/**
 * A composable that displays a category icon within a colored circle.
 */
@Composable
fun CategoryIconCircle(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val categoryColor = category.color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(categoryColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = getCategoryIcon(category.icon),
            contentDescription = category.name,
            tint = categoryColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Gets the Compose Color from an IncomeCategory.
 */
val IncomeCategory.color: Color
    get() = colorHex.toComposeColor()

/**
 * A composable that displays an income category icon within a colored circle.
 */
@Composable
fun IncomeCategoryIconCircle(
    category: IncomeCategory,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val categoryColor = category.color
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(categoryColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = getCategoryIcon(category.icon),
            contentDescription = category.name,
            tint = categoryColor,
            modifier = Modifier.size(iconSize),
        )
    }
}
