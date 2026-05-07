package com.example.myfirstapp

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import org.junit.Assert.assertTrue
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
        grantNotificationPermissionIfNeeded()
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
    fun quickAddFlow_addsItemWithoutOpeningBottomSheet() {
        val title = "Quick Add UI ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithTag("quick_add_open").performClick()
        composeTestRule.waitUntilNodeExists("quick_add_title_input")
        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)

        composeTestRule.onNodeWithTag("quick_add_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("quick_add_submit").performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_add_slot").assertIsDisplayed()
    }

    @Test
    fun quickAddFlow_onToday_addsItemToTodayList() {
        val title = "Quick Add Today ${System.currentTimeMillis()}"

        tabNode("today").performClick()
        tabNode("today").assertIsSelected()
        composeTestRule.onNodeWithTag("quick_add_open").performClick()
        composeTestRule.waitUntilNodeExists("quick_add_title_input")

        composeTestRule.onNodeWithTag("quick_add_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("quick_add_submit").performClick()

        composeTestRule.waitUntilTodoScreenText("today", title)
        composeTestRule.onTodoScreenText("today", title).assertIsDisplayed()
    }

    @Test
    fun completedTab_hidesQuickAddAndFabOpensDetailAdd() {
        tabNode("completed").performClick()
        tabNode("completed").assertIsSelected()

        composeTestRule.onAllNodesWithTag("quick_add_open").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("quick_add_slot").assertCountEquals(0)

        openDetailAddFromFab()
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()
    }

    @Test
    fun backPress_onTopLevelTab_returnsToPreviousTab() {
        tabNode("today").performClick()
        tabNode("today").assertIsSelected()

        tabNode("completed").performClick()
        tabNode("completed").assertIsSelected()

        pressBackUnconditionally()
        tabNode("today").assertIsSelected()
    }

    @Test
    fun backPress_fromSecondTab_returnsToStartTab() {
        tabNode("all").assertIsSelected()
        tabNode("today").performClick()
        tabNode("today").assertIsSelected()

        pressBackUnconditionally()
        tabNode("all").assertIsSelected()
    }

    @Test
    fun backPress_onStartTab_finishesActivity() {
        tabNode("all").assertIsSelected()

        pressBackUnconditionally()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            activityScenario.state == Lifecycle.State.DESTROYED
        }
    }

    @Test
    fun backPress_whenBottomSheetOpen_closesBottomSheetFirst() {
        openDetailAddFromFab()
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
        openDetailAddFromFab()
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        composeTestRule.onNodeWithTag("task_edit_close").performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(newTaskTitle).fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)
    }

    @Test
    fun bottomSheet_open_wrapsContentHeightAndShowsEntireEditor() {
        openDetailAddFromFab()
        composeTestRule.waitUntilNodeExists("todo_editor_sheet")

        composeTestRule.onNodeWithTag("task_edit_close").assertIsDisplayed()
        composeTestRule.onNodeWithTag("task_title_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("due_date_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("due_time_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("priority_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_button").assertIsDisplayed()

        val sheetBounds = composeTestRule.onNodeWithTag("todo_editor_sheet").getUnclippedBoundsInRoot()
        val sheetHeight = sheetBounds.bottom - sheetBounds.top
        val displayMetrics = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .resources
            .displayMetrics
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density

        assertTrue(
            "Bottom sheet should wrap editor content instead of occupying the full root height.",
            sheetHeight.value < screenHeightDp * 0.9f
        )
        assertTrue(
            "Bottom sheet should rise from the bottom and leave background content visible above it.",
            sheetBounds.top.value > 24f
        )
    }

    @Test
    fun bottomSheet_afterRepeatedTabRoundTrips_opensAndCloses() {
        repeat(10) {
            tabNode("calendar").performClick()
            tabNode("all").performClick()

            openDetailAddFromFab()
            composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

            composeTestRule.onNodeWithTag("task_edit_close").performClick()
            composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
                composeTestRule.onAllNodesWithText(newTaskTitle).fetchSemanticsNodes().isEmpty()
            }
            composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)
        }
    }

    @Test
    fun bottomSheet_openThenSwitchTab_closesOverlayAndDoesNotReappear() {
        openDetailAddFromFab()
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        tabNode("calendar").performClick()
        composeTestRule.waitUntilNodeDoesNotExist("task_edit_close")
        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)

        tabNode("all").performClick()
        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)

        openDetailAddFromFab()
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()
    }

    @Test
    fun todoTabs_afterRepeatedRoundTrips_showCurrentTabContentOnly() {
        val timestamp = System.currentTimeMillis()
        val todayTitle = "Tab Today $timestamp"
        val completedTitle = "Tab Done $timestamp"
        val allOnlyTitle = "Tab All $timestamp"

        val todayId = runBlocking {
            val todayTodoId = addTodoUseCase(
                title = todayTitle,
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            val completedId = addTodoUseCase(
                title = completedTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = allOnlyTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
            todayTodoId
        }

        repeat(10) {
            tabNode("today").performClick()
            tabNode("today").assertIsSelected()
            composeTestRule.waitUntilTodoScreenTag("today", "todo_quick_tomorrow_$todayId")
            composeTestRule.onTodoScreenText("today", todayTitle).assertIsDisplayed()
            composeTestRule.assertNoTodoScreenTag("today", "clear_completed_button")

            tabNode("completed").performClick()
            tabNode("completed").assertIsSelected()
            composeTestRule.waitUntilTodoScreenTag("completed", "clear_completed_button")
            composeTestRule.onTodoScreenText("completed", completedTitle).assertIsDisplayed()
            composeTestRule.assertNoTodoScreenTag("completed", "todo_quick_tomorrow_$todayId")

            tabNode("all").performClick()
            tabNode("all").assertIsSelected()
            composeTestRule.waitUntilTodoScreenText("all", allOnlyTitle)
            composeTestRule.onTodoScreenText("all", allOnlyTitle).assertIsDisplayed()
            composeTestRule.assertNoTodoScreenTag("all", "clear_completed_button")
            composeTestRule.assertNoTodoScreenTag("all", "todo_quick_tomorrow_$todayId")
        }
    }

    @Test
    fun todoTabs_switchToToday_doesNotKeepPreviousTabSceneWhenClockPaused() {
        val timestamp = System.currentTimeMillis()
        val todayTitle = "Paused Clock Today $timestamp"
        val allOnlyTitle = "Paused Clock All $timestamp"

        runBlocking {
            addTodoUseCase(
                title = todayTitle,
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = allOnlyTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
        }

        tabNode("today").performClick()
        tabNode("today").assertIsSelected()
        composeTestRule.waitUntilTodoScreenText("today", todayTitle)
        composeTestRule.onTodoScreenText("today", todayTitle).assertIsDisplayed()

        tabNode("all").performClick()
        tabNode("all").assertIsSelected()
        composeTestRule.waitUntilTodoScreenText("all", allOnlyTitle)
        composeTestRule.onTodoScreenText("all", allOnlyTitle).assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        try {
            tabNode("today").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()

            tabNode("today").assertIsSelected()
            composeTestRule.onTodoScreenText("today", todayTitle).assertIsDisplayed()
            composeTestRule.onAllNodes(
                hasText(allOnlyTitle) and hasAnyAncestor(hasTestTag("todo_screen_today")),
                useUnmergedTree = true
            ).assertCountEquals(0)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun todoTabs_switchToCompleted_doesNotKeepTodaySceneWhenClockPaused() {
        val timestamp = System.currentTimeMillis()
        val todayTitle = "Paused Today To Done $timestamp"
        val completedTitle = "Paused Completed $timestamp"

        val todayId = runBlocking {
            val todayTodoId = addTodoUseCase(
                title = todayTitle,
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            val completedId = addTodoUseCase(
                title = completedTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
            todayTodoId
        }

        tabNode("today").performClick()
        tabNode("today").assertIsSelected()
        composeTestRule.waitUntilTodoScreenTag("today", "todo_quick_tomorrow_$todayId")
        composeTestRule.onTodoScreenText("today", todayTitle).assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        try {
            tabNode("completed").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()

            tabNode("completed").assertIsSelected()
            composeTestRule.onTodoScreenText("completed", completedTitle).assertIsDisplayed()
            composeTestRule.assertNoTodoScreenText("completed", todayTitle)
            composeTestRule.assertNoTodoScreenTag("completed", "todo_quick_tomorrow_$todayId")
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun todoTabs_rapidTaps_firstFrameShowsOnlyLastSelectedTabContent() {
        val timestamp = System.currentTimeMillis()
        val todayTitle = "First Frame Today $timestamp"
        val completedTitle = "First Frame Done $timestamp"
        val allOnlyTitle = "First Frame All $timestamp"

        val todayId = runBlocking {
            val todayTodoId = addTodoUseCase(
                title = todayTitle,
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            val completedId = addTodoUseCase(
                title = completedTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = allOnlyTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
            todayTodoId
        }

        tabNode("completed").performClick()
        tabNode("completed").assertIsSelected()
        composeTestRule.waitUntilNodeExists("clear_completed_button")

        composeTestRule.mainClock.autoAdvance = false
        try {
            tabNode("all").performClick()
            tabNode("completed").performClick()
            tabNode("today").performClick()
            composeTestRule.mainClock.advanceTimeByFrame()

            tabNode("today").assertIsSelected()
            composeTestRule.onTodoScreenText("today", todayTitle).assertIsDisplayed()
            composeTestRule.assertNoTodoScreenText("today", completedTitle)
            composeTestRule.assertNoTodoScreenText("today", allOnlyTitle)
            composeTestRule.assertNoTodoScreenTag("today", "clear_completed_button")
            composeTestRule.onTodoScreenTag("today", "todo_quick_tomorrow_$todayId").assertIsDisplayed()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun todoTabs_afterRapidSwitching_settlesOnLastSelectedTabContent() {
        val timestamp = System.currentTimeMillis()
        val todayTitle = "Rapid Today $timestamp"
        val completedTitle = "Rapid Done $timestamp"
        val allOnlyTitle = "Rapid All $timestamp"

        val todayId = runBlocking {
            val todayTodoId = addTodoUseCase(
                title = todayTitle,
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            val completedId = addTodoUseCase(
                title = completedTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            addTodoUseCase(
                title = allOnlyTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
            todayTodoId
        }

        repeat(20) {
            tabNode("today").performClick()
            tabNode("completed").performClick()
            tabNode("all").performClick()
        }
        tabNode("completed").performClick()

        tabNode("completed").assertIsSelected()
        composeTestRule.waitUntilTodoScreenTag("completed", "clear_completed_button")
        composeTestRule.onTodoScreenText("completed", completedTitle).assertIsDisplayed()
        composeTestRule.assertNoTodoScreenText("completed", todayTitle)
        composeTestRule.assertNoTodoScreenText("completed", allOnlyTitle)
        composeTestRule.assertNoTodoScreenTag("completed", "todo_quick_tomorrow_$todayId")
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
        tabNode("completed").assertIsSelected()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(firstDoneTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(secondDoneTitle).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.waitUntilNodeExists("clear_completed_button")
        composeTestRule.onNodeWithTag("clear_completed_button", useUnmergedTree = true).performClick()
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

        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        assertAgendaDateDisplayed(targetDate)

        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithTag("task_edit_close").performClick()

        assertAgendaDateDisplayed(targetDate)
    }

    @Test
    fun calendar_dateSelection_afterBottomSheetDismiss_updatesAgendaLabel() {
        tabNode("calendar").performClick()
        val firstDate = nextSelectableDate()
        val secondDate = differentSelectableDate(firstDate)

        composeTestRule.onNodeWithTag("calendar_day_$firstDate").performClick()
        assertAgendaDateDisplayed(firstDate)

        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()
        composeTestRule.onNodeWithTag("task_edit_close").performClick()

        assertAgendaDateDisplayed(firstDate)
        composeTestRule.onNodeWithTag("calendar_day_$secondDate").performClick()
        assertAgendaDateChanged(from = firstDate, to = secondDate)
    }

    @Test
    fun calendar_dateSelection_afterBottomSheetOpenAndTabSwitch_updatesAgendaLabel() {
        tabNode("calendar").performClick()
        val firstDate = nextSelectableDate()
        val secondDate = differentSelectableDate(firstDate)

        composeTestRule.onNodeWithTag("calendar_day_$firstDate").performClick()
        assertAgendaDateDisplayed(firstDate)

        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
        composeTestRule.onNodeWithText(newTaskTitle).assertIsDisplayed()

        tabNode("all").performClick()
        composeTestRule.waitUntilNodeDoesNotExist("task_edit_close")

        tabNode("calendar").performClick()
        assertAgendaDateDisplayed(firstDate)
        composeTestRule.onNodeWithTag("calendar_day_$secondDate").performClick()
        assertAgendaDateDisplayed(secondDate)
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
    fun calendar_dateSelection_afterRepeatedTabRoundTrips_updatesAgendaLabel() {
        tabNode("calendar").performClick()
        val firstDate = nextSelectableDate()
        val secondDate = differentSelectableDate(firstDate)

        composeTestRule.onNodeWithTag("calendar_day_$firstDate").performClick()
        assertAgendaDateDisplayed(firstDate)

        repeat(10) { index ->
            val targetDate = if (index % 2 == 0) secondDate else firstDate
            val previousDate = if (targetDate == secondDate) firstDate else secondDate

            tabNode("all").performClick()
            tabNode("calendar").performClick()
            composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
            assertAgendaDateChanged(from = previousDate, to = targetDate)
        }
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
    fun todayPlanner_showsOverdueTimedAndDueTodaySections() {
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
        composeTestRule.onNodeWithTag("todo_list")
            .performScrollToNode(hasText("QA due today"))
        composeTestRule.onNodeWithText("QA due today")
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("QA high priority").assertCountEquals(0)
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
        tabNode("today").assertIsSelected()
        composeTestRule.onNodeWithText("QA move tomorrow").assertIsDisplayed()

        composeTestRule.waitUntilNodeExists("todo_quick_tomorrow_$id")
        composeTestRule.onNodeWithTag("todo_quick_tomorrow_$id", useUnmergedTree = true).performClick()
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
        tabNode("today").assertIsSelected()
        composeTestRule.onNodeWithText("QA clear date").assertIsDisplayed()

        composeTestRule.waitUntilNodeExists("todo_quick_clear_date_$id")
        composeTestRule.onNodeWithTag("todo_quick_clear_date_$id", useUnmergedTree = true).performClick()
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

    private fun openDetailAddFromFab() {
        composeTestRule.onNodeWithTag("add_fab").performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
    }

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

    private fun differentSelectableDate(date: LocalDate): LocalDate {
        val currentMonth = YearMonth.from(date)
        return if (date.dayOfMonth < currentMonth.lengthOfMonth()) {
            date.plusDays(1)
        } else {
            date.minusDays(1)
        }
    }

    private fun assertAgendaDateDisplayed(date: LocalDate) {
        val label = agendaDateLabel(date)
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    private fun assertAgendaDateChanged(from: LocalDate, to: LocalDate) {
        val oldLabel = agendaDateLabel(from)
        val newLabel = agendaDateLabel(to)
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(newLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(newLabel).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(oldLabel).assertCountEquals(0)
    }

    private fun agendaDateLabel(date: LocalDate): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(
            com.example.myfirstapp.feature.calendar.impl.R.string.calendar_agenda_date_label,
            date.format(DateTimeFormatter.ofPattern("yyyy MMM d", Locale.getDefault()))
        )
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
            .close()
    }

    private fun ComposeTestRule.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeTestRule.waitUntilTodoScreenText(
        filterName: String,
        text: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodes(
                hasText(text) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeTestRule.onTodoScreenText(
        filterName: String,
        text: String
    ) = onNode(
        hasText(text) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
        useUnmergedTree = true
    )

    private fun ComposeTestRule.assertNoTodoScreenText(
        filterName: String,
        text: String
    ) {
        onAllNodes(
            hasText(text) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    private fun ComposeTestRule.waitUntilTodoScreenTag(
        filterName: String,
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodes(
                hasTestTag(tag) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
                useUnmergedTree = true
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ComposeTestRule.onTodoScreenTag(
        filterName: String,
        tag: String
    ) = onNode(
        hasTestTag(tag) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
        useUnmergedTree = true
    )

    private fun ComposeTestRule.assertNoTodoScreenTag(
        filterName: String,
        tag: String
    ) {
        onAllNodes(
            hasTestTag(tag) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    private fun ComposeTestRule.waitUntilNodeDoesNotExist(
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }
}
