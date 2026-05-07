package com.neo.yourtodo.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun AppTabIcon(
    tab: AppTabDestination,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (selected) Color(0xFF506381) else Color(0xFF514C59)
    val accentColor = if (selected) Color(0xFF5E7297) else Color(0xFF514C59)
    val fillColor = if (selected) Color(0xFFEFF3FA) else Color.Transparent

    Canvas(modifier = modifier.size(30.dp)) {
        val strokeWidth = 2.2.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        when (tab) {
            AppTabDestination.ALL -> {
                val tile = 8.dp.toPx()
                val gap = 4.dp.toPx()
                val startX = (size.width - tile * 2 - gap) / 2f
                val startY = (size.height - tile * 2 - gap) / 2f
                repeat(2) { row ->
                    repeat(2) { column ->
                        val x = startX + column * (tile + gap)
                        val y = startY + row * (tile + gap)
                        val isHeroTile = selected && row == 0 && column == 0
                        drawRoundRect(
                            color = if (isHeroTile) accentColor.copy(alpha = 0.12f) else fillColor,
                            topLeft = Offset(x, y),
                            size = Size(tile, tile),
                            cornerRadius = CornerRadius(2.6.dp.toPx())
                        )
                        drawRoundRect(
                            color = if (isHeroTile) accentColor else lineColor,
                            topLeft = Offset(x, y),
                            size = Size(tile, tile),
                            cornerRadius = CornerRadius(2.6.dp.toPx()),
                            style = stroke
                        )
                    }
                }
            }

            AppTabDestination.TODAY -> {
                val topLeft = Offset(5.dp.toPx(), 5.dp.toPx())
                val cardSize = Size(20.dp.toPx(), 20.dp.toPx())
                drawRoundRect(
                    color = fillColor,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = CornerRadius(5.dp.toPx())
                )
                drawRoundRect(
                    color = lineColor,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = CornerRadius(5.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = lineColor,
                    start = Offset(8.dp.toPx(), 10.5.dp.toPx()),
                    end = Offset(22.dp.toPx(), 10.5.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor,
                    start = Offset(10.dp.toPx(), 4.dp.toPx()),
                    end = Offset(10.dp.toPx(), 7.8.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accentColor,
                    start = Offset(20.dp.toPx(), 4.dp.toPx()),
                    end = Offset(20.dp.toPx(), 7.8.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = accentColor,
                    radius = if (selected) 4.dp.toPx() else 2.6.dp.toPx(),
                    center = Offset(15.dp.toPx(), 17.dp.toPx())
                )
            }

            AppTabDestination.COMPLETED -> {
                drawCircle(
                    color = fillColor,
                    radius = 10.5.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                drawCircle(
                    color = lineColor,
                    radius = 10.5.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                    style = stroke
                )
                val check = Path().apply {
                    moveTo(9.dp.toPx(), 15.3.dp.toPx())
                    lineTo(13.2.dp.toPx(), 19.2.dp.toPx())
                    lineTo(21.2.dp.toPx(), 10.7.dp.toPx())
                }
                drawPath(
                    path = check,
                    color = accentColor,
                    style = Stroke(
                        width = 2.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            AppTabDestination.CALENDAR -> {
                val topLeft = Offset(4.8.dp.toPx(), 5.dp.toPx())
                val cardSize = Size(20.4.dp.toPx(), 20.dp.toPx())
                drawRoundRect(
                    color = fillColor,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = CornerRadius(4.8.dp.toPx())
                )
                drawRoundRect(
                    color = lineColor,
                    topLeft = topLeft,
                    size = cardSize,
                    cornerRadius = CornerRadius(4.8.dp.toPx()),
                    style = stroke
                )
                drawLine(
                    color = lineColor,
                    start = Offset(8.dp.toPx(), 10.5.dp.toPx()),
                    end = Offset(22.dp.toPx(), 10.5.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                listOf(10.2.dp.toPx(), 15.dp.toPx(), 19.8.dp.toPx()).forEach { x ->
                    drawCircle(
                        color = lineColor.copy(alpha = if (selected) 0.7f else 0.45f),
                        radius = 1.dp.toPx(),
                        center = Offset(x, 15.4.dp.toPx())
                    )
                }
                drawCircle(
                    color = accentColor,
                    radius = 3.2.dp.toPx(),
                    center = Offset(19.8.dp.toPx(), 20.dp.toPx())
                )
            }
        }
    }
}
