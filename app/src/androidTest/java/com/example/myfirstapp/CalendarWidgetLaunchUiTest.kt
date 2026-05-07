package com.example.myfirstapp

import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myfirstapp.app.MainActivity
import com.example.myfirstapp.core.database.AppDatabase
import com.example.myfirstapp.core.datastore.source.UserPreferencesDataSource
import com.example.myfirstapp.core.model.TodoFilter
import com.example.myfirstapp.core.model.TodoPriorityFilter
import com.example.myfirstapp.feature.calendar.api.CalendarWidgetIntentContract
import com.example.myfirstapp.feature.calendar.impl.R as CalendarImplR
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalendarWidgetLaunchUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

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
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun widgetDateIntent_opensCalendarTabWithSelectedDate() {
        val selectedDate = LocalDate.of(2026, 7, 11)

        activityScenario = ActivityScenario.launch(widgetDateIntent(selectedDate))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_tab_calendar", useUnmergedTree = true)
            .assertIsSelected()
        composeTestRule.onNodeWithTag("calendar_month_label")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("calendar_day_$selectedDate")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(selectedDate.agendaLabel())
            .assertIsDisplayed()

        val selectedDescription = composeTestRule
            .onNodeWithTag("calendar_day_$selectedDate")
            .fetchSemanticsNode()
            .config
            .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            .joinToString(separator = ", ")

        val selectedCopy = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(CalendarImplR.string.calendar_a11y_selected)
        assertTrue(
            "Widget launch date should be marked selected. Description was: $selectedDescription",
            selectedDescription.contains(selectedCopy)
        )
    }

    private fun widgetDateIntent(date: LocalDate): Intent {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Intent(context, MainActivity::class.java).apply {
            action = CalendarWidgetIntentContract.ACTION_OPEN_CALENDAR_DATE
            putExtra(CalendarWidgetIntentContract.EXTRA_SELECTED_DATE, date.toString())
        }
    }

    private fun LocalDate.agendaLabel(): String =
        format(DateTimeFormatter.ofPattern("yyyy MMM d", Locale.getDefault()))
}
