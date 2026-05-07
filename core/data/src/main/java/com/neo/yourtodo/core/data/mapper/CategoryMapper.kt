package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.model.Category

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        colorHex = colorHex,
        icon = icon,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
