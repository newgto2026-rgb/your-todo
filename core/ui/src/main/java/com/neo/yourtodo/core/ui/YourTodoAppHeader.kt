package com.neo.yourtodo.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun YourTodoAppHeader(
    wordmarkContentDescription: String,
    profileContentDescription: String,
    syncContentDescription: String,
    profileInitial: String?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier,
    syncTestTag: String? = null,
    content: @Composable RowScope.() -> Unit = {}
) {
    YourTodoBrandHeader(
        wordmarkContentDescription = wordmarkContentDescription,
        profileContentDescription = profileContentDescription,
        profileInitial = profileInitial,
        modifier = modifier,
        content = {
            content()
            YourTodoSyncActionButton(
                isSyncing = isSyncing,
                contentDescription = syncContentDescription,
                onClick = onSyncClick,
                testTag = syncTestTag
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
    )
}
