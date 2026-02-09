package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.ChevronDown: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "ChevronDown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(480f, 588.31f)
            lineTo(267.69f, 376f)
            lineTo(296f, 347.69f)
            lineToRelative(184f, 184f)
            lineToRelative(184f, -184f)
            lineTo(692.31f, 376f)
            lineTo(480f, 588.31f)
            close()
        }
    }.build()
}
