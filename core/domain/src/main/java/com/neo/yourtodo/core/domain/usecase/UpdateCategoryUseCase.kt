package com.neo.yourtodo.core.domain.usecase

import com.neo.yourtodo.core.domain.repository.TodoCategoryRepository
import java.util.Locale
import javax.inject.Inject

class UpdateCategoryUseCase @Inject constructor(
    private val repository: TodoCategoryRepository
) {
    suspend operator fun invoke(id: Long, name: String, colorHex: String?, icon: String?): Result<Unit> {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Category name must not be blank"))
        }
        if (colorHex != null && colorHex.isNotBlank() && !COLOR_HEX_REGEX.matches(colorHex)) {
            return Result.failure(IllegalArgumentException("Invalid color hex"))
        }
        val normalizedColor = colorHex?.ifBlank { null }?.uppercase(Locale.ROOT)
        val normalizedIcon = icon?.ifBlank { null }
        return repository.updateCategory(id, normalizedName, normalizedColor, normalizedIcon)
    }

    private companion object {
        val COLOR_HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    }
}
