package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.ChevronUp: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "ChevronUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveToRelative(480f, 404.31f)
            lineToRelative(-184f, 184f)
            lineTo(267.69f, 560f)
            lineTo(480f, 347.69f)
            lineTo(692.31f, 560f)
            lineTo(664f, 588.31f)
            lineToRelative(-184f, -184f)
            close()
        }
    }.build()
}
