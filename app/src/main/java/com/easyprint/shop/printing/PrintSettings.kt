package com.easyprint.shop.printing

import android.print.PrintAttributes

enum class PaperSize(
    val widthMm: Float,
    val heightMm: Float
) {
    A4(widthMm = 210.0f, heightMm = 297.0f),
    A5(widthMm = 148.0f, heightMm = 210.0f),
    LEGAL(widthMm = 215.9f, heightMm = 355.6f);

    fun toMediaSize(isLandscape: Boolean): PrintAttributes.MediaSize {
        val base = when (this) {
            A4 -> PrintAttributes.MediaSize.ISO_A4
            A5 -> PrintAttributes.MediaSize.ISO_A5
            LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        }
        return if (isLandscape) base.asLandscape() else base.asPortrait()
    }
}

enum class ColorMode {
    COLOR,
    BLACK_AND_WHITE;

    fun toPrintAttributeColorMode(): Int {
        return when (this) {
            COLOR -> PrintAttributes.COLOR_MODE_COLOR
            BLACK_AND_WHITE -> PrintAttributes.COLOR_MODE_MONOCHROME
        }
    }
}

data class PrintDefaults(
    val paperSize: PaperSize = PaperSize.A4,
    val colorMode: ColorMode = ColorMode.COLOR
)

sealed interface PrintJobStatus {
    data object Idle : PrintJobStatus
    data object Preparing : PrintJobStatus
    data object DialogOpened : PrintJobStatus
    data object CompletedSent : PrintJobStatus
    data object Cancelled : PrintJobStatus
    data object Failed : PrintJobStatus
}
