package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.FilterList: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "FilterList",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(400f, 720f)
            verticalLineToRelative(-80f)
            horizontalLineToRelative(160f)
            verticalLineToRelative(80f)
            lineTo(400f, 720f)
            close()
            moveTo(240f, 520f)
            verticalLineToRelative(-80f)
            horizontalLineToRelative(480f)
            verticalLineToRelative(80f)
            lineTo(240f, 520f)
            close()
            moveTo(120f, 320f)
            verticalLineToRelative(-80f)
            horizontalLineToRelative(720f)
            verticalLineToRelative(80f)
            lineTo(120f, 320f)
            close()
        }
    }.build()
}
