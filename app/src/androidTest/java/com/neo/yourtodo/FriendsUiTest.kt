package com.neo.yourtodo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.yourtodo.app.MainActivity
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.dao.PersonVisibilityDao
import com.neo.yourtodo.core.database.entity.ObservedTodoEntity
import com.neo.yourtodo.di.TestFriendRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FriendsUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var friendRepository: TestFriendRepository

    @Inject
    lateinit var assignedTodoDao: AssignedTodoDao

    @Inject
    lateinit var personVisibilityDao: PersonVisibilityDao

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()
        friendRepository.reset()
        runBlocking {
            assignedTodoDao.deleteByOwner(OwnerUserId)
            personVisibilityDao.purgeObservedTodosByCurrentUser(OwnerUserId)
        }
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists("app_tab_friends")
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
    }

    @Test
    fun friendsTabShowsRequestsFriendsAndOutgoingState() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()

        composeTestRule.waitUntilNodeExists("friends_screen")
        composeTestRule.onNodeWithContentDescription("Your Todo").assertIsDisplayed()
        composeTestRule.onNodeWithText("neo").assertIsDisplayed()
        composeTestRule.onNodeWithText("monday").assertIsDisplayed()
        composeTestRule.onNodeWithText("summer").assertIsDisplayed()
    }

    @Test
    fun friendsTabCanSendAndAcceptRequests() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.onNodeWithTag("friends_add_toggle").performClick()
        composeTestRule.onNodeWithTag("friends_nickname_input").performTextInput("river")
        composeTestRule.onNodeWithTag("friends_send_request").performClick()
        composeTestRule.waitUntilNodeExists("friends_outgoing_outgoing-added")
        composeTestRule.onNodeWithText("river").assertIsDisplayed()

        composeTestRule.onNodeWithTag("friends_accept_incoming-1").performClick()
        composeTestRule.waitUntilNodeExists("friends_friend_friend-2")
        composeTestRule.onNodeWithText("neo").assertIsDisplayed()
    }

    @Test
    fun friendsTabCanCloseAddFriendCard() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.onNodeWithTag("friends_add_toggle").performClick()
        composeTestRule.waitUntilNodeExists("friends_nickname_input")
        composeTestRule.onNodeWithTag("friends_add_close").performClick()

        composeTestRule.waitUntilNodeGone("friends_nickname_input")
    }

    @Test
    fun friendsTabOpensSharedTodoEditorLikeTodoEditor() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.onNodeWithTag("friends_auto_accept_friend-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_send_todo_friend-1").performClick()

        composeTestRule.waitUntilNodeExists("friends_assignment_editor_sheet")
        composeTestRule.onNodeWithTag("friends_assignment_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_assignment_due_date").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_assignment_due_time").assertIsDisplayed()
        composeTestRule.onAllNodes(hasTestTag("friends_assignment_mode_request"))
            .assertCountEquals(0)
        composeTestRule.onAllNodes(hasTestTag("friends_assignment_mode_direct"))
            .assertCountEquals(0)
        composeTestRule.onAllNodes(hasTestTag("friends_assignment_description"))
            .assertCountEquals(0)
    }

    @Test
    fun pendingFriendRequestRowShowsOnlyAcceptAndDeclineActions() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.onNodeWithTag("friends_incoming_incoming-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_decline_incoming-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_accept_incoming-1").assertIsDisplayed()
        composeTestRule.assertNodeDoesNotExist("friends_send_todo_friend-2")
        composeTestRule.assertNodeDoesNotExist("friends_auto_accept_friend-2")
        composeTestRule.assertNodeDoesNotExist("friends_show_my_todos_friend-2")
    }

    @Test
    fun activeFriendRowShowsSendAutoAcceptAndMyTodoVisibilityActions() {
        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.onNodeWithTag("friends_friend_friend-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_send_todo_friend-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_auto_accept_friend-1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_show_my_todos_friend-1").assertIsDisplayed()
    }

    @Test
    fun friendObservedTodosExpandInsideFriendRowWithoutGlobalSectionOrLimit() {
        runBlocking {
            personVisibilityDao.upsertObservedTodos((1..5).map { index ->
                observedTodo(id = "observed-$index", title = "Observed todo $index")
            })
        }

        composeTestRule.onNodeWithTag("app_tab_friends").performClick()
        composeTestRule.waitUntilNodeExists("friends_screen")

        composeTestRule.assertNodeDoesNotExist("friends_global_observed_todos_section")
        composeTestRule.onNodeWithTag("friends_observed_todos_toggle_friend-1").performClick()
        composeTestRule.waitUntilNodeExists("friends_friend_observed_todos_friend-1")

        (1..5).forEach { index ->
            composeTestRule.onNodeWithTag("friends_observed_todo_observed-$index")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Observed todo $index").assertIsDisplayed()
        }
        composeTestRule.assertNodeDoesNotExist("friends_global_observed_todos_section")
    }

    private fun ComposeTestRule.waitUntilNodeExists(
        testTag: String,
        timeoutMillis: Long = 15_000L
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(testTag))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    private fun ComposeTestRule.waitUntilNodeGone(
        testTag: String,
        timeoutMillis: Long = 15_000L
    ) {
        waitUntil(timeoutMillis) {
            onAllNodes(hasTestTag(testTag))
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
    }

    private fun ComposeTestRule.assertNodeDoesNotExist(testTag: String) {
        onAllNodes(hasTestTag(testTag))
            .assertCountEquals(0)
    }

    private fun observedTodo(
        id: String,
        title: String
    ): ObservedTodoEntity =
        ObservedTodoEntity(
            currentUserId = OwnerUserId,
            observedTodoId = id,
            sourceTodoId = "source-$id",
            grantId = "grant-friends",
            ownerUserId = ActiveFriendUserId,
            ownerNickname = "monday",
            ownerAvatarUrl = null,
            title = title,
            dueDateEpochDay = null,
            dueTimeMinutes = null,
            isDone = false,
            recurrenceOccurrenceId = null,
            projectionVersion = 1,
            updatedAtEpochMillis = 1_778_320_800_000L,
            cacheUpdatedAtEpochMillis = 1_778_320_800_000L
        )

    private companion object {
        const val OwnerUserId = "android-test-user"
        const val ActiveFriendUserId = "friend-1"
    }
}
