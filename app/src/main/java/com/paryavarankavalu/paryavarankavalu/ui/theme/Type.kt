package com.paryavarankavalu.paryavarankavalu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.paryavarankavalu.paryavarankavalu.R

// ─── Plus Jakarta Sans via Google Fonts ─────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage  = "com.google.android.gms",
    certificates     = R.array.com_google_android_gms_fonts_certs
)

private val PlusJakartaSans = GoogleFont("Plus Jakarta Sans")

val PlusJakartaSansFamily = FontFamily(
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Black),
)

// ─── Stitch Typography Scale ─────────────────────────────────────────────────
val Typography = Typography(
    // display-lg: 36sp / 800 / -0.02em
    displayLarge = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.ExtraBold,
        fontSize     = 36.sp,
        lineHeight   = 43.sp,
        letterSpacing= (-0.72).sp
    ),
    // headline-md: 24sp / 700
    headlineMedium = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 24.sp,
        lineHeight   = 32.sp,
        letterSpacing= 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Bold,
        fontSize     = 20.sp,
        lineHeight   = 26.sp,
        letterSpacing= 0.sp
    ),
    // subtitle-md: 16sp / 500
    titleMedium = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Medium,
        fontSize     = 16.sp,
        lineHeight   = 24.sp,
        letterSpacing= 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 14.sp,
        lineHeight   = 20.sp,
        letterSpacing= 0.sp
    ),
    // body-md: 16sp / 400 / 1.6 line-height
    bodyLarge = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 16.sp,
        lineHeight   = 25.6.sp,
        letterSpacing= 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 14.sp,
        lineHeight   = 22.sp,
        letterSpacing= 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.Normal,
        fontSize     = 12.sp,
        lineHeight   = 18.sp,
        letterSpacing= 0.sp
    ),
    // label-sm: 12sp / 600 / +0.05em
    labelSmall = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 12.sp,
        lineHeight   = 12.sp,
        letterSpacing= 0.6.sp
    ),
    labelMedium = TextStyle(
        fontFamily   = PlusJakartaSansFamily,
        fontWeight   = FontWeight.SemiBold,
        fontSize     = 13.sp,
        lineHeight   = 16.sp,
        letterSpacing= 0.5.sp
    )
)