package com.neo.yourtodo.core.database.dao

import androidx.room.Room
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.database.entity.CategoryEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CategoryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertObserveAndQueryByName_workAsExpected() = runTest {
        dao.insert(
            CategoryEntity(name = "work", colorHex = "#111111", icon = "briefcase", createdAt = 1L, updatedAt = 1L)
        )
        dao.insert(
            CategoryEntity(name = "Personal", colorHex = null, icon = null, createdAt = 2L, updatedAt = 2L)
        )

        val observed = dao.observeCategories().first()
        assertThat(observed.map { it.name }).containsExactly("Personal", "work").inOrder()

        val byName = dao.getCategoryByName("WORK")
        assertThat(byName).isNotNull()
        assertThat(byName?.name).isEqualTo("work")
    }

    @Test
    fun updateAndDelete_persistChanges() = runTest {
        val id = dao.insert(
            CategoryEntity(name = "Before", colorHex = null, icon = null, createdAt = 1L, updatedAt = 1L)
        )
        val existing = dao.getCategoryById(id)!!

        dao.update(existing.copy(name = "After", colorHex = "#123456", icon = "star", updatedAt = 3L))
        val updated = dao.getCategoryById(id)
        assertThat(updated?.name).isEqualTo("After")
        assertThat(updated?.colorHex).isEqualTo("#123456")
        assertThat(updated?.icon).isEqualTo("star")

        dao.delete(updated!!)
        assertThat(dao.getCategoryById(id)).isNull()
    }
}
