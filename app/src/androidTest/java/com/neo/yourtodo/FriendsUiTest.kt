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
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.yourtodo.app.MainActivity
import com.neo.yourtodo.di.TestFriendRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
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

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()
        friendRepository.reset()
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
}
