package com.neo.yourtodo.feature.friends.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.ui.navigation.AppRouteActions
import com.neo.yourtodo.feature.friends.api.FriendsFeatureEntry
import com.neo.yourtodo.feature.friends.api.FriendsRoute
import com.neo.yourtodo.feature.friends.impl.ui.FriendsRouteScreen
import javax.inject.Inject

class FriendsFeatureEntryImpl @Inject constructor() : FriendsFeatureEntry {
    override val route: NavKey = FriendsRoute

    override fun register(
        entryProviderScope: EntryProviderScope<NavKey>,
        routeActions: AppRouteActions
    ) {
        entryProviderScope.entry<FriendsRoute> {
            FriendsRouteScreen()
        }
    }
}
