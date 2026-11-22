package com.easyprint.shop.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import com.easyprint.shop.document.ImageProcessor
import com.easyprint.shop.document.PhotoEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class ImagePrintAdapter(
    private val context: Context,
    private val photos: List<PhotoEntry>,
    private val displayName: String,
    private val paperSize: PaperSize,
    private val requestedColorMode: ColorMode,
    private val photoPrintSize: PhotoPrintSize,
    private val multiPhotoLayout: MultiPhotoLayout,
    private val imageProcessor: ImageProcessor,
    private val onAdapterFinished: () -> Unit
) : PrintDocumentAdapter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var currentAttributes: PrintAttributes? = null

    private fun computeOutputPageCount(): Int {
        if (photos.isEmpty()) return 0
        return if (photos.size > 1 && multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET) {
            PrintPreset.computeCombinedSheetPages(
                paperSize = paperSize,
                imageDimensions = photos.map { it.orientedWidth to it.orientedHeight }
            ).size
        } else {
            photos.size
        }
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        activeJob?.cancel()
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        val changed = oldAttributes != newAttributes
        currentAttributes = newAttributes

        val pageCount = computeOutputPageCount()
        val info = PrintDocumentInfo.Builder(sanitizeJobName(displayName))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
            .setPageCount(pageCount)
            .build()

        callback.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val attributes = currentAttributes
        if (attributes == null) {
            callback.onWriteFailed("Print attributes not initialized.")
            return
        }

        activeJob?.cancel()
        activeJob = scope.launch {
            var printedDoc: PrintedPdfDocument? = null
            try {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return@launch
                }

                printedDoc = PrintedPdfDocument(context, attributes)

                val useMonochrome = requestedColorMode == ColorMode.BLACK_AND_WHITE ||
                    attributes.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    if (useMonochrome) {
                        val cm = ColorMatrix().apply { setSaturation(0f) }
                        colorFilter = ColorMatrixColorFilter(cm)
                    }
                }

                val isCombined = photos.size > 1 && multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET
                var wroteAny = false

                if (isCombined) {
                    val sheetChunks = photos.chunked(PrintPreset.MAX_PHOTOS_PER_COMBINED_SHEET)
                    for (sheetIndex in sheetChunks.indices) {
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return@launch
                        }
                        if (!isPageInRanges(sheetIndex, pages)) continue

                        val chunk = sheetChunks[sheetIndex]
                        val printedPage = printedDoc.startPage(sheetIndex)
                        val sheetWidthMm = printedPage.info.pageWidth / PrintPreset.POINTS_PER_MM
                        val sheetHeightMm = printedPage.info.pageHeight / PrintPreset.POINTS_PER_MM
                        val effectivePaper = PaperSizeForSheet(sheetWidthMm, sheetHeightMm)

                        val sheetLayouts = PrintPreset.computeCombinedSheetPages(
                            paperSize = effectivePaper.nearestOrExactPaperSize(paperSize),
                            imageDimensions = chunk.map { it.orientedWidth to it.orientedHeight }
                        )
                        // Fit rects directly inside actual printed contentRect to respect printer margins.
                        val layout = sheetLayouts.firstOrNull()
                        if (layout != null) {
                            val scaleX = printedPage.info.pageWidth / PrintPreset.mmToPoints(layout.sheetWidthMm)
                            val scaleY = printedPage.info.pageHeight / PrintPreset.mmToPoints(layout.sheetHeightMm)
                            val uniformScale = min(scaleX, scaleY)

                            chunk.forEachIndexed { idx, photo ->
                                if (cancellationSignal.isCanceled) {
                                    callback.onWriteCancelled()
                                    return@launch
                                }
                                val rectMm = layout.imageRectsMm.getOrNull(idx) ?: return@forEachIndexed
                                val bitmap = imageProcessor.decodeForPrint(
                                    file = photo.file,
                                    exifOrientation = photo.exifOrientation
                                ) ?: return@forEachIndexed
                                try {
                                    val dest = RectF(
                                        PrintPreset.mmToPoints(rectMm.leftMm) * uniformScale,
                                        PrintPreset.mmToPoints(rectMm.topMm) * uniformScale,
                                        PrintPreset.mmToPoints(rectMm.rightMm) * uniformScale,
                                        PrintPreset.mmToPoints(rectMm.bottomMm) * uniformScale
                                    )
                                    printedPage.canvas.drawBitmap(bitmap, null, dest, paint)
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }
                        printedDoc.finishPage(printedPage)
                        wroteAny = true
                    }
                } else {
                    for (pageIndex in photos.indices) {
                        if (cancellationSignal.isCanceled) {
                            callback.onWriteCancelled()
                            return@launch
                        }
                        if (!isPageInRanges(pageIndex, pages)) continue

                        val photo = photos[pageIndex]
                        val printedPage = printedDoc.startPage(pageIndex)
                        val sheetWidthMm = printedPage.info.pageWidth / PrintPreset.POINTS_PER_MM
                        val sheetHeightMm = printedPage.info.pageHeight / PrintPreset.POINTS_PER_MM
                        val contentRectPt = printedPage.info.contentRect

                        val rectMm = computeSinglePhotoRectOnActualSheet(
                            sheetWidthMm = sheetWidthMm,
                            sheetHeightMm = sheetHeightMm,
                            contentLeftMm = contentRectPt.left / PrintPreset.POINTS_PER_MM,
                            contentTopMm = contentRectPt.top / PrintPreset.POINTS_PER_MM,
                            contentWidthMm = contentRectPt.width() / PrintPreset.POINTS_PER_MM,
                            contentHeightMm = contentRectPt.height() / PrintPreset.POINTS_PER_MM,
                            photoPrintSize = photoPrintSize,
                            imageWidth = photo.orientedWidth,
                            imageHeight = photo.orientedHeight
                        )

                        val bitmap: Bitmap = imageProcessor.decodeForPrint(
                            file = photo.file,
                            exifOrientation = photo.exifOrientation
                        ) ?: run {
                            printedDoc.finishPage(printedPage)
                            callback.onWriteFailed("Unable to decode image for printing.")
                            return@launch
                        }

                        try {
                            val dest = RectF(
                                PrintPreset.mmToPoints(rectMm.leftMm),
                                PrintPreset.mmToPoints(rectMm.topMm),
                                PrintPreset.mmToPoints(rectMm.rightMm),
                                PrintPreset.mmToPoints(rectMm.bottomMm)
                            )
                            printedPage.canvas.drawBitmap(bitmap, null, dest, paint)
                        } finally {
                            bitmap.recycle()
                        }
                        printedDoc.finishPage(printedPage)
                        wroteAny = true
                    }
                }

                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return@launch
                }

                if (!wroteAny) {
                    callback.onWriteFailed("No pages selected for printing.")
                    return@launch
                }

                FileOutputStream(destination.fileDescriptor).use { out ->
                    printedDoc.writeTo(out)
                    out.flush()
                }
                callback.onWriteFinished(pages)
            } catch (_: OutOfMemoryError) {
                callback.onWriteFailed("Not enough memory to prepare image for printing.")
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "Failed to prepare image for printing.")
            } finally {
                runCatching { printedDoc?.close() }
            }
        }
    }

    override fun onFinish() {
        activeJob?.cancel()
        scope.cancel()
        onAdapterFinished()
    }

    private fun computeSinglePhotoRectOnActualSheet(
        sheetWidthMm: Float,
        sheetHeightMm: Float,
        contentLeftMm: Float,
        contentTopMm: Float,
        contentWidthMm: Float,
        contentHeightMm: Float,
        photoPrintSize: PhotoPrintSize,
        imageWidth: Int,
        imageHeight: Int
    ): PhysicalRectMm {
        val marginLeft = max(PrintPreset.SAFE_MARGIN_MM, contentLeftMm)
        val marginTop = max(PrintPreset.SAFE_MARGIN_MM, contentTopMm)
        val marginRight = max(PrintPreset.SAFE_MARGIN_MM, sheetWidthMm - (contentLeftMm + contentWidthMm))
        val marginBottom = max(PrintPreset.SAFE_MARGIN_MM, sheetHeightMm - (contentTopMm + contentHeightMm))

        val printableW = max(10f, sheetWidthMm - marginLeft - marginRight)
        val printableH = max(10f, sheetHeightMm - marginTop - marginBottom)
        val imageAspect = max(1, imageWidth).toFloat() / max(1, imageHeight).toFloat()

        val (boxW, boxH) = if (
            photoPrintSize.shortSideInches == null || photoPrintSize.longSideInches == null
        ) {
            printableW to printableH
        } else {
            val shortMm = PrintPreset.inchesToMm(photoPrintSize.shortSideInches)
            val longMm = PrintPreset.inchesToMm(photoPrintSize.longSideInches)
            val isLandscapeImage = imageWidth > imageHeight
            val rawW = if (isLandscapeImage) longMm else shortMm
            val rawH = if (isLandscapeImage) shortMm else longMm
            // Fit within printable area without ever cropping.
            val fitScale = min(1f, min(printableW / rawW, printableH / rawH))
            (rawW * fitScale) to (rawH * fitScale)
        }

        return PrintPreset.fitInsideCentered(
            containerLeft = marginLeft + (printableW - boxW) / 2f,
            containerTop = marginTop + (printableH - boxH) / 2f,
            containerWidth = boxW,
            containerHeight = boxH,
            contentAspect = imageAspect
        )
    }

    private data class PaperSizeForSheet(val widthMm: Float, val heightMm: Float) {
        fun nearestOrExactPaperSize(fallback: PaperSize): PaperSize {
            return fallback
        }
    }

    private fun isPageInRanges(pageIndex: Int, ranges: Array<out PageRange>): Boolean {
        for (range in ranges) {
            if (range == PageRange.ALL_PAGES) return true
            if (pageIndex >= range.start && pageIndex <= range.end) return true
        }
        return false
    }

    private fun sanitizeJobName(raw: String): String {
        return raw.trim().ifEmpty { "EasyPrint Photo" }.take(80)
    }
}
