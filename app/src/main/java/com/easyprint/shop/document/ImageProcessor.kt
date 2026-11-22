package com.easyprint.shop.document

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.easyprint.shop.printing.PrintPreset
import java.io.File
import kotlin.math.max

data class DecodedPhotoResult(
    val previewBitmap: Bitmap,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val exifOrientation: Int
)

sealed class PhotoDecodeOutcome {
    data class Success(val result: DecodedPhotoResult) : PhotoDecodeOutcome()
    data class Failure(val error: DocumentError) : PhotoDecodeOutcome()
}

class ImageProcessor {

    fun decodeForPreview(
        file: File,
        maxDimensionPx: Int = DEFAULT_PREVIEW_MAX_DIM_PX
    ): PhotoDecodeOutcome {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            if (rawW <= 0 || rawH <= 0) {
                return PhotoDecodeOutcome.Failure(DocumentError.UnsupportedFile)
            }

            val totalPixels = rawW.toLong() * rawH.toLong()
            if (totalPixels > MAX_SAFE_IMAGE_PIXELS) {
                return PhotoDecodeOutcome.Failure(DocumentError.VeryLargeImage)
            }

            val exifOrientation = readExifOrientation(file)
            val (orientedW, orientedH) = orientedDimensions(rawW, rawH, exifOrientation)

            val sampleSize = PrintPreset.computeSampleSize(rawW, rawH, maxDimensionPx)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                ?: return PhotoDecodeOutcome.Failure(DocumentError.UnsupportedFile)

            val orientedBitmap = applyExifTransform(decoded, exifOrientation)
            if (orientedBitmap !== decoded) {
                decoded.recycle()
            }

            PhotoDecodeOutcome.Success(
                DecodedPhotoResult(
                    previewBitmap = orientedBitmap,
                    orientedWidth = orientedW,
                    orientedHeight = orientedH,
                    exifOrientation = exifOrientation
                )
            )
        } catch (_: OutOfMemoryError) {
            PhotoDecodeOutcome.Failure(DocumentError.VeryLargeImage)
        } catch (_: Exception) {
            PhotoDecodeOutcome.Failure(DocumentError.GenericReadFailure)
        }
    }

    fun decodeForPrint(
        file: File,
        exifOrientation: Int,
        maxDimensionPx: Int = DEFAULT_PRINT_MAX_DIM_PX
    ): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val rawW = bounds.outWidth
            val rawH = bounds.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            val sampleSize = PrintPreset.computeSampleSize(rawW, rawH, maxDimensionPx)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
            val oriented = applyExifTransform(decoded, exifOrientation)
            if (oriented !== decoded) {
                decoded.recycle()
            }
            oriented
        } catch (_: OutOfMemoryError) {
            // Retry once at lower resolution so print can still succeed on low-memory devices.
            runCatching {
                val retryBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, retryBounds)
                val fallbackOpts = BitmapFactory.Options().apply {
                    inSampleSize = max(
                        2,
                        PrintPreset.computeSampleSize(
                            retryBounds.outWidth,
                            retryBounds.outHeight,
                            DEFAULT_PREVIEW_MAX_DIM_PX
                        )
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val decoded = BitmapFactory.decodeFile(file.absolutePath, fallbackOpts) ?: return null
                val oriented = applyExifTransform(decoded, exifOrientation)
                if (oriented !== decoded) decoded.recycle()
                oriented
            }.getOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifOrientation(file: File): Int {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun orientedDimensions(
        width: Int,
        height: Int,
        orientation: Int
    ): Pair<Int, Int> {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> height to width
            else -> width to height
        }
    }

    private fun applyExifTransform(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    companion object {
        const val DEFAULT_PREVIEW_MAX_DIM_PX = 1400
        const val DEFAULT_PRINT_MAX_DIM_PX = 2800
        private const val MAX_SAFE_IMAGE_PIXELS = 120_000_000L
    }
}
