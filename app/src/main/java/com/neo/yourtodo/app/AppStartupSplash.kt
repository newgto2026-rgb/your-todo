package com.neo.yourtodo.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.neo.yourtodo.R
import com.neo.yourtodo.core.ui.YourTodoScreenBackground
import com.neo.yourtodo.core.ui.YourTodoWordmark
import kotlinx.coroutines.delay

@Composable
fun AppStartupSplash(
    onFinished: () -> Unit
) {
    var started by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        started = true
        delay(STARTUP_SPLASH_VISIBLE_MS)
        exiting = true
        delay(STARTUP_SPLASH_EXIT_MS)
        onFinished()
    }

    val screenAlpha by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(
            durationMillis = STARTUP_SPLASH_EXIT_MS.toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "startupScreenAlpha"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 340,
            easing = FastOutSlowInEasing
        ),
        label = "startupLogoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.92f,
        animationSpec = tween(
            durationMillis = 520,
            easing = FastOutSlowInEasing
        ),
        label = "startupLogoScale"
    )
    val logoOffset by animateDpAsState(
        targetValue = if (started) 0.dp else 18.dp,
        animationSpec = tween(
            durationMillis = 520,
            easing = FastOutSlowInEasing
        ),
        label = "startupLogoOffset"
    )
    val ambientMotion = rememberInfiniteTransition(label = "startupAmbientMotion")
    val logoPulse by ambientMotion.animateFloat(
        initialValue = 0.992f,
        targetValue = 1.012f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 860,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "startupLogoPulse"
    )
    val shineProgress by ambientMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1180,
                delayMillis = 160,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "startupShineProgress"
    )
    val accentWidth by animateDpAsState(
        targetValue = if (started) 88.dp else 0.dp,
        animationSpec = tween(
            durationMillis = 600,
            delayMillis = 320,
            easing = FastOutSlowInEasing
        ),
        label = "startupAccentWidth"
    )
    val accentAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = 420,
            delayMillis = 260,
            easing = FastOutSlowInEasing
        ),
        label = "startupAccentAlpha"
    )

    YourTodoScreenBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = screenAlpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.84f),
                            Color.Transparent
                        ),
                        radius = 720f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(178.dp)
                        .height(46.dp)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = logoOffset.roundToPx()
                            )
                        }
                        .graphicsLayer {
                            alpha = logoAlpha
                            scaleX = logoScale * logoPulse
                            scaleY = logoScale * logoPulse
                        }
                        .clipToBounds()
                ) {
                    YourTodoWordmark(
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (-64 + 228 * shineProgress).dp.roundToPx(),
                                    y = 0
                                )
                            }
                            .width(44.dp)
                            .height(58.dp)
                            .graphicsLayer {
                                alpha = accentAlpha * 0.34f
                                rotationZ = -14f
                            }
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(accentWidth)
                        .graphicsLayer { alpha = accentAlpha }
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f))
                )
            }
        }
    }
}

private const val STARTUP_SPLASH_VISIBLE_MS = 1900L
private const val STARTUP_SPLASH_EXIT_MS = 220L
