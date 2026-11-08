package com.apptiary.streamtracktv.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.apptiary.streamtracktv.ui.AppInfo
import com.apptiary.streamtracktv.ui.R
import com.apptiary.streamtracktv.ui.components.Caption
import com.apptiary.streamtracktv.ui.components.FocusSurface
import com.apptiary.streamtracktv.ui.components.Highlight
import com.apptiary.streamtracktv.ui.theme.LocalPalette

@Composable
fun AboutScreen(info: AppInfo) {
    val palette = LocalPalette.current
    Column {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.displayMedium, color = palette.ink)
        Spacer(Modifier.height(24.dp))
        FocusSurface(onClick = null, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink,
            )
            Spacer(Modifier.height(12.dp))
            Highlight(stringResource(R.string.about_version, info.versionName, info.versionCode))
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge, color = palette.ink)
            Spacer(Modifier.height(16.dp))
            Caption(stringResource(R.string.about_license))
            Caption(stringResource(R.string.about_fonts))
        }
    }
}
