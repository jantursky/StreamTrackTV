package com.apptiary.streamtracktv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apptiary.streamtracktv.collector.PackageManagerAppCatalog
import com.apptiary.streamtracktv.ui.theme.LocalPalette
import com.apptiary.streamtracktv.ui.theme.RoundedIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PX = 160
private const val BANNER_PX_WIDTH = 320
private const val BANNER_PX_HEIGHT = 180

/** App banner or icon from the package manager; a monogram tile when the app is not installed. */
@Composable
fun AppIcon(packageName: String, label: String, catalog: PackageManagerAppCatalog, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value =
            withContext(Dispatchers.IO) {
                catalog.icon(packageName)?.let { drawable ->
                    val banner = drawable.intrinsicWidth > drawable.intrinsicHeight
                    val width = if (banner) BANNER_PX_WIDTH else ICON_PX
                    val height = if (banner) BANNER_PX_HEIGHT else ICON_PX
                    drawable.toBitmap(width, height).asImageBitmap()
                }
            }
    }
    val shaped = modifier.size(width = 80.dp, height = 45.dp).clip(RoundedIcon)
    val image = bitmap
    if (image != null) {
        Image(image, contentDescription = null, modifier = shaped, contentScale = ContentScale.Fit)
    } else {
        val palette = LocalPalette.current
        Box(shaped.background(palette.border), contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = palette.ink)
        }
    }
}
