package com.easyprint.shop.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

data class PdfMetadata(
    val pageCount: Int,
    val firstPageWidthPt: Int,
    val firstPageHeightPt: Int
)

sealed class PdfInspectOutcome {
    data class Success(val metadata: PdfMetadata) : PdfInspectOutcome()
    data class Failure(val error: DocumentError) : PdfInspectOutcome()
}

sealed class PdfPageRenderOutcome {
    data class Success(
        val bitmap: Bitmap,
        val pageWidthPt: Int,
        val pageHeightPt: Int
    ) : PdfPageRenderOutcome()

    data class Failure(val error: DocumentError) : PdfPageRenderOutcome()
}

class PdfProcessor {

    fun inspectPdf(file: File): PdfInspectOutcome {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            if (count <= 0) {
                return PdfInspectOutcome.Failure(DocumentError.CorruptedPdf)
            }
            val firstPage = renderer.openPage(0)
            val w = firstPage.width
            val h = firstPage.height
            firstPage.close()
            return PdfInspectOutcome.Success(
                PdfMetadata(
                    pageCount = count,
                    firstPageWidthPt = max(1, w),
                    firstPageHeightPt = max(1, h)
                )
            )
        } catch (_: SecurityException) {
            return PdfInspectOutcome.Failure(DocumentError.PasswordProtectedPdf)
        } catch (_: IOException) {
            return PdfInspectOutcome.Failure(DocumentError.CorruptedPdf)
        } catch (_: IllegalStateException) {
            return PdfInspectOutcome.Failure(DocumentError.CorruptedPdf)
        } catch (_: Exception) {
            return PdfInspectOutcome.Failure(DocumentError.CorruptedPdf)
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    fun renderPreviewPage(
        file: File,
        pageIndex: Int,
        maxDimensionPx: Int = DEFAULT_PREVIEW_MAX_DIM_PX
    ): PdfPageRenderOutcome {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return PdfPageRenderOutcome.Failure(DocumentError.CorruptedPdf)
            }
            page = renderer.openPage(pageIndex)
            val pageW = max(1, page.width)
            val pageH = max(1, page.height)

            val scale = min(
                maxDimensionPx.toFloat() / pageW.toFloat(),
                maxDimensionPx.toFloat() / pageH.toFloat()
            )
            val targetW = max(1, (pageW * scale).toInt())
            val targetH = max(1, (pageH * scale).toInt())

            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            return PdfPageRenderOutcome.Success(
                bitmap = bitmap,
                pageWidthPt = pageW,
                pageHeightPt = pageH
            )
        } catch (_: SecurityException) {
            return PdfPageRenderOutcome.Failure(DocumentError.PasswordProtectedPdf)
        } catch (_: OutOfMemoryError) {
            return PdfPageRenderOutcome.Failure(DocumentError.GenericReadFailure)
        } catch (_: Exception) {
            return PdfPageRenderOutcome.Failure(DocumentError.CorruptedPdf)
        } finally {
            runCatching { page?.close() }
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    companion object {
        const val DEFAULT_PREVIEW_MAX_DIM_PX = 1400
    }
}
