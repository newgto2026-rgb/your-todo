package com.neo.yourtodo.feature.friends.api

import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import kotlinx.serialization.Serializable

@Serializable
data object FriendsRoute : NavKey

@Serializable
data class FriendsIncomingAssignmentRoute(
    val friendUserId: String? = null,
    val bundleId: String? = null
) : NavKey

interface FriendsFeatureEntry : AppFeatureEntry
