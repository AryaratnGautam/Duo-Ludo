package com.easyprint.shop.printing

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintJob
import android.print.PrintManager
import com.easyprint.shop.document.ImageProcessor
import com.easyprint.shop.document.LoadedDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PrintCoordinator(
    private val imageProcessor: ImageProcessor
) {

    private var monitorJob: Job? = null

    fun startPrint(
        activity: Activity,
        document: LoadedDocument,
        paperSize: PaperSize,
        colorMode: ColorMode,
        photoPrintSize: PhotoPrintSize,
        multiPhotoLayout: MultiPhotoLayout,
        scope: CoroutineScope,
        onStatusChanged: (PrintJobStatus) -> Unit
    ) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            onStatusChanged(PrintJobStatus.Failed)
            return
        }

        onStatusChanged(PrintJobStatus.Preparing)

        val isLandscape = when (document) {
            is LoadedDocument.Pdf -> PrintPreset.shouldUseLandscapeSheetForPdf(
                firstPageWidth = document.firstPageWidthPt,
                firstPageHeight = document.firstPageHeightPt
            )
            is LoadedDocument.Photos -> {
                if (document.items.size > 1 && multiPhotoLayout == MultiPhotoLayout.COMBINE_ON_SHEET) {
                    val sheet = PrintPreset.computeCombinedSheetPages(
                        paperSize = paperSize,
                        imageDimensions = document.items.map { it.orientedWidth to it.orientedHeight }
                    ).firstOrNull()
                    sheet?.isLandscapeSheet ?: false
                } else {
                    val first = document.items.firstOrNull()
                    if (first != null) {
                        PrintPreset.shouldUseLandscapeSheetForSinglePhoto(
                            imageWidth = first.orientedWidth,
                            imageHeight = first.orientedHeight
                        )
                    } else {
                        false
                    }
                }
            }
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(paperSize.toMediaSize(isLandscape))
            .setColorMode(colorMode.toPrintAttributeColorMode())
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val jobName = "EasyPrint - ${document.displayName}".take(80)

        var adapterFinished = false
        val adapter = when (document) {
            is LoadedDocument.Pdf -> PdfPrintAdapter(
                context = activity,
                pdfFile = document.file,
                displayName = document.displayName,
                pageCount = document.pageCount,
                requestedColorMode = colorMode,
                onAdapterFinished = { adapterFinished = true }
            )
            is LoadedDocument.Photos -> ImagePrintAdapter(
                context = activity,
                photos = document.items,
                displayName = document.displayName,
                paperSize = paperSize,
                requestedColorMode = colorMode,
                photoPrintSize = photoPrintSize,
                multiPhotoLayout = multiPhotoLayout,
                imageProcessor = imageProcessor,
                onAdapterFinished = { adapterFinished = true }
            )
        }

        try {
            val printJob = printManager.print(jobName, adapter, attributes)
            onStatusChanged(PrintJobStatus.DialogOpened)
            observePrintJob(
                printJob = printJob,
                scope = scope,
                isAdapterFinished = { adapterFinished },
                onStatusChanged = onStatusChanged
            )
        } catch (_: Exception) {
            onStatusChanged(PrintJobStatus.Failed)
        }
    }

    private fun observePrintJob(
        printJob: PrintJob?,
        scope: CoroutineScope,
        isAdapterFinished: () -> Boolean,
        onStatusChanged: (PrintJobStatus) -> Unit
    ) {
        monitorJob?.cancel()
        if (printJob == null) return

        monitorJob = scope.launch(Dispatchers.Main) {
            // Observe PrintJob transitions cleanly for up to 5 minutes while user interacts with system dialog.
            repeat(600) {
                delay(500L)
                val info = printJob.info
                when {
                    printJob.isCancelled -> {
                        onStatusChanged(PrintJobStatus.Cancelled)
                        return@launch
                    }
                    printJob.isFailed -> {
                        onStatusChanged(PrintJobStatus.Failed)
                        return@launch
                    }
                    printJob.isCompleted || printJob.isStarted || printJob.isQueued -> {
                        if (isAdapterFinished() && info.state != android.print.PrintJobInfo.STATE_CREATED) {
                            onStatusChanged(PrintJobStatus.CompletedSent)
                            return@launch
                        }
                    }
                }
            }
        }
    }

    fun cancelObservation() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
