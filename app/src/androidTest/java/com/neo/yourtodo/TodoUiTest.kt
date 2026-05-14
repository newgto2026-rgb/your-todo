package com.neo.yourtodo

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neo.yourtodo.app.MainActivity
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.repository.TodoItemRepository
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriority
import com.neo.yourtodo.core.model.TodoPriorityFilter
import com.neo.yourtodo.feature.todo.impl.R as TodoImplR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodoUiTest {
    private companion object {
        const val UiTimeoutMillis = 30_000L
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
    private lateinit var todoProfileContentDescription: String

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
        todoProfileContentDescription = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_header_profile_icon)
        runBlocking {
            appDatabase.clearAllTables()
            userPreferencesDataSource.setSelectedTodoFilter(TodoFilter.ALL)
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
            userPreferencesDataSource.setSelectedTodoPriorityFilter(TodoPriorityFilter.ALL)
        }
        grantNotificationPermissionIfNeeded()
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists("app_tab_all")
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun mainScreen_showsCoreUi() {
        composeTestRule.onNodeWithContentDescription("Your Todo").assertIsDisplayed()
        tabNode("all").assertIsSelected()
        tabNode("today").assertIsNotSelected()
        tabNode("completed").assertIsNotSelected()
        tabNode("calendar").assertIsNotSelected()
        composeTestRule.onNodeWithTag("todo_sync_button").assertIsDisplayed()
    }

    @Test
    fun profileMenu_opensAndClosesFromTodoHeader() {
        composeTestRule.onNodeWithContentDescription(todoProfileContentDescription).performClick()

        composeTestRule.waitUntilNodeExists("profile_menu_drawer")
        composeTestRule.onNodeWithTag("profile_menu_nickname").assertTextContains("tester")
        composeTestRule.onNodeWithTag("profile_menu_email").assertTextContains("tester@example.com")
        composeTestRule.onNodeWithTag("profile_menu_copy_nickname").assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_menu_logout").assertIsDisplayed()

        composeTestRule.onNodeWithTag("profile_menu_close").performClick()

        composeTestRule.waitUntilNodeDoesNotExist("profile_menu_drawer")
    }

    @Test
    fun quickAddFlow_addsItemWithoutOpeningBottomSheet() {
        val title = "Quick Add UI ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithTag("quick_add_open").performClick()
        composeTestRule.waitUntilNodeExists("quick_add_title_input")
        composeTestRule.onAllNodesWithText(newTaskTitle).assertCountEquals(0)

        composeTestRule.onNodeWithTag("quick_add_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("quick_add_submit").performClick()

        composeTestRule.waitUntilDisplayedTodoScreenText("all", title)
        composeTestRule.onDisplayedTodoScreenText("all", title).assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_add_slot").assertIsDisplayed()
    }

    @Test
    fun quickAddFlow_withSignedInSessionCreatesPendingCreateTodo() {
        val title = "Quick Add Sync Pending ${System.currentTimeMillis()}"

        composeTestRule.onNodeWithTag("quick_add_open").performClick()
        composeTestRule.waitUntilNodeExists("quick_add_title_input")
        composeTestRule.onNodeWithTag("quick_add_title_input").performClick().performTextInput(title)
        composeTestRule.onNodeWithTag("quick_add_submit").performClick()

        composeTestRule.waitUntilDisplayedTodoScreenText("all", title)
        val saved = runBlocking {
            appDatabase.todoDao().observeTodos().first().single { it.title == title }
        }
        val outbox = waitUntilPendingCreateMutation(saved.id)
        assertEquals("PENDING_CREATE", saved.syncStatus)
        assertEquals("android-test-user", saved.ownerUserId)
        assertEquals(1, outbox.count { it.todoLocalId == saved.id && it.type == "CREATE" })
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
    fun allTabSortMenu_dueDateSortOrdersRowsByNearestDueDate() {
        val timestamp = System.currentTimeMillis()
        val today = LocalDate.now()
        val noDateTitle = "Sort no date $timestamp"
        val futureTitle = "Sort future $timestamp"
        val todayTitle = "Sort today $timestamp"
        var noDateId = -1L
        var futureId = -1L
        var todayId = -1L

        runBlocking {
            noDateId = addTodoUseCase(
                title = noDateTitle,
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            futureId = addTodoUseCase(
                title = futureTitle,
                dueDate = today.plusDays(4),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            todayId = addTodoUseCase(
                title = todayTitle,
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.LOW
            ).getOrThrow()
        }

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(noDateTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(futureTitle).fetchSemanticsNodes().isNotEmpty() &&
                composeTestRule.onAllNodesWithText(todayTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_due_date")
        composeTestRule.onNodeWithTag("todo_sort_option_due_date", useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            rowTop("todo_row_$todayId") < rowTop("todo_row_$futureId") &&
                rowTop("todo_row_$futureId") < rowTop("todo_row_$noDateId")
        }
        assertRowsInVerticalOrder("todo_row_$todayId", "todo_row_$futureId", "todo_row_$noDateId")
    }

    @Test
    fun allTabSortMenu_prioritySortOrdersRowsByHighMediumLow() {
        val timestamp = System.currentTimeMillis()
        var lowId = -1L
        var mediumId = -1L
        var highId = -1L

        runBlocking {
            highId = addTodoUseCase(
                title = "Priority high $timestamp",
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            mediumId = addTodoUseCase(
                title = "Priority medium $timestamp",
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            lowId = addTodoUseCase(
                title = "Priority low $timestamp",
                dueDate = null,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.LOW
            ).getOrThrow()
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$lowId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$mediumId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highId")
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            rowTop("todo_row_$lowId") < rowTop("todo_row_$mediumId") &&
                rowTop("todo_row_$mediumId") < rowTop("todo_row_$highId")
        }
        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_priority")
        composeTestRule.onNodeWithTag("todo_sort_option_priority", useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            rowTop("todo_row_$highId") < rowTop("todo_row_$mediumId") &&
                rowTop("todo_row_$mediumId") < rowTop("todo_row_$lowId")
        }
        assertRowsInVerticalOrder("todo_row_$highId", "todo_row_$mediumId", "todo_row_$lowId")
    }

    @Test
    fun allTabSortMenu_clearsPriorityFilterBeforeSorting() {
        val timestamp = System.currentTimeMillis()
        val lowLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_low)
        var lowId = -1L
        var mediumId = -1L
        var highId = -1L

        runBlocking {
            highId = addTodoUseCase(
                title = "Filter reset high $timestamp",
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            mediumId = addTodoUseCase(
                title = "Filter reset medium $timestamp",
                dueDate = LocalDate.now(),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            lowId = addTodoUseCase(
                title = "Filter reset low $timestamp",
                dueDate = LocalDate.now().plusDays(1),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.LOW
            ).getOrThrow()
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highId")
        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$lowLabel")
            .performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$lowId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$highId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$mediumId")

        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_due_date")
        composeTestRule.onNodeWithTag("todo_sort_option_due_date", useUnmergedTree = true).performClick()

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$mediumId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$lowId")
        assertRowsInVerticalOrder("todo_row_$highId", "todo_row_$mediumId", "todo_row_$lowId")
    }

    @Test
    fun allTabSortMenu_reselectKeepsPriorityFilter() {
        val timestamp = System.currentTimeMillis()
        val highLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_high)
        val today = LocalDate.now()
        var highTodayId = -1L
        var highFutureId = -1L
        var mediumTodayId = -1L

        runBlocking {
            highFutureId = addTodoUseCase(
                title = "Reselect high future $timestamp",
                dueDate = today.plusDays(2),
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            mediumTodayId = addTodoUseCase(
                title = "Reselect medium today $timestamp",
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            highTodayId = addTodoUseCase(
                title = "Reselect high today $timestamp",
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0,
                priority = TodoPriority.HIGH
            ).getOrThrow()
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$mediumTodayId")
        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_due_date")
        composeTestRule.onNodeWithTag("todo_sort_option_due_date", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            rowTop("todo_row_$highTodayId") < rowTop("todo_row_$highFutureId")
        }

        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$highLabel").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highTodayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highFutureId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$mediumTodayId")

        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_due_date")
        composeTestRule.onNodeWithTag("todo_sort_option_due_date", useUnmergedTree = true).performClick()

        composeTestRule.assertTodoScreenRowCount("all", 2)
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highTodayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$highFutureId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$mediumTodayId")
        assertRowsInVerticalOrder("todo_row_$highTodayId", "todo_row_$highFutureId")
    }

    @Test
    fun allTabFilters_keepTodayFutureAndCompletedItemsAvailable() {
        val timestamp = System.currentTimeMillis()
        val today = LocalDate.now()
        val todayTitle = "Exact today medium $timestamp"
        val futureTitle = "Exact future high $timestamp"
        val completedTitle = "Exact completed medium $timestamp"
        var todayId = -1L
        var futureId = -1L
        var completedId = -1L

        runBlocking {
            todayId = addTodoUseCase(
                title = todayTitle,
                dueDate = today,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            futureId = addTodoUseCase(
                title = futureTitle,
                dueDate = today.plusDays(2),
                categoryId = null,
                dueTimeMinutes = 2 * 60,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            completedId = addTodoUseCase(
                title = completedTitle,
                dueDate = null,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
        composeTestRule.assertTodoScreenRowCount("all", 3)
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$completedId")

        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_due_date")
        composeTestRule.onNodeWithTag("todo_sort_option_due_date", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
        composeTestRule.assertTodoScreenRowCount("all", 3)
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$completedId")
        assertRowsInVerticalOrder("todo_row_$todayId", "todo_row_$futureId", "todo_row_$completedId")

        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilNodeExists("todo_sort_option_priority")
        composeTestRule.onNodeWithTag("todo_sort_option_priority", useUnmergedTree = true).performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
        composeTestRule.assertTodoScreenRowCount("all", 3)
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$completedId")
        assertRowsInVerticalOrder("todo_row_$futureId", "todo_row_$todayId", "todo_row_$completedId")

        tabNode("today").performClick()
        tabNode("today").assertIsSelected()
        composeTestRule.waitUntilTodoScreenTag("today", "todo_row_$todayId")
        composeTestRule.assertNoTodoScreenTag("today", "todo_row_$futureId")
        composeTestRule.assertNoTodoScreenTag("today", "todo_row_$completedId")

        tabNode("completed").performClick()
        tabNode("completed").assertIsSelected()
        composeTestRule.waitUntilTodoScreenTag("completed", "todo_row_$completedId")
        composeTestRule.assertNoTodoScreenTag("completed", "todo_row_$todayId")
        composeTestRule.assertNoTodoScreenTag("completed", "todo_row_$futureId")

        tabNode("all").performClick()
        tabNode("all").assertIsSelected()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
        composeTestRule.assertTodoScreenRowCount("all", 3)
    }

    @Test
    fun priorityFilters_restoreAllExactTodayFutureAndCompletedItems() {
        val timestamp = System.currentTimeMillis()
        val today = LocalDate.now()
        val allLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_filter_all)
        val mediumLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_medium)
        val highLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_high)
        var todayId = -1L
        var futureId = -1L
        var completedId = -1L

        runBlocking {
            todayId = addTodoUseCase(
                title = "Priority exact today medium $timestamp",
                dueDate = today,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            futureId = addTodoUseCase(
                title = "Priority exact future high $timestamp",
                dueDate = today.plusDays(2),
                categoryId = null,
                dueTimeMinutes = 2 * 60,
                priority = TodoPriority.HIGH
            ).getOrThrow()
            completedId = addTodoUseCase(
                title = "Priority exact completed medium $timestamp",
                dueDate = null,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            todoItemRepository.toggleTodoDone(completedId)
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")

        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$mediumLabel").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$futureId")

        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$highLabel").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_$completedId")

        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$allLabel").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$todayId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$futureId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$completedId")
    }

    @Test
    fun detailAdd_highPriorityAppearsOnlyInHighPriorityFilter() {
        val title = "UI high priority ${System.currentTimeMillis()}"
        val highLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_high)
        val mediumLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(TodoImplR.string.todo_priority_medium)

        openDetailAddFromFab()
        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithTag("task_title_input").performTextInput(title)
        composeTestRule.onNodeWithTag("priority_high_option", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("save_button").performScrollTo().performClick()

        composeTestRule.waitUntilNodeDoesNotExist("task_title_input")
        composeTestRule.waitUntilDisplayedTodoScreenText("all", title)
        val saved = runBlocking {
            appDatabase.todoDao().observeTodos().first().single { it.title == title }
        }
        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$highLabel").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_${saved.id}")
        composeTestRule.onTodoScreenText("all", title).assertIsDisplayed()

        composeTestRule.onTodoScreenTag("all", "priority_filter_chip_$mediumLabel").performClick()
        composeTestRule.assertNoTodoScreenTag("all", "todo_row_${saved.id}")
    }

    @Test
    fun sortMenu_isScopedToAllTabAndHiddenOnTodayPlanner() {
        composeTestRule.onNodeWithTag("todo_sort_menu_button", useUnmergedTree = true)
            .assertIsDisplayed()

        tabNode("today").performClick()
        tabNode("today").assertIsSelected()

        composeTestRule.assertNoTodoScreenTag("today", "todo_sort_menu_button")
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
    fun backPress_whenAiSheetOpen_closesAiSheetFirst() {
        openAiAddFromFab()
        composeTestRule.onNodeWithTag("ai_todo_sheet").assertIsDisplayed()
        composeTestRule.waitForIdle()

        pressBack()
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithTag("ai_todo_sheet", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }

        tabNode("all").assertIsSelected()
        composeTestRule.onNodeWithTag("quick_add_open").performClick()
        composeTestRule.waitUntilNodeExists("quick_add_title_input")
    }

    @Test
    fun aiSheet_whenSwipedDown_staysOpen() {
        openAiAddFromFab()
        composeTestRule.onNodeWithTag("ai_todo_sheet").assertIsDisplayed()

        composeTestRule.onNodeWithTag("ai_todo_sheet")
            .performTouchInput { swipeDown() }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_todo_sheet").assertIsDisplayed()
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
            sheetHeight.value < screenHeightDp
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
        var allOnlyId = -1L

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
            allOnlyId = addTodoUseCase(
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
            composeTestRule.waitUntilDisplayedTodoScreenTag("all", "todo_row_$allOnlyId")
            composeTestRule.onTodoScreenTag("all", "todo_row_$allOnlyId").assertIsDisplayed()
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
    fun listSwipeDelete_confirmsAndDeletesItem() {
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

        composeTestRule.onNodeWithTag("todo_row_$id", useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
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
    fun allTab_toggleDoneMovesCompletedRowBelowActiveRows() {
        val timestamp = System.currentTimeMillis()
        var firstId = -1L
        var secondId = -1L
        runBlocking {
            firstId = addTodoUseCase(
                title = "Stable first $timestamp",
                dueDate = null,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
            secondId = addTodoUseCase(
                title = "Stable second $timestamp",
                dueDate = null,
                categoryId = null,
                priority = TodoPriority.MEDIUM
            ).getOrThrow()
        }

        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$secondId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$firstId")
        assertRowsInVerticalOrder("todo_row_$secondId", "todo_row_$firstId")

        composeTestRule.onTodoScreenTag("all", "todo_row_toggle_$secondId").performClick()
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$secondId")
        composeTestRule.waitUntilTodoScreenTag("all", "todo_row_$firstId")

        assertRowsInVerticalOrder("todo_row_$firstId", "todo_row_$secondId")
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

        composeTestRule.waitUntilNodeDoesNotExist("task_title_input")
        composeTestRule.waitUntilAgendaTodoDisplayed(title)
        composeTestRule.onNodeWithText(agendaDateLabel(targetDate)).assertIsDisplayed()
        composeTestRule.onNode(
            hasText(title) and hasAnyAncestor(hasTestTag("calendar_day_todo_sheet"))
        ).assertIsDisplayed()
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
        tabNode("calendar").assertIsSelected()
        tabNode("friends").assertIsNotSelected()
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
    fun todayPlanner_moveToTomorrow_undoSnackbarDisappearsAfterTimeout() {
        val id = runBlocking {
            addTodoUseCase(
                title = "QA snackbar timeout",
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
        composeTestRule.onNodeWithText("QA snackbar timeout").assertIsDisplayed()

        composeTestRule.waitUntilNodeExists("todo_quick_tomorrow_$id")
        composeTestRule.onNodeWithTag("todo_quick_tomorrow_$id", useUnmergedTree = true).performClick()

        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(undoText).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
            composeTestRule.onAllNodesWithText(undoText).fetchSemanticsNodes().isEmpty()
        }
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
        composeTestRule.waitUntilNodeDisplayed("add_fab")
        val fabIndex = composeTestRule.displayedNodeIndex("add_fab")
        composeTestRule.onAllNodesWithTag("add_fab", useUnmergedTree = true)[fabIndex].performClick()
        composeTestRule.waitUntilNodeDisplayed("add_manual_action")
        val manualIndex = composeTestRule.displayedNodeIndex("add_manual_action")
        composeTestRule.onAllNodesWithTag("add_manual_action", useUnmergedTree = true)[manualIndex].performClick()
        composeTestRule.waitUntilNodeExists("task_edit_close")
    }

    private fun openAiAddFromFab() {
        composeTestRule.waitUntilNodeDisplayed("add_fab")
        val fabIndex = composeTestRule.displayedNodeIndex("add_fab")
        composeTestRule.onAllNodesWithTag("add_fab", useUnmergedTree = true)[fabIndex].performClick()
        composeTestRule.waitUntilNodeDisplayed("add_ai_action")
        val aiIndex = composeTestRule.displayedNodeIndex("add_ai_action")
        composeTestRule.onAllNodesWithTag("add_ai_action", useUnmergedTree = true)[aiIndex].performClick()
        composeTestRule.waitUntilNodeExists("ai_todo_sheet")
    }

    private fun waitUntilPendingCreateMutation(todoLocalId: Long) =
        runBlocking {
            appDatabase.todoOutboxDao().getPendingMutations("android-test-user")
        }.also { current ->
            if (current.any { it.todoLocalId == todoLocalId && it.type == "CREATE" }) return current
        }.let {
            composeTestRule.waitUntil(timeoutMillis = UiTimeoutMillis) {
                runBlocking {
                    appDatabase.todoOutboxDao()
                        .getPendingMutations("android-test-user")
                        .any { it.todoLocalId == todoLocalId && it.type == "CREATE" }
                }
            }
            runBlocking {
                appDatabase.todoOutboxDao().getPendingMutations("android-test-user")
            }
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

    private fun rowTop(tag: String): Float =
        composeTestRule.onNodeWithTag(tag)
            .getUnclippedBoundsInRoot()
            .top
            .value

    private fun assertRowsInVerticalOrder(vararg tags: String) {
        val tops = tags.map(::rowTop)
        assertTrue(
            "Expected rows to appear in order: ${tags.joinToString()}",
            tops.zipWithNext().all { (upper, lower) -> upper < lower }
        )
    }

    private fun agendaDateLabel(date: LocalDate): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(
            com.neo.yourtodo.feature.calendar.impl.R.string.calendar_agenda_date_label,
            date.format(calendarAgendaDateFormatter(Locale.getDefault()))
        )
    }

    private fun calendarAgendaDateFormatter(locale: Locale): DateTimeFormatter {
        val pattern = if (locale.language == Locale.KOREAN.language) {
            "yyyy년 M월 d일 (E)"
        } else {
            "yyyy MMM d (E)"
        }
        return DateTimeFormatter.ofPattern(pattern, locale)
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

    private fun ComposeTestRule.waitUntilNodeDisplayed(
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            displayedNodeIndexOrNull(tag) != null
        }
    }

    private fun ComposeTestRule.displayedNodeIndex(tag: String): Int =
        displayedNodeIndexOrNull(tag) ?: error("No displayed node found for tag: $tag")

    private fun ComposeTestRule.displayedNodeIndexOrNull(tag: String): Int? {
        val nodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.indices.firstOrNull { index ->
            runCatching {
                onAllNodesWithTag(tag, useUnmergedTree = true)[index].isDisplayed()
            }.getOrDefault(false)
        }
    }

    private fun ComposeTestRule.waitUntilNodeDoesNotExist(
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun ComposeTestRule.waitUntilAgendaTodoDisplayed(
        title: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        val agendaTodo = hasText(title) and hasAnyAncestor(hasTestTag("calendar_day_todo_sheet"))
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodes(agendaTodo, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
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

    private fun ComposeTestRule.waitUntilDisplayedTodoScreenText(
        filterName: String,
        text: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            displayedTodoScreenTextIndex(filterName, text) != null
        }
    }

    private fun ComposeTestRule.onDisplayedTodoScreenText(
        filterName: String,
        text: String
    ) = onAllNodes(
        hasText(text) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
        useUnmergedTree = true
    )[displayedTodoScreenTextIndex(filterName, text)
        ?: error("No displayed todo screen text found for $filterName: $text")]

    private fun ComposeTestRule.displayedTodoScreenTextIndex(
        filterName: String,
        text: String
    ): Int? {
        val matcher = hasText(text) and hasAnyAncestor(hasTestTag("todo_screen_$filterName"))
        val nodes = onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
        return nodes.indices.firstOrNull { index ->
            runCatching {
                onAllNodes(matcher, useUnmergedTree = true)[index].isDisplayed()
            }.getOrDefault(false)
        }
    }

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

    private fun ComposeTestRule.waitUntilDisplayedTodoScreenTag(
        filterName: String,
        tag: String,
        timeoutMillis: Long = UiTimeoutMillis
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            runCatching {
                onTodoScreenTag(filterName, tag).isDisplayed()
            }.getOrDefault(false)
        }
    }

    private fun ComposeTestRule.assertNoTodoScreenTag(
        filterName: String,
        tag: String
    ) {
        onAllNodes(
            hasTestTag(tag) and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    private fun ComposeTestRule.assertTodoScreenRowCount(
        filterName: String,
        expectedCount: Int
    ) {
        val rowMatcher = SemanticsMatcher("Todo row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?.removePrefix("todo_row_")
                ?.toLongOrNull() != null
        }
        onAllNodes(
            rowMatcher and hasAnyAncestor(hasTestTag("todo_screen_$filterName")),
            useUnmergedTree = false
        ).assertCountEquals(expectedCount)
    }

}
