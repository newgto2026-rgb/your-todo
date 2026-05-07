package com.neo.yourtodo.core.data.mapper

import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.neo.yourtodo.core.model.Category
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryMapperTest {

    @Test
    fun toDomain_mapsFields() {
        val entity = CategoryEntity(
            id = 7L,
            name = "Work",
            colorHex = "#112233",
            icon = "briefcase",
            createdAt = 100L,
            updatedAt = 200L
        )

        val domain = entity.toDomain()

        assertThat(domain).isEqualTo(
            Category(
                id = 7L,
                name = "Work",
                colorHex = "#112233",
                icon = "briefcase",
                createdAt = 100L,
                updatedAt = 200L
            )
        )
    }
}
