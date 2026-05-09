package com.neo.yourtodo.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun YourTodoBrandHeader(
    wordmarkContentDescription: String,
    profileContentDescription: String,
    modifier: Modifier = Modifier,
    profileInitial: String? = null,
    content: @Composable RowScope.() -> Unit = {}
) {
    val displayInitial = profileInitial.toProfileInitial()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        YourTodoWordmark(
            contentDescription = wordmarkContentDescription,
            modifier = Modifier
                .height(42.dp)
                .width(162.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        content()
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F3A56))
                .semantics { contentDescription = profileContentDescription },
            contentAlignment = Alignment.Center
        ) {
            if (displayInitial == null) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = displayInitial,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

private fun String?.toProfileInitial(): String? =
    this
        ?.trim()
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
