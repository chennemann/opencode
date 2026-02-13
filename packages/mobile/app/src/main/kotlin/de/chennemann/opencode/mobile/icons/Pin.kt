package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Pin: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Pin",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveToRelative(640f, 480f)
            lineToRelative(80f, 80f)
            verticalLineToRelative(80f)
            lineTo(520f, 640f)
            verticalLineToRelative(240f)
            lineToRelative(-40f, 40f)
            lineToRelative(-40f, -40f)
            verticalLineToRelative(-240f)
            lineTo(240f, 640f)
            verticalLineToRelative(-80f)
            lineToRelative(80f, -80f)
            verticalLineToRelative(-280f)
            horizontalLineToRelative(-40f)
            verticalLineToRelative(-80f)
            horizontalLineToRelative(400f)
            verticalLineToRelative(80f)
            horizontalLineToRelative(-40f)
            verticalLineToRelative(280f)
            close()
        }
    }.build()
}
