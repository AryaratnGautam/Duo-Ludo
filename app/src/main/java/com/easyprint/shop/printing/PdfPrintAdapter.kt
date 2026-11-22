package com.easyprint.shop.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PdfPrintAdapter(
    private val context: Context,
    private val pdfFile: File,
    private val displayName: String,
    private val pageCount: Int,
    private val requestedColorMode: ColorMode,
    private val onAdapterFinished: () -> Unit
) : PrintDocumentAdapter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var currentAttributes: PrintAttributes? = null

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

        val info = PrintDocumentInfo.Builder(sanitizeJobName(displayName))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
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
            var inputPfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            var printedDoc: PrintedPdfDocument? = null
            try {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return@launch
                }

                inputPfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(inputPfd)
                printedDoc = PrintedPdfDocument(context, attributes)

                val useMonochrome = requestedColorMode == ColorMode.BLACK_AND_WHITE ||
                    attributes.colorMode == PrintAttributes.COLOR_MODE_MONOCHROME

                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    if (useMonochrome) {
                        val cm = ColorMatrix().apply { setSaturation(0f) }
                        colorFilter = ColorMatrixColorFilter(cm)
                    }
                }

                val totalPages = renderer.pageCount
                var wroteAny = false

                for (pageIndex in 0 until totalPages) {
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return@launch
                    }
                    if (!isPageInRanges(pageIndex, pages)) {
                        continue
                    }

                    val pdfPage = renderer.openPage(pageIndex)
                    var pageBitmap: Bitmap? = null
                    try {
                        val printedPage = printedDoc.startPage(pageIndex)
                        val contentRect = printedPage.info.contentRect
                        val availW = max(1, contentRect.width()).toFloat()
                        val availH = max(1, contentRect.height()).toFloat()

                        val srcW = max(1, pdfPage.width).toFloat()
                        val srcH = max(1, pdfPage.height).toFloat()

                        // Render at crisp ~240 DPI scaled safely within a memory budget per page.
                        val dpiScale = PRINT_DPI / PrintPreset.POINTS_PER_INCH
                        val fitScale = min(availW / srcW, availH / srcH)
                        val drawW = srcW * fitScale
                        val drawH = srcH * fitScale
                        var renderW = max(1, (drawW * dpiScale).toInt())
                        var renderH = max(1, (drawH * dpiScale).toInt())

                        val pixelCount = renderW.toLong() * renderH.toLong()
                        if (pixelCount > MAX_RENDER_PIXELS) {
                            val downscale = sqrt(MAX_RENDER_PIXELS.toDouble() / pixelCount.toDouble()).toFloat()
                            renderW = max(1, (renderW * downscale).toInt())
                            renderH = max(1, (renderH * downscale).toInt())
                        }

                        pageBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                        pageBitmap.eraseColor(Color.WHITE)
                        pdfPage.render(
                            pageBitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                        )

                        val left = contentRect.left + (availW - drawW) / 2f
                        val top = contentRect.top + (availH - drawH) / 2f
                        val destRect = RectF(left, top, left + drawW, top + drawH)
                        printedPage.canvas.drawBitmap(pageBitmap, null, destRect, paint)
                        printedDoc.finishPage(printedPage)
                        wroteAny = true
                    } finally {
                        runCatching { pageBitmap?.recycle() }
                        runCatching { pdfPage.close() }
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
            } catch (_: SecurityException) {
                callback.onWriteFailed("Password-protected PDF cannot be printed.")
            } catch (_: OutOfMemoryError) {
                callback.onWriteFailed("Not enough memory to render this PDF page.")
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "Failed to prepare PDF pages for printing.")
            } finally {
                runCatching { printedDoc?.close() }
                runCatching { renderer?.close() }
                runCatching { inputPfd?.close() }
            }
        }
    }

    override fun onFinish() {
        activeJob?.cancel()
        scope.cancel()
        onAdapterFinished()
    }

    private fun isPageInRanges(pageIndex: Int, ranges: Array<out PageRange>): Boolean {
        for (range in ranges) {
            if (range == PageRange.ALL_PAGES) return true
            if (pageIndex >= range.start && pageIndex <= range.end) return true
        }
        return false
    }

    private fun sanitizeJobName(raw: String): String {
        return raw.trim().ifEmpty { "EasyPrint Document" }.take(80)
    }

    companion object {
        private const val PRINT_DPI = 240f
        private const val MAX_RENDER_PIXELS = 6_500_000L
    }
}
