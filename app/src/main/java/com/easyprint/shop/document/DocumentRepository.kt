package com.easyprint.shop.document

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoEntry(
    val file: File,
    val displayName: String,
    val previewBitmap: Bitmap,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val exifOrientation: Int
)

sealed interface LoadedDocument {
    val displayName: String

    data class Pdf(
        val file: File,
        override val displayName: String,
        val pageCount: Int,
        val firstPageWidthPt: Int,
        val firstPageHeightPt: Int,
        val currentPageIndex: Int,
        val currentPageWidthPt: Int,
        val currentPageHeightPt: Int,
        val currentPageBitmap: Bitmap
    ) : LoadedDocument

    data class Photos(
        val items: List<PhotoEntry>,
        override val displayName: String
    ) : LoadedDocument
}

sealed class DocumentLoadOutcome {
    data class Success(val document: LoadedDocument) : DocumentLoadOutcome()
    data class Failure(val error: DocumentError) : DocumentLoadOutcome()
}

class DocumentRepository(
    private val incomingFileHandler: IncomingFileHandler,
    private val pdfProcessor: PdfProcessor,
    private val imageProcessor: ImageProcessor
) {

    fun extractUrisFromIntent(intent: Intent?): List<Uri> {
        return incomingFileHandler.extractUrisFromIntent(intent)
    }

    suspend fun loadFromUris(uris: List<Uri>): DocumentLoadOutcome = withContext(Dispatchers.IO) {
        when (val copyOutcome = incomingFileHandler.copyToPrivateCache(uris)) {
            is IncomingCopyOutcome.Failure -> DocumentLoadOutcome.Failure(copyOutcome.error)
            is IncomingCopyOutcome.Success -> processCachedFiles(copyOutcome.files)
        }
    }

    suspend fun renderPdfPage(
        document: LoadedDocument.Pdf,
        pageIndex: Int
    ): PdfPageRenderOutcome = withContext(Dispatchers.IO) {
        pdfProcessor.renderPreviewPage(document.file, pageIndex)
    }

    fun clearTemporaryFiles() {
        incomingFileHandler.clearTempFiles()
    }

    private fun processCachedFiles(files: List<CachedIncomingFile>): DocumentLoadOutcome {
        val first = files.firstOrNull()
            ?: return DocumentLoadOutcome.Failure(DocumentError.UnsupportedFile)

        return if (first.fileType == DetectedFileType.PDF) {
            loadPdf(first)
        } else {
            val imageFiles = files.filter {
                it.fileType == DetectedFileType.JPEG || it.fileType == DetectedFileType.PNG
            }
            if (imageFiles.isEmpty()) {
                incomingFileHandler.clearTempFiles()
                DocumentLoadOutcome.Failure(DocumentError.UnsupportedFile)
            } else {
                loadPhotos(imageFiles)
            }
        }
    }

    private fun loadPdf(cachedFile: CachedIncomingFile): DocumentLoadOutcome {
        return when (val inspect = pdfProcessor.inspectPdf(cachedFile.file)) {
            is PdfInspectOutcome.Failure -> {
                incomingFileHandler.clearTempFiles()
                DocumentLoadOutcome.Failure(inspect.error)
            }
            is PdfInspectOutcome.Success -> {
                when (val pageRender = pdfProcessor.renderPreviewPage(cachedFile.file, 0)) {
                    is PdfPageRenderOutcome.Failure -> {
                        incomingFileHandler.clearTempFiles()
                        DocumentLoadOutcome.Failure(pageRender.error)
                    }
                    is PdfPageRenderOutcome.Success -> {
                        DocumentLoadOutcome.Success(
                            LoadedDocument.Pdf(
                                file = cachedFile.file,
                                displayName = cachedFile.displayName,
                                pageCount = inspect.metadata.pageCount,
                                firstPageWidthPt = inspect.metadata.firstPageWidthPt,
                                firstPageHeightPt = inspect.metadata.firstPageHeightPt,
                                currentPageIndex = 0,
                                currentPageWidthPt = pageRender.pageWidthPt,
                                currentPageHeightPt = pageRender.pageHeightPt,
                                currentPageBitmap = pageRender.bitmap
                            )
                        )
                    }
                }
            }
        }
    }

    private fun loadPhotos(imageFiles: List<CachedIncomingFile>): DocumentLoadOutcome {
        val entries = mutableListOf<PhotoEntry>()
        for (item in imageFiles) {
            when (val decode = imageProcessor.decodeForPreview(item.file)) {
                is PhotoDecodeOutcome.Failure -> {
                    entries.forEach { it.previewBitmap.recycle() }
                    incomingFileHandler.clearTempFiles()
                    return DocumentLoadOutcome.Failure(decode.error)
                }
                is PhotoDecodeOutcome.Success -> {
                    entries.add(
                        PhotoEntry(
                            file = item.file,
                            displayName = item.displayName,
                            previewBitmap = decode.result.previewBitmap,
                            orientedWidth = decode.result.orientedWidth,
                            orientedHeight = decode.result.orientedHeight,
                            exifOrientation = decode.result.exifOrientation
                        )
                    )
                }
            }
        }

        val combinedName = if (entries.size == 1) {
            entries.first().displayName
        } else {
            "${entries.first().displayName} (+${entries.size - 1})"
        }
        return DocumentLoadOutcome.Success(
            LoadedDocument.Photos(
                items = entries,
                displayName = combinedName
            )
        )
    }
}
