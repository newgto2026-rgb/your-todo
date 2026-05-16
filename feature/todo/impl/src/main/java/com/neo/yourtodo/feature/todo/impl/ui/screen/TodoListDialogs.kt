package com.neo.yourtodo.feature.todo.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neo.yourtodo.feature.todo.impl.R

@Composable
internal fun LockedAiBottomSheetDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ai_todo_dialog")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .testTag("ai_todo_scrim")
                    .pointerInput(onDismissRequest) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startPosition = down.position
                            var isTap = true

                            do {
                                val event = awaitPointerEvent()
                                val pointer = event.changes.firstOrNull { it.id == down.id }
                                if (pointer != null) {
                                    if ((pointer.position - startPosition).getDistance() > viewConfiguration.touchSlop) {
                                        isTap = false
                                    }
                                    if (pointer.changedToUpIgnoreConsumed()) {
                                        if (isTap) onDismissRequest()
                                        break
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .testTag("ai_todo_sheet_container")
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    confirmation: TodoDeleteConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val titleRes = when (confirmation) {
        is TodoDeleteConfirmation.Single -> R.string.todo_delete_confirm_title
        is TodoDeleteConfirmation.Completed -> R.string.todo_clear_completed_confirm_title
    }
    val message = when (confirmation) {
        is TodoDeleteConfirmation.Single -> stringResource(R.string.todo_delete_confirm_message)
        is TodoDeleteConfirmation.Completed -> pluralStringResource(
            R.plurals.todo_clear_completed_confirm_message,
            confirmation.itemCount,
            confirmation.itemCount
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_confirmation_dialog"),
        title = { Text(stringResource(titleRes)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("confirm_delete_button")
            ) {
                Text(stringResource(R.string.todo_editor_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.todo_editor_cancel))
            }
        }
    )
}
