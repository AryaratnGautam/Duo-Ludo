package com.easyprint.shop.printing

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class PhotoPrintSize(
    val shortSideInches: Float?,
    val longSideInches: Float?
) {
    SMALL(shortSideInches = 3.5f, longSideInches = 5.0f),
    MEDIUM(shortSideInches = 4.0f, longSideInches = 6.0f),
    LARGE(shortSideInches = 5.0f, longSideInches = 7.0f),
    FULL_SHEET(shortSideInches = null, longSideInches = null)
}

enum class MultiPhotoLayout {
    ONE_PER_SHEET,
    COMBINE_ON_SHEET
}

data class PhysicalRectMm(
    val leftMm: Float,
    val topMm: Float,
    val widthMm: Float,
    val heightMm: Float
) {
    val rightMm: Float get() = leftMm + widthMm
    val bottomMm: Float get() = topMm + heightMm
}

data class SheetLayoutResult(
    val sheetWidthMm: Float,
    val sheetHeightMm: Float,
    val isLandscapeSheet: Boolean,
    val printableRectMm: PhysicalRectMm,
    val imageRectsMm: List<PhysicalRectMm>
)

object PrintPreset {

    const val MM_PER_INCH = 25.4f
    const val POINTS_PER_INCH = 72.0f
    const val POINTS_PER_MM = POINTS_PER_INCH / MM_PER_INCH

    // Conservative printable margin (mm) accounting for HP Ink Tank 315 non-borderless margins.
    const val SAFE_MARGIN_MM = 6.0f
    const val GRID_GAP_MM = 4.0f
    const val MAX_PHOTOS_PER_COMBINED_SHEET = 9

    fun mmToPoints(mm: Float): Float = mm * POINTS_PER_MM

    fun inchesToMm(inches: Float): Float = inches * MM_PER_INCH

    fun shouldUseLandscapeSheetForSinglePhoto(
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        if (imageWidth <= 0 || imageHeight <= 0) return false
        return imageWidth > imageHeight
    }

    fun shouldUseLandscapeSheetForPdf(
        firstPageWidth: Int,
        firstPageHeight: Int
    ): Boolean {
        if (firstPageWidth <= 0 || firstPageHeight <= 0) return false
        return firstPageWidth > firstPageHeight
    }

    fun computeSinglePhotoLayout(
        paperSize: PaperSize,
        photoPrintSize: PhotoPrintSize,
        imageWidth: Int,
        imageHeight: Int,
        marginMm: Float = SAFE_MARGIN_MM
    ): SheetLayoutResult {
        val safeImgW = max(1, imageWidth).toFloat()
        val safeImgH = max(1, imageHeight).toFloat()
        val imageAspect = safeImgW / safeImgH
        val isLandscape = shouldUseLandscapeSheetForSinglePhoto(imageWidth, imageHeight)

        val sheetW = if (isLandscape) paperSize.heightMm else paperSize.widthMm
        val sheetH = if (isLandscape) paperSize.widthMm else paperSize.heightMm
        val printable = PhysicalRectMm(
            leftMm = marginMm,
            topMm = marginMm,
            widthMm = max(10f, sheetW - 2f * marginMm),
            heightMm = max(10f, sheetH - 2f * marginMm)
        )

        val (boxW, boxH) = if (
            photoPrintSize.shortSideInches == null || photoPrintSize.longSideInches == null
        ) {
            printable.widthMm to printable.heightMm
        } else {
            val shortMm = inchesToMm(photoPrintSize.shortSideInches)
            val longMm = inchesToMm(photoPrintSize.longSideInches)
            val rawW = if (isLandscape) longMm else shortMm
            val rawH = if (isLandscape) shortMm else longMm
            // Never allow preset box to exceed printable area or silently crop on smaller paper (e.g. A5).
            val fitScale = min(1f, min(printable.widthMm / rawW, printable.heightMm / rawH))
            (rawW * fitScale) to (rawH * fitScale)
        }

        val placed = fitInsideCentered(
            containerLeft = printable.leftMm + (printable.widthMm - boxW) / 2f,
            containerTop = printable.topMm + (printable.heightMm - boxH) / 2f,
            containerWidth = boxW,
            containerHeight = boxH,
            contentAspect = imageAspect
        )

        return SheetLayoutResult(
            sheetWidthMm = sheetW,
            sheetHeightMm = sheetH,
            isLandscapeSheet = isLandscape,
            printableRectMm = printable,
            imageRectsMm = listOf(placed)
        )
    }

    fun computeCombinedSheetPages(
        paperSize: PaperSize,
        imageDimensions: List<Pair<Int, Int>>,
        marginMm: Float = SAFE_MARGIN_MM,
        gapMm: Float = GRID_GAP_MM
    ): List<SheetLayoutResult> {
        if (imageDimensions.isEmpty()) return emptyList()
        val chunks = imageDimensions.chunked(MAX_PHOTOS_PER_COMBINED_SHEET)
        return chunks.map { chunk ->
            computeCombinedSingleSheet(
                paperSize = paperSize,
                imageDimensions = chunk,
                marginMm = marginMm,
                gapMm = gapMm
            )
        }
    }

    private fun computeCombinedSingleSheet(
        paperSize: PaperSize,
        imageDimensions: List<Pair<Int, Int>>,
        marginMm: Float,
        gapMm: Float
    ): SheetLayoutResult {
        val count = imageDimensions.size
        val landscapeCount = imageDimensions.count { (w, h) -> w > h }
        val isLandscapeSheet = if (count == 2) {
            // Two portrait photos sit nicely side-by-side on landscape paper; two landscape photos stack cleanly on portrait.
            landscapeCount == 0
        } else {
            landscapeCount > count / 2
        }

        val sheetW = if (isLandscapeSheet) paperSize.heightMm else paperSize.widthMm
        val sheetH = if (isLandscapeSheet) paperSize.widthMm else paperSize.heightMm
        val printable = PhysicalRectMm(
            leftMm = marginMm,
            topMm = marginMm,
            widthMm = max(10f, sheetW - 2f * marginMm),
            heightMm = max(10f, sheetH - 2f * marginMm)
        )

        val (cols, rows) = when (count) {
            1 -> 1 to 1
            2 -> if (isLandscapeSheet) 2 to 1 else 1 to 2
            3, 4 -> 2 to 2
            5, 6 -> if (isLandscapeSheet) 3 to 2 else 2 to 3
            else -> {
                val c = ceil(sqrt(count.toDouble())).toInt()
                val r = ceil(count.toDouble() / c).toInt()
                c to r
            }
        }

        val totalGapW = gapMm * (cols - 1)
        val totalGapH = gapMm * (rows - 1)
        val cellW = max(10f, (printable.widthMm - totalGapW) / cols)
        val cellH = max(10f, (printable.heightMm - totalGapH) / rows)

        val rects = imageDimensions.mapIndexed { index, (w, h) ->
            val col = index % cols
            val row = index / cols
            val cellLeft = printable.leftMm + col * (cellW + gapMm)
            val cellTop = printable.topMm + row * (cellH + gapMm)
            val aspect = max(1, w).toFloat() / max(1, h).toFloat()
            fitInsideCentered(
                containerLeft = cellLeft,
                containerTop = cellTop,
                containerWidth = cellW,
                containerHeight = cellH,
                contentAspect = aspect
            )
        }

        return SheetLayoutResult(
            sheetWidthMm = sheetW,
            sheetHeightMm = sheetH,
            isLandscapeSheet = isLandscapeSheet,
            printableRectMm = printable,
            imageRectsMm = rects
        )
    }

    fun fitInsideCentered(
        containerLeft: Float,
        containerTop: Float,
        containerWidth: Float,
        containerHeight: Float,
        contentAspect: Float
    ): PhysicalRectMm {
        val safeAspect = if (contentAspect.isFinite() && contentAspect > 0f) contentAspect else 1f
        val containerAspect = containerWidth / max(0.001f, containerHeight)
        val drawW: Float
        val drawH: Float
        if (safeAspect >= containerAspect) {
            drawW = containerWidth
            drawH = containerWidth / safeAspect
        } else {
            drawH = containerHeight
            drawW = containerHeight * safeAspect
        }
        return PhysicalRectMm(
            leftMm = containerLeft + (containerWidth - drawW) / 2f,
            topMm = containerTop + (containerHeight - drawH) / 2f,
            widthMm = drawW,
            heightMm = drawH
        )
    }

    fun computeSampleSize(srcWidth: Int, srcHeight: Int, maxDimPx: Int): Int {
        var inSampleSize = 1
        if (srcHeight > maxDimPx || srcWidth > maxDimPx) {
            val halfH = srcHeight / 2
            val halfW = srcWidth / 2
            while ((halfH / inSampleSize) >= maxDimPx && (halfW / inSampleSize) >= maxDimPx) {
                inSampleSize *= 2
            }
        }
        return max(1, floor(inSampleSize.toDouble()).toInt())
    }
}
