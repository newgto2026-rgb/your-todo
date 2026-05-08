package com.neo.yourtodo.feature.friends.api

import androidx.navigation3.runtime.NavKey
import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import kotlinx.serialization.Serializable

@Serializable
data object FriendsRoute : NavKey

interface FriendsFeatureEntry : AppFeatureEntry
