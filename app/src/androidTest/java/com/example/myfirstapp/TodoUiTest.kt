package com.example.myfirstapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myfirstapp.app.MainActivity
import com.example.myfirstapp.core.database.AppDatabase
import com.example.myfirstapp.core.datastore.source.UserPreferencesDataSource
import com.example.myfirstapp.core.domain.usecase.AddTodoUseCase
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodoUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var addTodoUseCase: AddTodoUseCase

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var userPreferencesDataSource: UserPreferencesDataSource

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            appDatabase.clearAllTables()
            userPreferencesDataSource.setSelectedTodoFilter(TodoFilter.ALL)
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
            userPreferencesDataSource.setSelectedTodoPriorityFilter(TodoPriorityFilter.ALL)
        }
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun mainScreen_showsCoreUi() {
        composeTestRule.onNodeWithText("TODO").assertIsDisplayed()
        tabNode("all").assertIsSelected()
        tabNode("today").assertIsNotSelected()
        tabNode("completed").assertIsNotSelected()
        tabNode("calendar").assertIsNotSelected()
    }

    @Test
    fun addTaskFlow_opensBottomSheet_andAddsItem() {
        val title = "UI Test ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithTag("add_fab").performClick()
        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithText("New Task").assertIsDisplayed()

        composeTestRule.onNodeWithTag("task_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("task_title_input").assertTextContains(title)
        composeTestRule.onNodeWithTag("save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun backPress_onTopLevelTab_withoutOverlay_finishesActivity() {
        tabNode("today").performClick()
        tabNode("today").assertIsSelected()

        tabNode("completed").performClick()
        tabNode("completed").assertIsSelected()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            activityScenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun backPress_whenBottomSheetOpen_closesBottomSheetFirst() {
        composeTestRule.onNodeWithTag("add_fab").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText("New Task").assertIsDisplayed()
        composeTestRule.waitForIdle()

        pressBack()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("New Task").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onAllNodesWithText("New Task").assertCountEquals(0)
        tabNode("all").assertIsSelected()
    }

    @Test
    fun backPress_onFirstTab_finishesActivity() {
        tabNode("all").assertIsSelected()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            activityScenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun todayPlanner_showsOverdueTimedDueTodayAndHighPrioritySections() {
        val today = LocalDate.now()
        runBlocking {
            addTodoUseCase(
                title = "QA overdue",
                dueDate = today.minusDays(1),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = "QA timed today",
                dueDate = today,
                categoryId = null,
                dueTimeMinutes = 10 * 60,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = "QA due today",
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = "QA high priority",
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
        }

        tabNode("today").performClick()

        composeTestRule.onNodeWithText("Overdue").assertIsDisplayed()
        composeTestRule.onNodeWithText("QA overdue").assertIsDisplayed()
        composeTestRule.onNodeWithText("Timed today").assertIsDisplayed()
        composeTestRule.onNodeWithText("QA timed today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Due today").assertIsDisplayed()
        composeTestRule.onNodeWithText("QA due today").assertIsDisplayed()
        composeTestRule.onNodeWithText("High priority").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("QA high priority").assertIsDisplayed()
    }

    @Test
    fun todayPlanner_moveToTomorrow_removesTaskAndUndoRestoresIt() {
        val id = runBlocking {
            addTodoUseCase(
                title = "QA move tomorrow",
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
        }

        tabNode("today").performClick()
        composeTestRule.onNodeWithText("QA move tomorrow").assertIsDisplayed()

        composeTestRule.onNodeWithTag("todo_quick_tomorrow_$id").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("QA move tomorrow").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("QA move tomorrow").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("QA move tomorrow").assertIsDisplayed()
    }

    @Test
    fun todayPlanner_clearDate_removesTaskAndUndoRestoresIt() {
        val id = runBlocking {
            addTodoUseCase(
                title = "QA clear date",
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
        }

        tabNode("today").performClick()
        composeTestRule.onNodeWithText("QA clear date").assertIsDisplayed()

        composeTestRule.onNodeWithTag("todo_quick_clear_date_$id").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("QA clear date").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("QA clear date").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("QA clear date").assertIsDisplayed()
    }

    private fun tabNode(name: String) =
        composeTestRule.onNodeWithTag("app_tab_$name", useUnmergedTree = true)

    private fun ComposeTestRule.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = 5_000
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
