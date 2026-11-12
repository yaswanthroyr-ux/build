package com.example.build.data.api

import com.example.build.data.model.MealDetailResponse
import com.example.build.data.model.MealSummaryResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface TheMealDbApi {

    @GET("filter.php")
    suspend fun filterByIngredient(
        @Query("i") ingredient: String
    ): MealSummaryResponse

    @GET("search.php")
    suspend fun searchMeals(
        @Query("s") query: String
    ): MealDetailResponse

    @GET("lookup.php")
    suspend fun lookupMealDetails(
        @Query("i") id: String
    ): MealDetailResponse

    companion object {
        private const val BASE_URL = "https://www.themealdb.com/api/json/v1/1/"

        fun create(): TheMealDbApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TheMealDbApi::class.java)
        }
    }
}
