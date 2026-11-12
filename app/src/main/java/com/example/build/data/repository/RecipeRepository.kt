package com.example.build.data.repository

import com.example.build.data.api.TheMealDbApi
import com.example.build.data.model.MealDetail
import com.example.build.data.model.MealSummary

class RecipeRepository(
    private val api: TheMealDbApi = TheMealDbApi.create()
) {
    suspend fun getMealsByIngredient(ingredient: String): Result<List<MealSummary>> {
        return runCatching {
            val response = api.filterByIngredient(ingredient)
            response.meals ?: emptyList()
        }
    }

    suspend fun searchMealsByName(name: String): Result<List<MealDetail>> {
        return runCatching {
            val response = api.searchMeals(name)
            response.meals ?: emptyList()
        }
    }

    suspend fun getMealDetails(id: String): Result<MealDetail?> {
        return runCatching {
            val response = api.lookupMealDetails(id)
            response.meals?.firstOrNull()
        }
    }
}
