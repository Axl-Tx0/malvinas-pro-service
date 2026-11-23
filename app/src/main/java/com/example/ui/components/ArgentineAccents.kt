package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ArgCelesteBand
import com.example.ui.theme.ArgSolGold
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AlbicelesteRibbonStripe(
  modifier: Modifier = Modifier,
  height: Dp = 5.dp,
  showSunBadge: Boolean = true,
) {
  Canvas(modifier = modifier.fillMaxWidth().height(height)) {
    val bandHeight = size.height / 3f
    drawRect(
      color = ArgCelesteBand,
      topLeft = Offset.Zero,
      size = Size(size.width, bandHeight),
    )
    drawRect(
      color = Color.White,
      topLeft = Offset(0f, bandHeight),
      size = Size(size.width, bandHeight),
    )
    drawRect(
      color = ArgCelesteBand,
      topLeft = Offset(0f, bandHeight * 2f),
      size = Size(size.width, size.height - bandHeight * 2f),
    )

    if (showSunBadge && size.height >= 4f) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = (size.height * 0.24f).coerceAtLeast(1.6f)
      for (i in 0 until 8) {
        val angle = (i * Math.PI / 4.0)
        val inner = Offset(
          x = center.x + (radius * 1.1f * cos(angle)).toFloat(),
          y = center.y + (radius * 1.1f * sin(angle)).toFloat(),
        )
        val outer = Offset(
          x = center.x + (radius * 1.85f * cos(angle)).toFloat(),
          y = center.y + (radius * 1.85f * sin(angle)).toFloat(),
        )
        drawLine(
          color = ArgSolGold,
          start = inner,
          end = outer,
          strokeWidth = (radius * 0.45f).coerceAtLeast(1f),
        )
      }
      drawCircle(
        color = ArgSolGold,
        radius = radius,
        center = center,
      )
    }
  }
}
