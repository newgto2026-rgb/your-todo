package com.neo.yourtodo.app

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
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

    private fun navEntry(route: NavKey): NavEntry<NavKey> =
        NavEntry(key = route) {}

    private data object FirstRoute : NavKey
    private data object SecondRoute : NavKey
}
