package com.neo.yourtodo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.yourtodo.app.MainActivity
import com.neo.yourtodo.core.database.AppDatabase
import com.neo.yourtodo.core.datastore.source.UserPreferencesDataSource
import com.neo.yourtodo.core.domain.usecase.AddTodoUseCase
import com.neo.yourtodo.core.model.ReminderRepeatType
import com.neo.yourtodo.core.model.TodoFilter
import com.neo.yourtodo.core.model.TodoPriorityFilter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarUiTest {

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

    private var todayTodoId: Long = -1L
    private val today: LocalDate = LocalDate.now()
    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()

        runBlocking {
            appDatabase.clearAllTables()
            userPreferencesDataSource.setSelectedTodoFilter(TodoFilter.ALL)
            userPreferencesDataSource.setSelectedTodoCategoryFilter(null)
            userPreferencesDataSource.setSelectedTodoPriorityFilter(TodoPriorityFilter.ALL)

            todayTodoId = addTodoUseCase(
                title = "Calendar UI - today",
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()

            addTodoUseCase(
                title = "Calendar UI - today second",
                dueDate = today,
                categoryId = null,
                reminderAtEpochMillis = null,
                isReminderEnabled = false,
                reminderRepeatType = ReminderRepeatType.NONE,
                reminderRepeatDaysMask = 0
            ).getOrThrow()
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
    fun calendarMonthNavigation_changesMonthLabel() {
        openCalendarTab()
        val before = monthLabelText()

        composeTestRule.onNodeWithTag("calendar_next_month").performClick()
        composeTestRule.waitForIdle()
        val afterNext = monthLabelText()

        composeTestRule.onNodeWithTag("calendar_prev_month").performClick()
        composeTestRule.waitForIdle()
        val afterBack = monthLabelText()

        assertNotEquals(before, afterNext)
        assertEquals(before, afterBack)
    }

    @Test
    fun dateTap_withTodos_showsBottomSheetList() {
        openCalendarTab()

        composeTestRule.onNodeWithTag("calendar_day_$today").performClick()

        composeTestRule.onNodeWithTag("calendar_day_todo_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calendar UI - today").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calendar UI - today second").assertIsDisplayed()
    }

    @Test
    fun dateTap_withoutTodos_showsEmptyState() {
        openCalendarTab()

        val emptyDate = findEmptyDateInCurrentMonth()
        composeTestRule.onNodeWithTag("calendar_day_$emptyDate").performClick()

        composeTestRule.onNodeWithTag("calendar_day_todo_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_todo_list_empty").assertIsDisplayed()
    }

    @Test
    fun todoClickInBottomSheet_opensTodoEditSheet() {
        openCalendarTab()

        composeTestRule.onNodeWithTag("calendar_day_$today").performClick()
        composeTestRule.waitUntilNodeExists("calendar_day_todo_item_$todayTodoId")
        composeTestRule.onNodeWithTag("calendar_day_todo_item_$todayTodoId").performClick()

        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithTag("task_title_input").assertIsDisplayed().assertTextContains("Calendar UI - today")
    }

    @Test
    fun todoEditSheetDismiss_returnsToCalendarContext() {
        openCalendarTab()

        composeTestRule.onNodeWithTag("calendar_day_$today").performClick()
        composeTestRule.waitUntilNodeExists("calendar_day_todo_item_$todayTodoId")
        composeTestRule.onNodeWithTag("calendar_day_todo_item_$todayTodoId").performClick()
        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithTag("task_title_input").assertIsDisplayed()

        composeTestRule.onNodeWithTag("task_edit_close").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_tab_calendar", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_todo_sheet").assertIsDisplayed()
    }

    @Test
    fun addTaskForSelectedDate_prefillsDateAndReturnsToCalendarAgenda() {
        val targetDate = findEmptyDateInCurrentMonth()
        val title = "Calendar add ${System.currentTimeMillis()}"

        openCalendarTab()
        composeTestRule.onNodeWithTag("calendar_day_$targetDate").performClick()
        composeTestRule.onNodeWithTag("calendar_add_todo_for_date").performClick()

        composeTestRule.waitUntilNodeExists("task_title_input")
        composeTestRule.onNodeWithText(targetDate.toString()).assertIsDisplayed()
        composeTestRule.onNodeWithTag("task_title_input").performTextInput(title)
        composeTestRule.onNodeWithTag("save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    private fun openCalendarTab() {
        composeTestRule.onNodeWithTag("app_tab_calendar", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithTag("calendar_month_label").assertIsDisplayed()
    }

    private fun monthLabelText(): String {
        val semanticsNode = composeTestRule.onNodeWithTag("calendar_month_label").fetchSemanticsNode()
        val descriptionList = semanticsNode.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
        return descriptionList.joinToString(separator = " ").trim()
    }

    private fun findEmptyDateInCurrentMonth(): LocalDate {
        val dayRange = 1..today.lengthOfMonth()
        return dayRange
            .asSequence()
            .map { today.withDayOfMonth(it) }
            .first { it != today }
    }

    private fun ComposeTestRule.waitUntilNodeExists(
        tag: String,
        timeoutMillis: Long = 5_000
    ) {
        waitUntil(timeoutMillis = timeoutMillis) {
            onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
