package de.chennemann.opencode.mobile.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Send: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    ImageVector.Builder(
        name = "Send",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(160f, 740f)
            verticalLineToRelative(-520f)
            lineToRelative(616.92f, 260f)
            lineTo(160f, 740f)
            close()
            moveTo(200f, 680f)
            lineTo(674f, 480f)
            lineTo(200f, 280f)
            verticalLineToRelative(155.38f)
            lineTo(393.85f, 480f)
            lineTo(200f, 524.62f)
            lineTo(200f, 680f)
            close()
            moveTo(200f, 680f)
            verticalLineToRelative(-400f)
            verticalLineToRelative(400f)
            close()
        }
    }.build()
}
