package com.easyprint.shop.document

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

enum class DetectedFileType {
    PDF,
    JPEG,
    PNG,
    UNSUPPORTED
}

data class CachedIncomingFile(
    val file: File,
    val displayName: String,
    val fileType: DetectedFileType
)

sealed interface DocumentError {
    data object UnsupportedFile : DocumentError
    data object CorruptedPdf : DocumentError
    data object PasswordProtectedPdf : DocumentError
    data object UriPermissionFailure : DocumentError
    data object InsufficientStorage : DocumentError
    data object VeryLargeImage : DocumentError
    data object GenericReadFailure : DocumentError
}

sealed class IncomingCopyOutcome {
    data class Success(val files: List<CachedIncomingFile>) : IncomingCopyOutcome()
    data class Failure(val error: DocumentError) : IncomingCopyOutcome()
}

class IncomingFileHandler(private val context: Context) {

    private val tempDir: File
        get() = File(context.cacheDir, TEMP_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

    fun extractUrisFromIntent(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        val action = intent.action ?: return emptyList()
        val collected = LinkedHashSet<Uri>()

        when (action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { collected.add(it) }
                collectClipData(intent, collected)
            }

            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                if (streamUri != null) {
                    collected.add(streamUri)
                }
                if (collected.isEmpty()) {
                    intent.data?.let { collected.add(it) }
                    collectClipData(intent, collected)
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = IntentCompat.getParcelableArrayListExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
                streamUris?.forEach { collected.add(it) }
                collectClipData(intent, collected)
            }
        }

        return collected.toList()
    }

    private fun collectClipData(intent: Intent, target: MutableSet<Uri>) {
        val clip = intent.clipData ?: return
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i)?.uri?.let { target.add(it) }
        }
    }

    fun copyToPrivateCache(uris: List<Uri>): IncomingCopyOutcome {
        if (uris.isEmpty()) return IncomingCopyOutcome.Failure(DocumentError.GenericReadFailure)
        clearTempFiles()

        val resolver = context.contentResolver
        val cachedFiles = mutableListOf<CachedIncomingFile>()

        for (uri in uris) {
            val outcome = copySingleUri(resolver, uri)
            when (outcome) {
                is SingleCopyResult.Success -> cachedFiles.add(outcome.file)
                is SingleCopyResult.Failure -> {
                    clearTempFiles()
                    return IncomingCopyOutcome.Failure(outcome.error)
                }
            }
        }

        if (cachedFiles.isEmpty()) {
            return IncomingCopyOutcome.Failure(DocumentError.UnsupportedFile)
        }
        return IncomingCopyOutcome.Success(cachedFiles)
    }

    private sealed class SingleCopyResult {
        data class Success(val file: CachedIncomingFile) : SingleCopyResult()
        data class Failure(val error: DocumentError) : SingleCopyResult()
    }

    private fun copySingleUri(resolver: ContentResolver, uri: Uri): SingleCopyResult {
        val displayName = resolveDisplayName(resolver, uri) ?: "document"
        val mimeType = runCatching { resolver.getType(uri) }.getOrNull()?.lowercase()

        // Check declared size against available internal cache storage before copying.
        val declaredSize = resolveFileSize(resolver, uri)
        if (declaredSize != null && declaredSize > 0L) {
            val available = availableCacheBytes()
            if (available in 1 until (declaredSize + MIN_SAFETY_BUFFER_BYTES)) {
                return SingleCopyResult.Failure(DocumentError.InsufficientStorage)
            }
        }

        val targetFile = File(tempDir, "ep_${UUID.randomUUID()}.tmp")
        try {
            val inputStream = resolver.openInputStream(uri)
                ?: return SingleCopyResult.Failure(DocumentError.UriPermissionFailure)

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
        } catch (e: SecurityException) {
            targetFile.delete()
            return SingleCopyResult.Failure(DocumentError.UriPermissionFailure)
        } catch (e: IOException) {
            targetFile.delete()
            val msg = e.message?.lowercase().orEmpty()
            return if (msg.contains("enospc") || msg.contains("no space") || availableCacheBytes() < MIN_SAFETY_BUFFER_BYTES) {
                SingleCopyResult.Failure(DocumentError.InsufficientStorage)
            } else {
                SingleCopyResult.Failure(DocumentError.GenericReadFailure)
            }
        } catch (_: Exception) {
            targetFile.delete()
            return SingleCopyResult.Failure(DocumentError.GenericReadFailure)
        }

        if (!targetFile.exists() || targetFile.length() == 0L) {
            targetFile.delete()
            return SingleCopyResult.Failure(DocumentError.GenericReadFailure)
        }

        val detected = detectFileType(targetFile, mimeType, displayName)
        if (detected == DetectedFileType.UNSUPPORTED) {
            targetFile.delete()
            return SingleCopyResult.Failure(DocumentError.UnsupportedFile)
        }

        val ext = when (detected) {
            DetectedFileType.PDF -> ".pdf"
            DetectedFileType.JPEG -> ".jpg"
            DetectedFileType.PNG -> ".png"
            DetectedFileType.UNSUPPORTED -> ".tmp"
        }
        val finalFile = File(tempDir, "ep_${UUID.randomUUID()}$ext")
        val renamed = targetFile.renameTo(finalFile)
        val resultFile = if (renamed) finalFile else targetFile

        return SingleCopyResult.Success(
            CachedIncomingFile(
                file = resultFile,
                displayName = displayName,
                fileType = detected
            )
        )
    }

    private fun detectFileType(
        file: File,
        mimeType: String?,
        displayName: String
    ): DetectedFileType {
        val header = ByteArray(64)
        val readCount = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(0)

        if (readCount >= 4) {
            // PDF magic: %PDF within leading 64 bytes (allows UTF-8 BOM or leading whitespace)
            for (i in 0..(readCount - 4)) {
                if (header[i] == 0x25.toByte() &&
                    header[i + 1] == 0x50.toByte() &&
                    header[i + 2] == 0x44.toByte() &&
                    header[i + 3] == 0x46.toByte()
                ) {
                    return DetectedFileType.PDF
                }
            }
            // JPEG magic: FF D8 FF
            if (header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte()
            ) {
                return DetectedFileType.JPEG
            }
            // PNG magic: 89 50 4E 47
            if (header[0] == 0x89.toByte() &&
                header[1] == 0x50.toByte() &&
                header[2] == 0x4E.toByte() &&
                header[3] == 0x47.toByte()
            ) {
                return DetectedFileType.PNG
            }
        }

        val lowerName = displayName.lowercase()
        return when {
            mimeType == "application/pdf" || mimeType == "application/x-pdf" || lowerName.endsWith(".pdf") ->
                DetectedFileType.PDF
            mimeType == "image/jpeg" || mimeType == "image/jpg" ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ->
                DetectedFileType.JPEG
            mimeType == "image/png" || lowerName.endsWith(".png") ->
                DetectedFileType.PNG
            else -> DetectedFileType.UNSUPPORTED
        }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String? {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    private fun resolveFileSize(resolver: ContentResolver, uri: Uri): Long? {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun availableCacheBytes(): Long {
        return runCatching {
            val stat = StatFs(context.cacheDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(Long.MAX_VALUE)
    }

    fun clearTempFiles() {
        val dir = File(context.cacheDir, TEMP_DIR_NAME)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
        }
    }

    companion object {
        private const val TEMP_DIR_NAME = "easyprint_temp"
        private const val MIN_SAFETY_BUFFER_BYTES = 512L * 1024L
    }
}
