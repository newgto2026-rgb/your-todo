package com.neo.yourtodo.app

import androidx.compose.material3.SnackbarHostState
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AppNavHostHelperTest {

    @Test
    fun activeContentKeyFor_returnsMatchingEntryContentKey() {
        val entries = listOf(navEntry(FirstRoute), navEntry(SecondRoute))

        val activeContentKey = entries.activeContentKeyFor(SecondRoute)

        assertThat(activeContentKey).isEqualTo(SecondRoute.toString())
    }

    @Test
    fun activeContentKeyFor_fallsBackToActiveRouteWhenEntryIsMissing() {
        val entries = listOf(navEntry(FirstRoute))

        val activeContentKey = entries.activeContentKeyFor(SecondRoute)

        assertThat(activeContentKey).isEqualTo(SecondRoute)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun requestExitConfirmation_resetsPendingWhenSnackbarJobIsCancelled() = runTest {
        val pendingTransitions = mutableListOf<Boolean>()

        val job = requestExitConfirmation(
            snackbarHostState = SnackbarHostState(),
            coroutineScope = this,
            message = "Exit?",
            setPending = pendingTransitions::add
        )
        runCurrent()

        job.cancelAndJoin()

        assertThat(pendingTransitions).containsExactly(true, false).inOrder()
    }

    private fun navEntry(route: NavKey): NavEntry<NavKey> =
        NavEntry(key = route) {}

    private data object FirstRoute : NavKey
    private data object SecondRoute : NavKey
}
