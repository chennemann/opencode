package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Add: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Add",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(440f, 760f)
            lineToRelative(80f, 0f)
            lineToRelative(0f, -240f)
            lineToRelative(240f, 0f)
            lineToRelative(0f, -80f)
            lineToRelative(-240f, 0f)
            lineToRelative(0f, -240f)
            lineToRelative(-80f, 0f)
            lineToRelative(0f, 240f)
            lineToRelative(-240f, 0f)
            lineToRelative(0f, 80f)
            lineToRelative(240f, 0f)
            close()
        }
    }.build()
}
