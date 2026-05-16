package com.neo.yourtodo.core.data.repository.todo

import com.neo.yourtodo.core.data.mapper.toDomain
import com.neo.yourtodo.core.database.dao.CategoryDao
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.model.Category
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
internal class TodoCategoryStore @Inject constructor(
    private val categoryDao: CategoryDao,
    private val userPreferencesDataSource: UserPreferencesDataSource
) {
    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeCategories().map { categories -> categories.map { it.toDomain() } }

    suspend fun addCategory(name: String, colorHex: String?, icon: String?): Long {
        ensureUniqueCategoryName(name, excludeCategoryId = null)
        val now = System.currentTimeMillis()
        return categoryDao.insert(
            CategoryEntity(
                name = name,
                colorHex = colorHex,
                icon = icon,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateCategory(id: Long, name: String, colorHex: String?, icon: String?) {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        ensureUniqueCategoryName(name, excludeCategoryId = id)
        categoryDao.update(
            existing.copy(
                name = name,
                colorHex = colorHex,
                icon = icon,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteCategory(id: Long) {
        val existing = categoryDao.getCategoryById(id) ?: throw IllegalStateException("Category not found")
        categoryDao.delete(existing)

        if (userPreferencesDataSource.selectedTodoCategoryFilter.first() == id) {
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
        }
    }

    suspend fun requireCategoryExists(categoryId: Long?) {
        if (categoryId != null && categoryDao.getCategoryById(categoryId) == null) {
            throw IllegalArgumentException("Category not found")
        }
    }

    private suspend fun ensureUniqueCategoryName(name: String, excludeCategoryId: Long?) {
        val duplicate = categoryDao.getCategoryByName(name)
        if (duplicate != null && duplicate.id != excludeCategoryId) {
            throw IllegalArgumentException("Category name already exists")
        }
    }
}
