package com.neo.yourtodo.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun YourTodoWordmark(
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(R.drawable.todo_wordmark),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
