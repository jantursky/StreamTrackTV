package com.jantursky.streamtracktv.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.jantursky.streamtracktv.ui.R

private fun variable(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Inter for body and UI text. */
val Inter =
    FontFamily(
        variable(R.font.inter, FontWeight.Normal),
        variable(R.font.inter, FontWeight.Medium),
        variable(R.font.inter, FontWeight.SemiBold),
    )

/** Inter Tight for display and headings. */
val InterTight =
    FontFamily(
        variable(R.font.inter_tight, FontWeight.Normal),
        variable(R.font.inter_tight, FontWeight.Medium),
    )

// TV scale: base text 24 sp, nothing under 18 sp.
val StreamTrackTypography =
    Typography(
        displayLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 72.sp, letterSpacing = (-0.021).em),
        displayMedium = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 52.sp, letterSpacing = (-0.021).em),
        headlineMedium = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 36.sp, letterSpacing = (-0.025).em),
        headlineSmall = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 30.sp, letterSpacing = (-0.02).em),
        titleLarge = TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Medium, fontSize = 28.sp),
        titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 24.sp),
        bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 34.sp),
        bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
        bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp),
        labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    )
