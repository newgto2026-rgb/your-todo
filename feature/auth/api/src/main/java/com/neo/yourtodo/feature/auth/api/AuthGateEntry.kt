package com.neo.yourtodo.feature.auth.api

import androidx.compose.runtime.Composable

interface AuthGateEntry {
    @Composable
    fun Content(content: @Composable () -> Unit)
}
