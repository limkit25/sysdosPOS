package com.sysdos.kasirpintar.data.dao

import androidx.room.*
import com.sysdos.kasirpintar.data.model.Recipe

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe WHERE productId = :productId")
    suspend fun getIngredientsForProduct(productId: Int): List<Recipe>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipes(recipes: List<Recipe>)

    @Query("DELETE FROM recipe WHERE productId = :productId")
    suspend fun deleteRecipeForProduct(productId: Int)
}
