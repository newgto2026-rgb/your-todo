package com.neo.yourtodo.core.domain.repository

import com.neo.yourtodo.core.model.Category
import kotlinx.coroutines.flow.Flow

interface TodoCategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun addCategory(name: String, colorHex: String?, icon: String?): Result<Long>
    suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit>
    suspend fun deleteCategory(id: Long): Result<Unit>
}
