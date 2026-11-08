package com.apptiary.streamtracktv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.nayuki.qrcodegen.QrCode

/** Quiet zone the QR specification asks for, in modules. */
private const val QUIET_ZONE = 4

/** Dark-on-white QR code; scanners need that contrast whatever the theme. */
@Composable
fun QrCodeImage(content: String, size: Dp, description: String, modifier: Modifier = Modifier) {
    val qr = remember(content) { QrCode.encodeText(content, QrCode.Ecc.MEDIUM) }
    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        val modules = qr.size + 2 * QUIET_ZONE
        val cell = this.size.minDimension / modules
        drawRect(Color.White)
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.getModule(x, y)) {
                    drawRect(Color.Black, Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), Size(cell, cell))
                }
            }
        }
    }
}
