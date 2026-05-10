package com.neo.yourtodo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neo.yourtodo.app.MainActivity
import com.neo.yourtodo.app.navigation.AppNavigationIdentityProbe
import com.neo.yourtodo.app.push.PushNotificationContract
import com.neo.yourtodo.core.database.dao.AssignedTodoDao
import com.neo.yourtodo.core.database.entity.AssignedTodoEntity
import com.neo.yourtodo.core.database.entity.assignedTodoCacheKey
import com.neo.yourtodo.di.TestFriendRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
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
class PushNotificationLaunchUiTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    @Inject
    lateinit var friendRepository: TestFriendRepository

    @Inject
    lateinit var assignedTodoDao: AssignedTodoDao

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setup() {
        hiltRule.inject()
        friendRepository.reset()
        runBlocking {
            assignedTodoDao.deleteByOwner(OWNER_USER_ID)
            assignedTodoDao.upsertAssignedTodos(listOf(pendingReceivedAssignedTodo()))
        }
        AppNavigationIdentityProbe.start()
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilNodeExists("app_tab_friends")
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) {
            activityScenario.close()
        }
        AppNavigationIdentityProbe.stop()
    }

    @Test
    fun foregroundPushClick_opensIncomingAssignmentDecisionDialog() {
        sendForegroundAssignmentBundlePushClick()

        composeTestRule.waitUntilNodeExists("app_tab_friends")
        composeTestRule.waitUntilNodeExists("friends_assignment_monitor_dialog")
        composeTestRule.waitUntilNodeExists("friends_assignment_pending_$ASSIGNED_TODO_ID")
        composeTestRule.onNodeWithTag("friends_assignment_pending_$ASSIGNED_TODO_ID")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_assignment_pending_check_$ASSIGNED_TODO_ID")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("friends_assignment_accept_selected")
            .assertIsDisplayed()
        assertFriendsRouteIdentityRetained()
    }

    private fun sendForegroundAssignmentBundlePushClick() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = assignmentBundlePushClickIntent(context)
        activityScenario.onActivity { activity ->
            activity.handleNavigationIntent(intent)
        }
    }

    private fun assignmentBundlePushClickIntent(context: Context): Intent {
        val deepLink = "yourtodo://assignment-bundles/received/$BUNDLE_ID"
        return Intent(context, MainActivity::class.java).apply {
            action = PushNotificationContract.ACTION_OPEN_PUSH_NOTIFICATION
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = deepLink.toUri()
            putExtra(PushNotificationContract.EXTRA_TYPE, "ASSIGNMENT_BUNDLE_RECEIVED")
            putExtra(PushNotificationContract.EXTRA_DEEP_LINK, deepLink)
            putExtra(PushNotificationContract.EXTRA_NOTIFICATION_EVENT_ID, "event-$BUNDLE_ID")
            putExtra(PushNotificationContract.EXTRA_BUNDLE_ID, BUNDLE_ID)
            putExtra(PushNotificationContract.EXTRA_ACTOR_USER_ID, FRIEND_USER_ID)
            putExtra(PushNotificationContract.EXTRA_ACTOR_NICKNAME, FRIEND_NICKNAME)
        }
    }

    private fun pendingReceivedAssignedTodo(): AssignedTodoEntity =
        AssignedTodoEntity(
            ownerUserId = OWNER_USER_ID,
            id = ASSIGNED_TODO_ID,
            cacheKey = assignedTodoCacheKey(OWNER_USER_ID, ASSIGNED_TODO_ID),
            bundleId = BUNDLE_ID,
            title = "Push shared task",
            description = null,
            dueDateEpochDay = null,
            dueTimeMinutes = null,
            priority = "MEDIUM",
            category = null,
            status = "PENDING_ACCEPTANCE",
            terminalReason = null,
            progressPercent = 0,
            senderUserId = FRIEND_USER_ID,
            senderNickname = FRIEND_NICKNAME,
            receiverUserId = OWNER_USER_ID,
            receiverNickname = "tester",
            reminderAt = null,
            reminderEnabled = null,
            createdAtEpochMillis = 1_778_320_800_000L,
            completedAtEpochMillis = null,
            receivedCached = true,
            receivedTaskHidden = false,
            sentCached = false,
            cacheUpdatedAt = 1_778_320_800_000L
        )

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

    private fun assertFriendsRouteIdentityRetained() {
        composeTestRule.waitUntil(5_000L) {
            friendsRouteEntryHashes().isNotEmpty() && friendsRouteViewModelHashSets().isNotEmpty()
        }

        val entryHashes = friendsRouteEntryHashes().distinct()
        assertEquals(
            "Foreground push must keep one NavEntry instance for FriendsRoute: $entryHashes",
            1,
            entryHashes.size
        )

        val viewModelHashSets = friendsRouteViewModelHashSets().distinct()
        assertEquals(
            "Foreground push must keep one ViewModel identity set for FriendsRoute: $viewModelHashSets",
            1,
            viewModelHashSets.size
        )
        assertTrue(
            "FriendsRoute did not create an entry-scoped ViewModel",
            viewModelHashSets.single().isNotEmpty()
        )
        assertTrue(
            "Incoming assignment push must not create a second Friends screen entry",
            incomingEntryHashes().isEmpty()
        )
    }

    private fun friendsRouteEntryHashes(): List<Int> =
        AppNavigationIdentityProbe.snapshotEntryEvents()
            .filter { event -> event.contentKey == "FriendsRoute" }
            .map { event -> event.identityHash }

    private fun friendsRouteViewModelHashSets(): List<List<Int>> =
        AppNavigationIdentityProbe.snapshotViewModelEvents()
            .filter { event -> event.contentKey == "FriendsRoute" }
            .map { event -> event.viewModelIdentityHashes }

    private fun incomingEntryHashes(): List<Int> =
        AppNavigationIdentityProbe.snapshotEntryEvents()
            .filter { event -> event.contentKey.isIncomingAssignmentRouteKey() }
            .map { event -> event.identityHash }

    private fun String.isIncomingAssignmentRouteKey(): Boolean =
        contains("FriendsIncomingAssignmentRoute(") && contains("bundleId=$BUNDLE_ID")

    private companion object {
        const val OWNER_USER_ID = "android-test-user"
        const val FRIEND_USER_ID = "friend-1"
        const val FRIEND_NICKNAME = "monday"
        const val BUNDLE_ID = "bundle-push-1"
        const val ASSIGNED_TODO_ID = "assigned-push-1"
    }
}
