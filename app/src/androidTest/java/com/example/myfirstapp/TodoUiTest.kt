package com.example.myfirstapp

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfirstapp.app.MainActivity
import com.example.myfirstapp.core.database.AppDatabase
import com.example.myfirstapp.core.datastore.source.UserPreferencesDataSource
import com.example.myfirstapp.core.domain.repository.TodoItemRepository
import com.example.myfirstapp.core.domain.usecase.AddTodoUseCase
import com.example.myfirstapp.core.model.ReminderRepeatType
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriority
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.feature.todo.impl.R as TodoImplR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodoUiTest {
    private companion object {
        const val UiTimeoutMillis = 15_000L
    }

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var addTodoUseCase: AddTodoUseCase

    @Inject
    lateinit var todoItemRepository: TodoItemRepository

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var userPreferencesDataSource: UserPreferencesDataSource

    private lateinit var activityScenario: ActivityScenario<MainActivity>
    private lateinit var newTaskTitle: String
    private lateinit var editTaskTitle: String
    private lateinit var undoText: String

    @Before
    fun setup() {
        hiltRule.inject()
        newTaskTitle = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_editor_title_new_task)
        editTaskTitle = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_editor_title_edit_task)
        undoText = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_action_undo)
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
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag("task_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("task_title_input").assertTextContains(title)
        composeTestRule.onNodeWithTag("save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
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
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            activityScenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun backPress_fromSecondTab_doesNotReturnToFirstTab() {
        tabNode("all").assertIsSelected()
        tabNode("today").performClick()
        tabNode("today").assertIsSelected()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            activityScenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun backPress_whenBottomSheetOpen_closesBottomSheetFirst() {
        composeTestRule.onNodeWithTag("add_fab").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()
        composeTestRule.waitForIdle()

        pressBack()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(newTaskTitle).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)
        tabNode("all").assertIsSelected()
    }

    @Test
    fun closeButton_whenBottomSheetOpen_closesBottomSheet() {
        composeTestRule.onNodeWithTag("add_fab").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag("task_edit_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(newTaskTitle).fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)
    }

    @Test
    fun listOverflowDelete_confirmsAndDeletesItem() {
        val title = "Delete UI ${System.currentTimeMillis()}"
        val id = runBlocking {
            addTodoUseCase(
                title = title,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
        }

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("todo_row_more_$id", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("todo_row_delete_$id", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("delete_confirmation_dialog")
        composeTestRule.onNodeWithTag("confirm_delete_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onAllNodesWithText(title).assertCountEquals(0)
    }

    @Test
    fun completedTabClearCompleted_confirmsAndDeletesCompletedItems() {
        val timestamp = System.currentTimeMillis()
        val firstDoneTitle = "Done A $timestamp"
        val secondDoneTitle = "Done B $timestamp"
        val activeTitle = "Active $timestamp"

        runBlocking {
            val firstDoneId = addTodoUseCase(
                title = firstDoneTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.LOW
            ).getOrThrow()
            val secondDoneId = addTodoUseCase(
                title = secondDoneTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            addTodoUseCase(
                title = activeTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(firstDoneId)
            todoItemRepository.toggleTodoDone(secondDoneId)
        }

        tabNode("completed").performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(firstDoneTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(secondDoneTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("clear_completed_button").performClick()
        composeTestRule.waitUntilNodeExists("delete_confirmation_dialog")
        composeTestRule.onNodeWithTag("confirm_delete_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(firstDoneTitle).fetchSemanticsNodes().isEmpty() &&
                composeTestRule.onAllNodesWithText(secondDoneTitle).fetchSemanticsNodes().isEmpty()
        }
        tabNode("all").performClick()
        composeTestRule.onNodeWithText(activeTitle).assertIsDisplayed()
    }

    @Test
    fun backPress_afterAddTaskForDate_keepsSelectedCalendarDate() {
        tabNode("calendar").performClick()
        tabNode("calendar").assertIsSelected()

        val targetDate = nextSelectableDate()
        val expectedDateLabel = agendaDateLabel(targetDate)

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithText(expectedDateLabel).assertIsDisplayed()

        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(expectedDateLabel).fetchSemanticsNodes().isNotEmpty()
        }

        tabNode("calendar").assertIsSelected()
        composeTestRule.onNodeWithText(expectedDateLabel).assertIsDisplayed()
    }

    @Test
    fun calendar_addTaskForDate_opensBottomSheet() {
        tabNode("calendar").performClick()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()

        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()
    }

    @Test
    fun calendar_addTaskForDate_prefillsSelectedDateInEditor() {
        tabNode("calendar").performClick()
        val targetDate = nextSelectableDate()

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_title_input")

        composeTestRule.onNodeWithText(targetDate.toString()).assertIsDisplayed()
    }

    @Test
    fun calendar_addTaskForDate_saveTask_showsInAgendaList() {
        tabNode("calendar").performClick()
        val targetDate = nextSelectableDate()
        val title = "Calendar Add ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithTag("task_title_input").performTextInput(title)
        composeTestRule.onNodeWithTag("save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(agendaDateLabel(targetDate)).assertIsDisplayed()
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun calendar_closeButton_afterAddTaskForDate_keepsSelectedDate() {
        tabNode("calendar").performClick()
        val targetDate = nextSelectableDate()
        val expectedDateLabel = agendaDateLabel(targetDate)

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithTag("task_edit_close").performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(expectedDateLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(expectedDateLabel).assertIsDisplayed()
    }

    @Test
    fun calendar_clickAgendaTodo_opensEditBottomSheet() {
        val targetDate = nextSelectableDate()
        val title = "Agenda Edit ${System.currentTimeMillis()}"
        runBlocking {
            addTodoUseCase(
                title = title,
                dueDate = targetDate,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
        }

        tabNode("calendar").performClick()
        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText(title).performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(editTaskTitle).assertIsDisplayed()
        composeTestRule.onNodeWithTag("task_title_input").assertTextContains(title)
    }

    @Test
    fun calendar_monthNavigation_nextAndPrevious_areInteractive() {
        tabNode("calendar").performClick()
        val today = LocalDate.now()

        composeTestRule.onNodeWithTag("calendar_next_month").performClick()
        composeTestRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_prev_month").performClick()
        composeTestRule.onNodeWithTag("calendar_day_$today").assertIsDisplayed()
    }

    @Test
    fun calendar_dateSelection_updatesAgendaLabel() {
        tabNode("calendar").performClick()
        val targetDate = nextSelectableDate()

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithText(agendaDateLabel(targetDate)).assertIsDisplayed()
    }

    @Test
    fun calendar_defaultState_showsAgendaSectionAndAddButton() {
        tabNode("calendar").performClick()
        composeTestRule.onNodeWithTag("calendar_day_todo_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_todo_list_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").assertIsDisplayed()
    }

    @Test
    fun backPress_onFirstTab_finishesActivity() {
        tabNode("all").assertIsSelected()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
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
        tabNode("today").assertIsSelected()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText("QA overdue").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("QA overdue").assertIsDisplayed()
        composeTestRule.onNodeWithText("QA timed today").assertIsDisplayed()
        composeTestRule.onNodeWithText("QA due today").assertIsDisplayed()
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
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText("QA move tomorrow").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(undoText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(undoText).performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
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
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText("QA clear date").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(undoText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(undoText).performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText("QA clear date").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("QA clear date").assertIsDisplayed()
    }

    private fun tabNode(name: String) =
        composeTestRule.onNodeWithTag("app_tab_$name", useUnmergedTree = true)

    private fun nextSelectableDate(): LocalDate {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val targetDay = if (today.dayOfMonth < currentMonth.lengthOfMonth()) {
            today.dayOfMonth + 1
        } else {
            max(1, today.dayOfMonth - 1)
        }
        return currentMonth.atDay(min(targetDay, currentMonth.lengthOfMonth()))
    }

    private fun agendaDateLabel(date: LocalDate): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(
            com.example.myfirstapp.feature.calendar.impl.R.string.calendar_agenda_date_label,
            date.format(DateTimeFormatter.ofPattern("yyyy MMM d", Locale.getDefault()))
        )
    }

    private fun ComposeTestRule.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
