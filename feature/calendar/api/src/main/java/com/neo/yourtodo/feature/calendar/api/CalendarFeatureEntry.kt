package com.neo.yourtodo.feature.calendar.api

import com.neo.yourtodo.core.ui.navigation.AppFeatureEntry
import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
data object CalendarRoute : NavKey

interface CalendarFeatureEntry : AppFeatureEntry
