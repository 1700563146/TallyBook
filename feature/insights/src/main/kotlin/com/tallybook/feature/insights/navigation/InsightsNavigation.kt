package com.tallybook.feature.insights.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tallybook.feature.insights.detail.CategoryDetailScreen
import com.tallybook.feature.insights.ui.InsightsScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface InsightsRoute {

    @Serializable
    data object Insights : InsightsRoute

    /**
     * Drill-down for one category slice.
     *
     * The window is passed in as raw bounds rather than re-derived, so the list always covers exactly
     * the numbers shown on the slice the user tapped. [categoryId] is null for the "未分类" bucket.
     */
    @Serializable
    data class CategoryDetail(
        val categoryId: String? = null,
        val fromInclusive: Long = 0L,
        val toExclusive: Long = 0L,
        val categoryName: String = "",
    ) : InsightsRoute
}

fun NavGraphBuilder.insightsGraph(navController: NavController) {
    composable<InsightsRoute.Insights> {
        InsightsScreen(
            onCategoryClick = { categoryId, fromInclusive, toExclusive, categoryName ->
                navController.navigate(
                    InsightsRoute.CategoryDetail(
                        categoryId = categoryId,
                        fromInclusive = fromInclusive,
                        toExclusive = toExclusive,
                        categoryName = categoryName,
                    ),
                )
            },
        )
    }

    composable<InsightsRoute.CategoryDetail> {
        CategoryDetailScreen(onBack = { navController.popBackStack() })
    }
}
