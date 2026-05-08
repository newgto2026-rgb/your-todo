package com.neo.yourtodo.feature.auth.impl

import androidx.compose.runtime.Composable
import com.neo.yourtodo.feature.auth.api.AuthGateEntry
import com.neo.yourtodo.feature.auth.impl.ui.screen.AuthGate
import javax.inject.Inject

class AuthGateEntryImpl @Inject constructor() : AuthGateEntry {

    @Composable
    override fun Content(content: @Composable () -> Unit) {
        AuthGate(content = content)
    }
}
